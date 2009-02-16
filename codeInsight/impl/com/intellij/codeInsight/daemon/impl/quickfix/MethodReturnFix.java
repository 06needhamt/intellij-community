package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.IntentionQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import org.jetbrains.annotations.NotNull;

public class MethodReturnFix extends IntentionQuickFix {
  private final PsiMethod myMethod;
  private final PsiType myReturnType;
  private final boolean myFixWholeHierarchy;

  public MethodReturnFix(PsiMethod method, PsiType toReturn, boolean fixWholeHierarchy) {
    myMethod = method;
    myReturnType = toReturn;
    myFixWholeHierarchy = fixWholeHierarchy;
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("fix.return.type.text",
                                  myMethod.getName(),
                                  myReturnType.getCanonicalText());
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.return.type.family");
  }

  public boolean isAvailable() {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myReturnType != null
        && myReturnType.isValid()
        && !TypeConversionUtil.isNullType(myReturnType)
        && myMethod.getReturnType() != null
        && !Comparing.equal(myReturnType, myMethod.getReturnType());
  }

  public void applyFix(final Project project, final PsiFile file, final Editor editor) {
    if (!CodeInsightUtilBase.prepareFileForWrite(myMethod.getContainingFile())) return;
    PsiMethod method = myFixWholeHierarchy ? myMethod.findDeepestSuperMethod() : myMethod;
    if (method == null) method = myMethod;
    ChangeSignatureProcessor processor = new ChangeSignatureProcessor(myMethod.getProject(),
                                                                      method,
        false, null,
        method.getName(),
        myReturnType,
        RemoveUnusedParameterFix.getNewParametersInfo(method, null));
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      processor.run();
    }
    else {
      processor.run();
    }
    if (method.getContainingFile() != file) {
      UndoUtil.markPsiFileForUndo(file);
    }
  }

}
