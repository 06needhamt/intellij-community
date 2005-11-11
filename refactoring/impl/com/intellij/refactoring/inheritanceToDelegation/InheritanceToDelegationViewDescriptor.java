package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;

/**
 * @author dsl
 */
public class InheritanceToDelegationViewDescriptor extends UsageViewDescriptorAdapter {
  private PsiClass myClass;

  public InheritanceToDelegationViewDescriptor(PsiClass aClass) {
    super();
    myClass = aClass;
  }

  public PsiElement[] getElements() {
    return new PsiElement[] { myClass };
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("replace.inheritance.with.delegation.elements.header");
  }
}
