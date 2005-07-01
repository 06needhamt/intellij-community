package com.siyeh.ig;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class InspectionGadgetsFix implements LocalQuickFix{
  //to appear in "Apply Fix" statement when multiple Quick Fixes exist
  public String getFamilyName() {
    return "";
  }

  public void applyFix(Project project,
                         ProblemDescriptor descriptor){
        if(isQuickFixOnReadOnlyFile(descriptor)){
            return;
        }
        try{
            doFix(project, descriptor);
        } catch(IncorrectOperationException e){
            final Class<? extends InspectionGadgetsFix> aClass = getClass();
            final String className = aClass.getName();
            final Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }

    protected abstract void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException;

    protected static void deleteElement(@NotNull PsiElement element)
            throws IncorrectOperationException{
        element.delete();
    }

    protected static void replaceExpression(PsiExpression expression,
                                     String newExpression)
            throws IncorrectOperationException{
        final PsiManager psiManager = expression.getManager();
        final PsiElementFactory factory = psiManager.getElementFactory();
        final PsiExpression newExp = factory.createExpressionFromText(newExpression,
                                                                      null);
        final PsiElement replacementExp = expression.replace(newExp);
        final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
        styleManager.reformat(replacementExp);
    }
    protected static void replaceExpressionAndShorten(PsiExpression expression,
                                     String newExpression)
            throws IncorrectOperationException{
        final PsiManager psiManager = expression.getManager();
        final PsiElementFactory factory = psiManager.getElementFactory();
        final PsiExpression newExp = factory.createExpressionFromText(newExpression,
                                                                      null);
        final PsiElement replacementExp = expression.replace(newExp);
        final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
        styleManager.shortenClassReferences(replacementExp);
        styleManager.reformat(replacementExp);
    }

    protected static void replaceStatement(PsiStatement statement,
                                    String newStatement)
            throws IncorrectOperationException{
        final PsiManager psiManager = statement.getManager();
        final PsiElementFactory factory = psiManager.getElementFactory();
        final PsiStatement newExp = factory.createStatementFromText(newStatement,
                                                                    null);
        final PsiElement replacementExp = statement.replace(newExp);
        final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
        styleManager.reformat(replacementExp);
    }
    protected static void replaceStatementAndShortenClassNames(PsiStatement statement,
                                    String newStatement)
            throws IncorrectOperationException{
        final PsiManager psiManager = statement.getManager();
        final PsiElementFactory factory = psiManager.getElementFactory();
        final PsiStatement newExp = factory.createStatementFromText(newStatement,
                                                                    null);
        final PsiElement replacementStatement = statement.replace(newExp);
        final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
        styleManager.shortenClassReferences(replacementStatement);
        styleManager.reformat(replacementStatement);
    }

    private static boolean isQuickFixOnReadOnlyFile(ProblemDescriptor descriptor){
        final PsiElement problemElement = descriptor.getPsiElement();
        if(problemElement == null){
            return false;
        }
        final PsiFile containingPsiFile = problemElement.getContainingFile();
        if(containingPsiFile == null){
            return false;
        }
        final VirtualFile virtualFile = containingPsiFile.getVirtualFile();
        final PsiManager psiManager = problemElement.getManager();
        final Project project = psiManager.getProject();
        final ReadonlyStatusHandler handler = ReadonlyStatusHandler.getInstance(project);
        final ReadonlyStatusHandler.OperationStatus status =
                handler.ensureFilesWritable(new VirtualFile[]{virtualFile});
        return status.hasReadonlyFiles();
    }
}
