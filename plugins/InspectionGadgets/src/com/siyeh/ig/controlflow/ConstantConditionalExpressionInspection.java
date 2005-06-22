package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;

public class ConstantConditionalExpressionInspection
        extends ExpressionInspection {
    private final ConstantConditionalFix fix = new ConstantConditionalFix();

    public String getDisplayName() {
        return "Constant conditional expression";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConstantConditionalExpressionVisitor();
    }

    public String buildErrorString(PsiElement location) {
        final PsiConditionalExpression exp = (PsiConditionalExpression) location;
        return '\'' + exp.getText() + "' can be simplified to '" +
                calculateReplacementExpression(exp) +
                "' #loc";
    }

    private static String calculateReplacementExpression(PsiConditionalExpression exp) {
        final PsiExpression thenExpression = exp.getThenExpression();
        final PsiExpression elseExpression = exp.getElseExpression();
        final PsiExpression condition = exp.getCondition();
        assert thenExpression != null;
        assert elseExpression != null;

        if (isTrue(condition)) {
            return thenExpression.getText();
        } else {
            return elseExpression.getText();
        }
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class
            ConstantConditionalFix extends InspectionGadgetsFix {
        public String getName() {
            return "Simplify";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiConditionalExpression expression = (PsiConditionalExpression) descriptor.getPsiElement();
            final String newExpression = calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private static class ConstantConditionalExpressionVisitor
            extends BaseInspectionVisitor {

        public void visitConditionalExpression(PsiConditionalExpression exp) {
            super.visitConditionalExpression(exp);
            final PsiExpression condition = exp.getCondition();
            final PsiExpression thenExpression = exp.getThenExpression();
            if (thenExpression == null) {
                return;
            }
            final PsiExpression elseExpression = exp.getElseExpression();
            if (elseExpression == null) {
                return;
            }
            if (isFalse(condition) || isTrue(condition)) {
                registerError(exp);
            }
        }
    }

    private static boolean isFalse(PsiExpression expression) {
        final String text = expression.getText();
        return "false".equals(text);
    }

    private static boolean isTrue(PsiExpression expression) {
        final String text = expression.getText();
        return "true".equals(text);
    }


}
