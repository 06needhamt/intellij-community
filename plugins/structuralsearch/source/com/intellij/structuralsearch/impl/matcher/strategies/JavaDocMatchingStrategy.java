package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiMethod;

/**
 * Java doc matching strategy
 */
public final class JavaDocMatchingStrategy extends MatchingStrategyBase {
  public void visitClass(final PsiClass clazz) {
    result = true;
  }

  public void visitClassInitializer(final PsiClassInitializer clazzInit) {
    result = true;
  }

  public void visitMethod(final PsiMethod method) {
    result = true;
  }

  private JavaDocMatchingStrategy() {}
  private static JavaDocMatchingStrategy instance;

  public static MatchingStrategy getInstance() {
    if (instance==null) instance = new JavaDocMatchingStrategy();
    return instance;
  }
}
