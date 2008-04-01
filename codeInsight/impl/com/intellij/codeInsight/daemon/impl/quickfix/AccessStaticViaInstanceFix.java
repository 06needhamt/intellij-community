package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AccessStaticViaInstanceFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AccessStaticViaInstanceFix");

  private final PsiReferenceExpression myExpression;
  private final PsiMember myMember;
  private final JavaResolveResult myResult;

  public AccessStaticViaInstanceFix(PsiReferenceExpression expression, JavaResolveResult result) {
    myExpression = expression;
    myMember = (PsiMember)result.getElement();
    myResult = result;
  }

  @NotNull
  public String getName() {
    PsiClass aClass = myMember.getContainingClass();
    if (aClass == null) return "";
    return QuickFixBundle.message("access.static.via.class.reference.text",
                                  HighlightMessageUtil.getSymbolName(myMember, myResult.getSubstitutor()),
                                  HighlightUtil.formatClass(aClass),
                                  HighlightUtil.formatClass(aClass,false));
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("access.static.via.class.reference.family");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    if (!myExpression.isValid() || !myMember.isValid()) return;
    if (!CodeInsightUtilBase.prepareFileForWrite(myExpression.getContainingFile())) return;
    PsiClass containingClass = myMember.getContainingClass();
    if (containingClass == null) return;
    try {
      PsiExpression qualifierExpression = myExpression.getQualifierExpression();
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      if (qualifierExpression instanceof PsiThisExpression &&
          ((PsiThisExpression) qualifierExpression).getQualifier() == null) {
        // this.field -> field
        qualifierExpression.delete();
        if (myExpression.resolve() != myMember) {
          PsiReferenceExpression expr = (PsiReferenceExpression) factory.createExpressionFromText("A.foo", myExpression);
          final PsiExpression qualifierReplacement = expr.getQualifierExpression();
          LOG.assertTrue(qualifierReplacement != null);
          qualifierReplacement.replace(factory.createReferenceExpression(containingClass));
          final PsiElement referenceReplacement = expr.getReferenceNameElement();
          LOG.assertTrue(referenceReplacement != null);
          referenceReplacement.replace(myExpression);
          myExpression.replace(expr);
        }
      }
      else if (qualifierExpression != null) {
        qualifierExpression.replace(factory.createReferenceExpression(containingClass));
      }

      PsiFile containingFile = myMember.getContainingFile();
      if (containingFile != null) {
        UndoUtil.markPsiFileForUndo(containingFile);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
