package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiJavaCodeReferenceElement;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 23.01.2004
 * Time: 1:07:09
 * To change this template use File | Settings | File Templates.
 */
public class TypeFilter extends NodeFilter {
  public void visitReferenceElement(PsiJavaCodeReferenceElement psiMethod) {
    result = true;
  }

  private TypeFilter() {}

  public static NodeFilter getInstance() {
    if (instance==null) instance = new TypeFilter();
    return instance;
  }
  private static NodeFilter instance;
}
