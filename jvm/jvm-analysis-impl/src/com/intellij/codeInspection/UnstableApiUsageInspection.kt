// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.AnnotatedApiUsageUtil.findAnnotatedContainingDeclaration
import com.intellij.codeInspection.AnnotatedApiUsageUtil.findAnnotatedTypeUsedInDeclarationSignature
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.codeInspection.deprecation.DeprecationInspection
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.codeInspection.util.SpecialAnnotationsUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ArrayUtilRt
import com.siyeh.ig.ui.ExternalizableStringSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import java.awt.BorderLayout
import javax.swing.JPanel

class UnstableApiUsageInspection : LocalInspectionTool() {

  companion object {

    private val SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME: String = ApiStatus.ScheduledForRemoval::class.java.canonicalName

    val DEFAULT_UNSTABLE_API_ANNOTATIONS: List<String> = listOf(
      SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME,
      "org.jetbrains.annotations.ApiStatus.Experimental",
      "org.jetbrains.annotations.ApiStatus.Internal",
      "com.google.common.annotations.Beta",
      "io.reactivex.annotations.Beta",
      "io.reactivex.annotations.Experimental",
      "rx.annotations.Experimental",
      "rx.annotations.Beta",
      "org.apache.http.annotation.Beta",
      "org.gradle.api.Incubating"
    )

    private val knownAnnotationMessageProviders = mapOf(SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME to ScheduledForRemovalMessageProvider())
  }

  @JvmField
  val unstableApiAnnotations: List<String> = ExternalizableStringSet(
    *ArrayUtilRt.toStringArray(DEFAULT_UNSTABLE_API_ANNOTATIONS)
  )

  @JvmField
  var myIgnoreInsideImports: Boolean = true

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    ApiUsageUastVisitor.createPsiElementVisitor(
      UnstableApiUsageProcessor(
        holder,
        myIgnoreInsideImports,
        unstableApiAnnotations.toList(),
        knownAnnotationMessageProviders
      )
    )

  override fun createOptionsPanel(): JPanel {
    val checkboxPanel = SingleCheckboxOptionsPanel(
      JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.ignore.inside.imports"), this, "myIgnoreInsideImports"
    )

    //TODO in add annotation window "Include non-project items" should be enabled by default
    val annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      unstableApiAnnotations, JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.annotations.list")
    )

    val panel = JPanel(BorderLayout(2, 2))
    panel.add(checkboxPanel, BorderLayout.NORTH)
    panel.add(annotationsListControl, BorderLayout.CENTER)
    return panel
  }
}

private class UnstableApiUsageProcessor(
  private val problemsHolder: ProblemsHolder,
  private val ignoreInsideImports: Boolean,
  private val unstableApiAnnotations: List<String>,
  private val knownAnnotationMessageProviders: Map<String, UnstableApiUsageMessageProvider>
) : ApiUsageProcessor {

  private companion object {
    fun isLibraryElement(element: PsiElement): Boolean {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return true
      }
      val containingVirtualFile = PsiUtilCore.getVirtualFile(element)
      return containingVirtualFile != null && ProjectFileIndex.getInstance(element.project).isInLibraryClasses(containingVirtualFile)
    }
  }

  override fun processImportReference(sourceNode: UElement, target: PsiModifierListOwner) {
    if (!ignoreInsideImports) {
      checkUnstableApiUsage(target, sourceNode, false)
    }
  }

  override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
    checkUnstableApiUsage(target, sourceNode, false)
  }

  override fun processConstructorInvocation(
    sourceNode: UElement,
    instantiatedClass: PsiClass,
    constructor: PsiMethod?,
    subclassDeclaration: UClass?
  ) {
    if (constructor != null) {
      checkUnstableApiUsage(constructor, sourceNode, false)
    }
  }

  override fun processMethodOverriding(method: UMethod, overriddenMethod: PsiMethod) {
    checkUnstableApiUsage(overriddenMethod, method, true)
  }

  private fun getMessageProvider(psiAnnotation: PsiAnnotation): UnstableApiUsageMessageProvider? {
    val annotationName = psiAnnotation.qualifiedName ?: return null
    return knownAnnotationMessageProviders[annotationName] ?: DefaultUnstableApiUsageMessageProvider
  }

  private fun getElementToHighlight(sourceNode: UElement): PsiElement? =
    (sourceNode as? UDeclaration)?.uastAnchor.sourcePsiElement ?: sourceNode.sourcePsi

  private fun checkUnstableApiUsage(
    target: PsiModifierListOwner,
    sourceNode: UElement,
    isMethodOverriding: Boolean
  ) {
    if (!isLibraryElement(target)) {
      return
    }
    val annotatedContainingDeclaration = findAnnotatedContainingDeclaration(target, unstableApiAnnotations, true)
    if (annotatedContainingDeclaration != null) {
      val messageProvider = getMessageProvider(annotatedContainingDeclaration.psiAnnotation) ?: return
      val message = if (isMethodOverriding) {
        messageProvider.buildMessageUnstableMethodOverridden(annotatedContainingDeclaration)
      }
      else {
        messageProvider.buildMessage(annotatedContainingDeclaration)
      }
      val elementToHighlight = getElementToHighlight(sourceNode) ?: return
      problemsHolder.registerProblem(elementToHighlight, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      return
    }

    if (!isMethodOverriding) {
      val declaration = target.toUElement(UDeclaration::class.java) ?: return
      val unstableTypeUsedInSignature = findAnnotatedTypeUsedInDeclarationSignature(declaration, unstableApiAnnotations)
      if (unstableTypeUsedInSignature != null) {
        val messageProvider = getMessageProvider(unstableTypeUsedInSignature.psiAnnotation) ?: return
        val message = messageProvider.buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(target, unstableTypeUsedInSignature)
        val elementToHighlight = getElementToHighlight(sourceNode) ?: return
        problemsHolder.registerProblem(elementToHighlight, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }

}

private interface UnstableApiUsageMessageProvider {

  fun buildMessage(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String

  fun buildMessageUnstableMethodOverridden(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String

  fun buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(
    referencedApi: PsiModifierListOwner,
    annotatedTypeUsedInSignature: AnnotatedContainingDeclaration
  ): String
}

private object DefaultUnstableApiUsageMessageProvider : UnstableApiUsageMessageProvider {
  override fun buildMessageUnstableMethodOverridden(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String =
    with(annotatedContainingDeclaration) {
      if (isOwnAnnotation) {
        JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.overridden.method.is.marked.unstable.itself", targetName)
      }
      else {
        JvmAnalysisBundle.message(
          "jvm.inspections.unstable.api.usage.overridden.method.is.declared.in.unstable.api",
          targetName,
          containingDeclarationType,
          containingDeclarationName
        )
      }
    }

  override fun buildMessage(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String =
    with(annotatedContainingDeclaration) {
      if (isOwnAnnotation) {
        JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.api.is.marked.unstable.itself", targetName)
      }
      else {
        JvmAnalysisBundle.message(
          "jvm.inspections.unstable.api.usage.api.is.declared.in.unstable.api",
          targetName,
          containingDeclarationType,
          containingDeclarationName
        )
      }
    }

  override fun buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(
    referencedApi: PsiModifierListOwner,
    annotatedTypeUsedInSignature: AnnotatedContainingDeclaration
  ): String = JvmAnalysisBundle.message(
    "jvm.inspections.unstable.api.usage.unstable.type.is.used.in.signature.of.referenced.api",
    DeprecationInspection.getPresentableName(referencedApi),
    annotatedTypeUsedInSignature.targetType,
    annotatedTypeUsedInSignature.targetName
  )
}

private class ScheduledForRemovalMessageProvider : UnstableApiUsageMessageProvider {
  override fun buildMessageUnstableMethodOverridden(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String {
    val versionMessage = getVersionMessage(annotatedContainingDeclaration)
    return with(annotatedContainingDeclaration) {
      if (isOwnAnnotation) {
        JvmAnalysisBundle.message(
          "jvm.inspections.scheduled.for.removal.method.overridden.marked.itself",
          targetName,
          versionMessage
        )
      }
      else {
        JvmAnalysisBundle.message(
          "jvm.inspections.scheduled.for.removal.method.overridden.declared.in.marked.api",
          targetName,
          containingDeclarationType,
          containingDeclarationName,
          versionMessage
        )
      }
    }
  }

  override fun buildMessage(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String {
    val versionMessage = getVersionMessage(annotatedContainingDeclaration)
    return with(annotatedContainingDeclaration) {
      if (!isOwnAnnotation) {
        JvmAnalysisBundle.message(
          "jvm.inspections.scheduled.for.removal.api.is.declared.in.marked.api",
          targetName,
          containingDeclarationType,
          containingDeclarationName,
          versionMessage
        )
      }
      else {
        JvmAnalysisBundle.message(
          "jvm.inspections.scheduled.for.removal.api.is.marked.itself", targetName, versionMessage
        )
      }
    }
  }

  override fun buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(
    referencedApi: PsiModifierListOwner,
    annotatedTypeUsedInSignature: AnnotatedContainingDeclaration
  ): String {
    val versionMessage = getVersionMessage(annotatedTypeUsedInSignature)
    return JvmAnalysisBundle.message(
      "jvm.inspections.scheduled.for.removal.scheduled.for.removal.type.is.used.in.signature.of.referenced.api",
      DeprecationInspection.getPresentableName(referencedApi),
      annotatedTypeUsedInSignature.targetType,
      annotatedTypeUsedInSignature.targetName,
      versionMessage
    )
  }

  private fun getVersionMessage(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String {
    val versionValue = AnnotationUtil.getDeclaredStringAttributeValue(annotatedContainingDeclaration.psiAnnotation, "inVersion")
    return if (versionValue.isNullOrEmpty()) {
      JvmAnalysisBundle.message("jvm.inspections.scheduled.for.removal.future.version")
    }
    else {
      JvmAnalysisBundle.message("jvm.inspections.scheduled.for.removal.predefined.version", versionValue)
    }
  }
}