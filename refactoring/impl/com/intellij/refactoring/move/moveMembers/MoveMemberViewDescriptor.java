/**
 * created at Sep 11, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move.moveMembers;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.refactoring.RefactoringBundle;

class MoveMemberViewDescriptor implements UsageViewDescriptor {
  private PsiElement[] myElementsToMove;

  public MoveMemberViewDescriptor(PsiElement[] elementsToMove) {
    myElementsToMove = elementsToMove;
  }

  public PsiElement[] getElements() {
    return myElementsToMove;
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("move.members.elements.header");
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
