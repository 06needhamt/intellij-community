package com.siyeh.ipp.base;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.psiutils.BoolUtils;
import com.siyeh.ipp.psiutils.ComparisonUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Intention implements IntentionAction{
    private final PsiElementPredicate predicate;

    /** @noinspection AbstractMethodCallInConstructor,OverridableMethodCallInConstructor*/
    protected Intention(){
        super();
        predicate = getElementPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }
        final PsiElement element = findMatchingElement(file, editor);
        if(element == null){
            return;
        }
        processIntention(element);
    }

    protected abstract void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException;

    protected abstract @NotNull PsiElementPredicate getElementPredicate();

    protected static void replaceExpression(@NotNull String newExpression,
                                            @NotNull PsiExpression exp)
            throws IncorrectOperationException{
        final PsiManager mgr = exp.getManager();
        final PsiElementFactory factory = mgr.getElementFactory();
        final PsiExpression newCall =
                factory.createExpressionFromText(newExpression, null);
        final PsiElement insertedElement = exp.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpression(@NotNull PsiExpression newExpression,
                                                                 @NotNull PsiExpression exp)
            throws IncorrectOperationException{
        final PsiManager mgr = exp.getManager();
        final PsiElementFactory factory = mgr.getElementFactory();

        PsiExpression expressionToReplace = exp;
        final String newExpressionText = newExpression.getText();
        final String expString;
        if(BoolUtils.isNegated(exp)){
            expressionToReplace = BoolUtils.findNegation(exp);
            expString = newExpressionText;
        } else if(ComparisonUtils.isComparison(newExpression)){
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) newExpression;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final String operator = sign.getText();
            final String negatedComparison =
                    ComparisonUtils.getNegatedComparison(operator);
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            assert rhs != null;
            expString = lhs.getText() + negatedComparison + rhs.getText();
        } else{
            if(ParenthesesUtils.getPrecendence(newExpression) >
                    ParenthesesUtils.PREFIX_PRECEDENCE){
                expString = "!(" + newExpressionText + ')';
            } else{
                expString = '!' + newExpressionText;
            }
        }
        final PsiExpression newCall =
                factory.createExpressionFromText(expString, null);
        assert expressionToReplace != null;
        final PsiElement insertedElement = expressionToReplace.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpressionString(String newExpression,
                                                                       PsiExpression exp)
            throws IncorrectOperationException{
        final PsiManager mgr = exp.getManager();
        final PsiElementFactory factory = mgr.getElementFactory();

        PsiExpression expressionToReplace = exp;
        final String expString;
        if(BoolUtils.isNegated(exp)){
            expressionToReplace = BoolUtils.findNegation(exp);
            expString = newExpression;
        } else{
            expString = "!(" + newExpression + ')';
        }
        final PsiExpression newCall =
                factory.createExpressionFromText(expString, null);
        assert expressionToReplace != null;
        final PsiElement insertedElement = expressionToReplace.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceStatement(@NotNull String newStatement,
                                           @NotNull PsiStatement statement)
            throws IncorrectOperationException{
        final PsiManager mgr = statement.getManager();
        final PsiElementFactory factory = mgr.getElementFactory();
        final PsiStatement newCall =
                factory.createStatementFromText(newStatement, null);
        final PsiElement insertedElement = statement.replace(newCall);
        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
    }

    @Nullable PsiElement findMatchingElement(PsiFile file,
                                                       Editor editor){
        final CaretModel caretModel = editor.getCaretModel();
        final int position = caretModel.getOffset();
        PsiElement element = file.findElementAt(position);
        while(element != null){
            if(predicate.satisfiedBy(element)){
                return element;
            } else{
                element = element.getParent();
            }
        }
        return null;
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file){
        return findMatchingElement(file, editor) != null;
    }

    public boolean startInWriteAction(){
        return true;
    }

    private static boolean isFileReadOnly(Project project, PsiFile file){
        final VirtualFile virtualFile = file.getVirtualFile();
        return ReadonlyStatusHandler.getInstance(project)
                .ensureFilesWritable(new VirtualFile[]{virtualFile})
                .hasReadonlyFiles();
    }
}
