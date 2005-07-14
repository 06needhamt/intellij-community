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
import com.siyeh.ig.psiutils.BoolUtils;

public class SimplifiableConditionalExpressionInspection
        extends ExpressionInspection {
    private final TrivialConditionalFix fix = new TrivialConditionalFix();


    public String getDisplayName() {
        return "Conditional that can be simplified to && or ||";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryConditionalExpressionVisitor();
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

        if (isTrue(thenExpression)) {
            return condition.getText() + " || " + elseExpression.getText();
        } else if(isFalse(thenExpression)){
            return BoolUtils.getNegatedExpressionText(condition) +" && " + elseExpression.getText() ;
        }if (isFalse(elseExpression)) {
            return condition.getText() + " && " + thenExpression.getText();
        } else{
            return BoolUtils.getNegatedExpressionText(condition) +" || " +thenExpression.getText() ;
        }
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class
            TrivialConditionalFix extends InspectionGadgetsFix {
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

    private static class UnnecessaryConditionalExpressionVisitor
            extends BaseInspectionVisitor {

        public void visitConditionalExpression(PsiConditionalExpression exp) {
            super.visitConditionalExpression(exp);
            final PsiExpression thenExpression = exp.getThenExpression();
            if (thenExpression == null) {
                return;
            }
            final PsiExpression elseExpression = exp.getElseExpression();
            if (elseExpression == null) {
                return;
            }
            final boolean thenConstant = isFalse(thenExpression) || isTrue(thenExpression);
            final boolean elseConstant = isFalse(elseExpression) || isTrue(elseExpression);
            if (thenConstant == elseConstant) {
                return;
            }
            registerError(exp);
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
