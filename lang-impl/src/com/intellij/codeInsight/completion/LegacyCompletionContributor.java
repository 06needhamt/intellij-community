/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class LegacyCompletionContributor extends CompletionContributor {
  public static boolean DEBUG = false;

  public LegacyCompletionContributor() {
    final PsiElementPattern.Capture<PsiElement> everywhere = PlatformPatterns.psiElement();
    extend(CompletionType.BASIC, everywhere, new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet _result) {
        final PsiFile file = parameters.getOriginalFile();
        final int startOffset = parameters.getOffset();
        final PsiElement insertedElement = parameters.getPosition();
        CompletionData completionData = ApplicationManager.getApplication().runReadAction(new Computable<CompletionData>() {
          public CompletionData compute() {
            return CompletionUtil.getCompletionDataByElement(file);
          }
        });
        final CompletionResultSet result = _result.withPrefixMatcher(completionData == null
                                                                     ? CompletionData.findPrefixStatic(insertedElement, startOffset)
                                                                     : completionData.findPrefix(insertedElement, startOffset));
        if (completionData == null) {
          // some completion data may depend on prefix
          completionData = ApplicationManager.getApplication().runReadAction(new Computable<CompletionData>() {
            public CompletionData compute() {
              return CompletionUtil.getCompletionDataByElement(file);
            }
          });
        }

        if (completionData == null) return;

        completeReference(parameters, result, completionData);

        final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
        final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
        completionData.addKeywordVariants(keywordVariants, insertedElement, parameters.getOriginalFile());
        completionData.completeKeywordsBySet(lookupSet, keywordVariants, insertedElement, result.getPrefixMatcher(), parameters.getOriginalFile());
        for (final LookupElement item : lookupSet) {
          result.addElement(item);
        }
      }
    });


  }

  public static void completeReference(final CompletionParameters parameters, final CompletionResultSet result, CompletionData completionData) {
    final Set<LookupElement> lookupSet = new LinkedHashSet<LookupElement>();
    final int startOffset = parameters.getOffset();
    final PsiElement insertedElement = parameters.getPosition();
    final PsiReference ref = ApplicationManager.getApplication().runReadAction(new Computable<PsiReference>() {
      public PsiReference compute() {
        return insertedElement.getContainingFile().findReferenceAt(startOffset);
      }
    });
    if (ref instanceof PsiMultiReference) {
      for (final PsiReference reference : completionData.getReferences((PsiMultiReference)ref)) {
        int offsetInElement = startOffset - reference.getElement().getTextRange().getStartOffset();
        final CompletionResultSet resultSet = result.withPrefixMatcher(
            reference.getElement().getText().substring(reference.getRangeInElement().getStartOffset(), offsetInElement));
        completionData.completeReference(reference, lookupSet, insertedElement, parameters.getOriginalFile(), startOffset);
        for (final LookupElement item : lookupSet) {
          resultSet.addElement(item);
        }
        lookupSet.clear();
      }
    }
    else if (ref != null) {
      completionData.completeReference(ref, lookupSet, insertedElement, parameters.getOriginalFile(),
                                       startOffset);
    }
    for (final LookupElement item : lookupSet) {
      result.addElement(item);
    }
  }


}
