package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;

/**
 * @author dsl
 */
public class MoveInstanceMethodViewDescriptor extends UsageViewDescriptorAdapter {
  private PsiMethod myMethod;
  private PsiVariable myTargetVariable;
  private PsiClass myTargetClass;

  public MoveInstanceMethodViewDescriptor(
    PsiMethod method,
    PsiVariable targetVariable,
    PsiClass targetClass) {
    super();
    myMethod = method;
    myTargetVariable = targetVariable;
    myTargetClass = targetClass;
  }

  public PsiElement[] getElements() {
    return new PsiElement[] {myMethod, myTargetVariable, myTargetClass};
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("move.instance.method.elements.header");
  }

}
