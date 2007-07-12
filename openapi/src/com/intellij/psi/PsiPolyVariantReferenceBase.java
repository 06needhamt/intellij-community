/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.util.TextRange;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiPolyVariantReferenceBase<T extends PsiElement> extends PsiReferenceBase<T> implements PsiPolyVariantReference {

  public PsiPolyVariantReferenceBase(final T psiElement) {
    super(psiElement);
  }

  public PsiPolyVariantReferenceBase(final T element, final TextRange range, final boolean soft) {
    super(element, range, soft);
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }
}
