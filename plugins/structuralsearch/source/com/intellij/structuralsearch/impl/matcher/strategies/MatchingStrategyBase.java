package com.intellij.structuralsearch.impl.matcher.strategies;

import com.intellij.psi.*;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;

/**
 * Base filtering strategy to find statements
 */
public class MatchingStrategyBase extends JavaElementVisitor implements MatchingStrategy, NodeFilter {
  protected boolean result;

  @Override public void visitReferenceExpression(final PsiReferenceExpression psiReferenceExpression) {
    visitExpression(psiReferenceExpression);
  }

  @Override public void visitCodeBlock(final PsiCodeBlock block) {
    result = true;
  }
  
  @Override public void visitCatchSection(final PsiCatchSection section) {
    result = true;
  }

  @Override public void visitStatement(final PsiStatement statement) {
    result = true;
  }

  public boolean continueMatching(final PsiElement start) {
    return accepts(start);
  }

  protected MatchingStrategyBase() {}
  private static MatchingStrategyBase instance;

  public static MatchingStrategy getInstance() {
    if (instance==null) instance = new MatchingStrategyBase();
    return instance;
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
