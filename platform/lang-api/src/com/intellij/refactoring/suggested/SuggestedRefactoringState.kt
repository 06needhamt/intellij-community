// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.suggested

import com.intellij.openapi.editor.RangeMarker
import com.intellij.psi.*
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import org.jetbrains.annotations.Nls

private var nextFeatureUsageId = 0

/**
 * Data class representing state of accumulated signature changes.
 */
//TODO: drop data modifier
data class SuggestedRefactoringState(
  val declaration: PsiElement,
  val refactoringSupport: SuggestedRefactoringSupport,
  val syntaxError: Boolean,
  val oldDeclarationText: String,
  val oldImportsText: String?,
  val oldSignature: Signature,
  val newSignature: Signature,
  val parameterMarkers: List<RangeMarker?>,
  val disappearedParameters: Map<String, Any> = emptyMap() /* last known parameter name to its id */,
  val featureUsageId: Int = nextFeatureUsageId++
) {
  fun withDeclaration(declaration: PsiElement): SuggestedRefactoringState {
    val state = SuggestedRefactoringState(
      declaration, refactoringSupport, syntaxError, oldDeclarationText, oldImportsText,
      oldSignature, newSignature, parameterMarkers, disappearedParameters, featureUsageId
    )
    copyRestoredDeclaration(state)
    return state
  }

  fun withSyntaxError(syntaxError: Boolean): SuggestedRefactoringState {
    val state = SuggestedRefactoringState(
      declaration, refactoringSupport, syntaxError, oldDeclarationText, oldImportsText,
      oldSignature, newSignature, parameterMarkers, disappearedParameters, featureUsageId
    )
    copyRestoredDeclaration(state)
    return state
  }

  fun withOldSignature(oldSignature: Signature): SuggestedRefactoringState {
    val state = SuggestedRefactoringState(
      declaration, refactoringSupport, syntaxError, oldDeclarationText, oldImportsText,
      oldSignature, newSignature, parameterMarkers, disappearedParameters, featureUsageId
    )
    copyRestoredDeclaration(state)
    return state
  }

  fun withNewSignature(newSignature: Signature): SuggestedRefactoringState {
    val state = SuggestedRefactoringState(
      declaration, refactoringSupport, syntaxError, oldDeclarationText, oldImportsText,
      oldSignature, newSignature, parameterMarkers, disappearedParameters, featureUsageId
    )
    copyRestoredDeclaration(state)
    return state
  }

  fun withParameterMarkers(parameterMarkers: List<RangeMarker?>): SuggestedRefactoringState {
    val state = SuggestedRefactoringState(
      declaration, refactoringSupport, syntaxError, oldDeclarationText, oldImportsText,
      oldSignature, newSignature, parameterMarkers, disappearedParameters, featureUsageId
    )
    copyRestoredDeclaration(state)
    return state
  }

  fun withDisappearedParameters(disappearedParameters: Map<String, Any>): SuggestedRefactoringState {
    val state = SuggestedRefactoringState(
      declaration, refactoringSupport, syntaxError, oldDeclarationText, oldImportsText,
      oldSignature, newSignature, parameterMarkers, disappearedParameters, featureUsageId
    )
    copyRestoredDeclaration(state)
    return state
  }

  private fun copyRestoredDeclaration(toState: SuggestedRefactoringState) {
    if (toState.declaration == this.declaration) {
      // do not reset lazy calculated declaration copy for performance reasons
      toState.restoredDeclarationCopy = this.restoredDeclarationCopy
    }
  }

  private var restoredDeclarationCopy: PsiElement? = null

  fun restoredDeclarationCopy(): PsiElement {
    if (restoredDeclarationCopy == null) {
      restoredDeclarationCopy = createRestoredDeclarationCopy()
    }
    return restoredDeclarationCopy!!
  }

  @Deprecated("Use restoredDeclarationCopy()", ReplaceWith("restoredDeclarationCopy()"))
  fun createRestoredDeclarationCopy(refactoringSupport: SuggestedRefactoringSupport): PsiElement {
    return restoredDeclarationCopy()
  }

  private fun createRestoredDeclarationCopy(): PsiElement {
    val psiFile = declaration.containingFile
    val signatureRange = refactoringSupport.signatureRange(declaration)!!
    val importsRange = refactoringSupport.importsRange(psiFile)
    if (importsRange != null) {
      require(importsRange.endOffset < signatureRange.startOffset)
    }

    val fileCopy = psiFile.copy() as PsiFile
    val document = fileCopy.viewProvider.document!!
    document.replaceString(signatureRange.startOffset, signatureRange.endOffset, oldDeclarationText)
    if (oldImportsText != null) {
      document.replaceString(importsRange!!.startOffset, importsRange.endOffset, oldImportsText)
    }
    PsiDocumentManager.getInstance(psiFile.project).commitDocument(document)

    var originalSignatureStart = signatureRange.startOffset
    if (oldImportsText != null) {
      originalSignatureStart -= importsRange!!.length
      originalSignatureStart += oldImportsText.length
    }

    return refactoringSupport.declarationByOffset(fileCopy, originalSignatureStart)!!
  }
}

/**
 * Data representing suggested refactoring that can be performed.
 */
sealed class SuggestedRefactoringData {
  abstract val declaration: PsiElement
}

/**
 * Data representing suggested Rename refactoring.
 */
class SuggestedRenameData(override val declaration: PsiNamedElement, val oldName: String) : SuggestedRefactoringData()

/**
 * Data representing suggested Change Signature refactoring.
 */
@Suppress("DataClassPrivateConstructor")
data class SuggestedChangeSignatureData private constructor(
  val declarationPointer: SmartPsiElementPointer<PsiElement>,
  val oldSignature: Signature,
  val newSignature: Signature,
  val nameOfStuffToUpdate: String,
  val oldDeclarationText: String,
  val oldImportsText: String?
) : SuggestedRefactoringData() {

  override val declaration: PsiElement
    get() = declarationPointer.element!!

  fun restoreInitialState(refactoringSupport: SuggestedRefactoringSupport): () -> Unit {
    val file = declaration.containingFile
    val psiDocumentManager = PsiDocumentManager.getInstance(file.project)
    val document = psiDocumentManager.getDocument(file)!!
    require(psiDocumentManager.isCommitted(document))

    val signatureRange = refactoringSupport.signatureRange(declaration)!!
    val importsRange = refactoringSupport.importsRange(file)
      ?.extendWithWhitespace(document.charsSequence)

    val newSignatureText = document.getText(signatureRange)
    val newImportsText = importsRange
      ?.let { document.getText(it) }
      ?.takeIf { it != oldImportsText }

    document.replaceString(signatureRange.startOffset, signatureRange.endOffset, oldDeclarationText)
    if (newImportsText != null) {
      document.replaceString(importsRange.startOffset, importsRange.endOffset, oldImportsText!!)
    }
    psiDocumentManager.commitDocument(document)

    return {
      require(psiDocumentManager.isCommitted(document))

      val newSignatureRange = refactoringSupport.signatureRange(declaration)!!
      document.replaceString(newSignatureRange.startOffset, newSignatureRange.endOffset, newSignatureText)

      if (newImportsText != null) {
        val newImportsRange = refactoringSupport.importsRange(file)!!
          .extendWithWhitespace(document.charsSequence)
        document.replaceString(newImportsRange.startOffset, newImportsRange.endOffset, newImportsText)
      }

      psiDocumentManager.commitDocument(document)
    }
  }

  companion object {
    /**
     * Creates an instance of [SuggestedChangeSignatureData].
     *
     * @param nameOfStuffToUpdate term to be used in the UI to describe usages to be updated.
     * Typical values are [SuggestedRefactoringAvailability.USAGES], [SuggestedRefactoringAvailability.OVERRIDES]
     * and [SuggestedRefactoringAvailability.IMPLEMENTATIONS].
     */
    @JvmStatic
    fun create(state: SuggestedRefactoringState, @Nls nameOfStuffToUpdate: String): SuggestedChangeSignatureData {
      return SuggestedChangeSignatureData(
        state.declaration.createSmartPointer(),
        state.oldSignature,
        state.newSignature,
        nameOfStuffToUpdate,
        state.oldDeclarationText,
        state.oldImportsText
      )
    }
  }
}
