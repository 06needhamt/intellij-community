package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: May 13, 2005
 */
public class AddNoInspectionForClassAction extends AddNoInspectionDocTagAction{
  public AddNoInspectionForClassAction(final LocalInspectionTool tool, final PsiElement context) {
    super(tool, context);
  }

  public AddNoInspectionForClassAction(final HighlightDisplayKey key, final PsiElement context) {
    super(key, context);
  }

  public AddNoInspectionForClassAction(final String displayName, final String ID, final PsiElement context) {
    super(displayName, ID, context);
  }

  @Nullable protected PsiDocCommentOwner getContainer() {
    PsiDocCommentOwner container = super.getContainer();
    if (container == null || container instanceof PsiClass){
      return null;
    }
    while (container != null ) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(container, PsiClass.class);
      if (parentClass == null && container instanceof PsiClass){
        return container;
      }
      container = parentClass;
    }
    return container;
  }
}
