/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.j2k

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.DummyHolder
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.JetLanguage
import org.jetbrains.kotlin.j2k.ast.Element
import org.jetbrains.kotlin.j2k.usageProcessing.UsageProcessing
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import java.util.*

public trait PostProcessor {
    public fun analyzeFile(file: JetFile, range: TextRange?): BindingContext

    public open fun fixForProblem(problem: Diagnostic): (() -> Unit)? {
        val psiElement = problem.getPsiElement()
        return when (problem.getFactory()) {
            Errors.UNNECESSARY_NOT_NULL_ASSERTION -> { ->
                val exclExclOp = psiElement as JetSimpleNameExpression
                val exclExclExpr = exclExclOp.getParent() as JetUnaryExpression
                exclExclExpr.replace(exclExclExpr.getBaseExpression()!!)
            }

            Errors.VAL_REASSIGNMENT -> { ->
                val property = (psiElement as? JetSimpleNameExpression)?.getReference()?.resolve() as? JetProperty
                if (property != null && !property.isVar()) {
                    val factory = JetPsiFactory(psiElement.getProject())
                    property.getValOrVarNode().getPsi()!!.replace(factory.createVarNode().getPsi()!!)
                }
            }

            else -> null
        }
    }

    public fun doAdditionalProcessing(file: JetFile, rangeMarker: RangeMarker?)
}

public enum class ParseContext {
    TOP_LEVEL
    CODE_BLOCK
}

public class JavaToKotlinConverter(private val project: Project,
                                   private val settings: ConverterSettings,
                                   private val referenceSearcher: ReferenceSearcher,
                                   private val resolverForConverter: ResolverForConverter,
                                   private val postProcessor: PostProcessor?) {
    private val LOG = Logger.getInstance("#org.jetbrains.kotlin.j2k.JavaToKotlinConverter")

    public data class InputElement(
            val element: PsiElement,
            val postProcessingContext: PsiElement?
    )

    public data class ElementResult(val text: String,  val parseContext: ParseContext)

    public data class Result(val results: List<ElementResult?>, val externalCodeProcessing: ((ProgressIndicator) -> (() -> Unit)?)?) //TODO: change interface to not perform write-actions under progress

    public fun elementsToKotlin(
            inputElements: List<InputElement>,
            progress: ProgressIndicator = EmptyProgressIndicator()
    ): Result {
        try {
            val elementCount = inputElements.size()
            val intermediateResults = ArrayList<(Converter.IntermediateResult)?>(elementCount)

            val usageProcessings = LinkedHashMap<PsiElement, MutableCollection<UsageProcessing>>()
            val usageProcessingCollector: (UsageProcessing) -> Unit = {
                usageProcessings.getOrPut(it.targetElement, { ArrayList() }).add(it)
            }

            fun inConversionScope(element: PsiElement)
                    = inputElements.any { it.element.isAncestor(element, strict = false) }

            val progressText = "Converting Java to Kotlin"
            val fileCountText = elementCount.toString() + " " + if (elementCount > 1) "files" else "file"
            var fraction = 0.0
            var pass = 1

            fun processFilesWithProgress(passFraction: Double, processFile: (Int) -> Unit) {
                // we use special process with EmptyProgressIndicator to avoid changing text in our progress by inheritors search inside etc
                ProgressManager.getInstance().runProcess(
                        {
                            progress.setText("$progressText ($fileCountText) - pass $pass of 3")

                            val filesCount = inputElements.indices
                            for (i in filesCount) {
                                progress.checkCanceled()
                                progress.setFraction(fraction + passFraction * i / elementCount)

                                val psiFile = inputElements[i].element as? PsiFile
                                if (psiFile != null) {
                                    progress.setText2(psiFile.getVirtualFile().getPresentableUrl())
                                }

                                processFile(i)
                            }

                            pass++
                            fraction += passFraction
                        },
                        EmptyProgressIndicator())
            }

            processFilesWithProgress(0.25) { i ->
                val psiElement = inputElements[i].element
                val converter = Converter.create(psiElement, settings, ::inConversionScope, referenceSearcher, resolverForConverter, usageProcessingCollector)
                val result = converter.convert()
                intermediateResults.add(result)
            }

            val results = ArrayList<ElementResult?>(elementCount)
            processFilesWithProgress(0.25) { i ->
                val result = intermediateResults[i]
                results.add(if (result != null)
                                ElementResult(result.codeGenerator(usageProcessings), result.parseContext)
                            else
                                null)
                intermediateResults[i] = null // to not hold unused objects in the heap
            }

            val externalCodeProcessing = buildExternalCodeProcessing(usageProcessings, ::inConversionScope)

            if (postProcessor == null) {
                assert(progress is EmptyProgressIndicator, "Progress indicator not supported for postProcessor == null")
                return Result(results, externalCodeProcessing)
            }

            val finalResults = ArrayList<ElementResult?>(elementCount)
            processFilesWithProgress(0.5) { i ->
                val result = results[i]
                if (result != null) {
                    try {
                        //TODO: post processing does not work correctly for ParseContext different from TOP_LEVEL
                        val kotlinFile = JetPsiFactory(project).createAnalyzableFile("dummy.kt", result.text, inputElements[i].postProcessingContext!!)
                        AfterConversionPass(project, postProcessor).run(kotlinFile, range = null)
                        finalResults.add(ElementResult(kotlinFile.getText(), result.parseContext))
                    }
                    catch(e: ProcessCanceledException) {
                        throw e
                    }
                    catch(t: Throwable) {
                        LOG.error(t)
                        finalResults.add(result)
                    }
                }
                else {
                    finalResults.add(null)
                }
            }

            return Result(finalResults, externalCodeProcessing)
        }
        catch(e: ElementCreationStackTraceRequiredException) {
            // if we got this exception then we need to turn element creation stack traces on to get better diagnostic
            Element.saveCreationStacktraces = true
            try {
                return elementsToKotlin(inputElements)
            }
            finally {
                Element.saveCreationStacktraces = false
            }
        }
    }

    data class ReferenceInfo(
            val reference: PsiReference,
            val target: PsiElement,
            val file: PsiFile,
            val processings: Collection<UsageProcessing>
    )

    private fun buildExternalCodeProcessing(
            usageProcessings: Map<PsiElement, Collection<UsageProcessing>>,
            inConversionScope: (PsiElement) -> Boolean
    ): ((ProgressIndicator) -> (() -> Unit)?)? {
        if (usageProcessings.isEmpty()) return null


        val map: Map<PsiElement, Collection<UsageProcessing>> = usageProcessings.values()
                .flatMap { it }
                .filter { it.javaCodeProcessor != null || it.kotlinCodeProcessor != null }
                .groupBy { it.targetElement }
        if (map.isEmpty()) return null

        return fun(progress: ProgressIndicator): (() -> Unit)? {
            val refs = ArrayList<ReferenceInfo>()

            progress.setText("Searching usages to update...")

            for ((i, entry) in map.entrySet().withIndex()) {
                val psiElement = entry.key
                val processings = entry.value

                progress.setText2((psiElement as? PsiNamedElement)?.getName() ?: "")
                progress.checkCanceled()

                ProgressManager.getInstance().runProcess(
                        {
                            val searchJava = processings.any { it.javaCodeProcessor != null }
                            val searchKotlin = processings.any { it.kotlinCodeProcessor != null }
                            referenceSearcher.findExternalCodeProcessingUsages(psiElement, searchJava, searchKotlin)
                                    .filterNot { inConversionScope(it.getElement()) }
                                    .mapTo(refs) { ReferenceInfo(it, psiElement, it.getElement().getContainingFile(), processings) }
                        },
                        ProgressPortionReporter(progress, i / map.size().toDouble(), 1.0 / map.size()))

            }

            if (refs.isEmpty()) return null

            return { processUsages(refs) }
        }
    }

    private fun processUsages(refs: Collection<ReferenceInfo>) {
        @ReferenceLoop
        for ((reference, target, file, processings) in refs.sortBy(ReferenceComparator)) {
            val processors = when (reference.getElement().getLanguage()) {
                JavaLanguage.INSTANCE -> processings.map { it.javaCodeProcessor }.filterNotNull()
                JetLanguage.INSTANCE -> processings.map { it.kotlinCodeProcessor }.filterNotNull()
                else -> continue@ReferenceLoop
            }

            checkReferenceValid(reference)

            var references = listOf(reference)
            for (processor in processors) {
                references = references.flatMap { processor.processUsage(it) ?: listOf(it) }
                references.forEach { checkReferenceValid(it) }
            }
        }
    }

    private fun checkReferenceValid(reference: PsiReference) {
        val element = reference.getElement()
        assert(element.isValid() && element.getContainingFile() !is DummyHolder) { "Reference $reference got invalidated" }
    }

    private object ReferenceComparator : Comparator<ReferenceInfo> {
        override fun compare(info1: ReferenceInfo, info2: ReferenceInfo): Int {
            val element1 = info1.reference.getElement()
            val element2 = info2.reference.getElement()

            val deepness1 = element1.deepnessInTree()
            val deepness2 = element2.deepnessInTree()
            if (deepness1 != deepness2) { // put deeper elements first to not invalidate them when processing ancestors
                return -deepness1.compareTo(deepness2)
            }

            // process elements of the same deepness from right to left so that right-side of assignments is not invalidated by processing of the left one
            return -element1.getStartOffsetInParent().compareTo(element2.getStartOffsetInParent())
        }

        private fun PsiElement.deepnessInTree() = parents(withItself = true).takeWhile { it !is PsiFile }.count()

    }

    private class ProgressPortionReporter(
            indicator: ProgressIndicator,
            private val start: Double,
            private val portion: Double
    ) : DelegatingProgressIndicator(indicator) {

        init {
            setFraction(0.0)
        }

        override fun start() {
            setFraction(0.0)
        }

        override fun stop() {
            setFraction(portion)
        }

        override fun setFraction(fraction: Double) {
            super.setFraction(start + (fraction * portion))
        }

        override fun getFraction(): Double {
            return (super.getFraction() - start) / portion
        }

        override fun setText(text: String?) {
        }

        override fun setText2(text: String?) {
        }
    }
}
