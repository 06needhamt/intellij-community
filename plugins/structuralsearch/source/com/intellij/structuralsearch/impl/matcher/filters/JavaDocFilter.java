package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.javadoc.PsiDocComment;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 26.12.2003
 * Time: 19:08:26
 * To change this template use Options | File Templates.
 */
public class JavaDocFilter extends NodeFilter {
  public boolean accepts(PsiElement element) {
    return element instanceof PsiDocCommentOwner ||
      element instanceof PsiDocComment;
  }

  private static NodeFilter instance;

  public static NodeFilter getInstance() {
    if (instance==null) instance = new JavaDocFilter();
    return instance;
  }

  private JavaDocFilter() {
  }
}
