package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class StringConcatenationInLoopsInspection extends ExpressionInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreUnlessAssigned = false;

    public String getID(){
        return "StringContatenationInLoop";
    }

    public String getDisplayName() {
        return "String concatenation in loop";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "String concatenation (#ref) in loop #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Only warn if concatenated string is assigned",
                this, "m_ignoreUnlessAssigned");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StringConcatenationInLoopsVisitor(this, inspectionManager,
                onTheFly);
    }

    private class StringConcatenationInLoopsVisitor
            extends BaseInspectionVisitor {
        private StringConcatenationInLoopsVisitor(BaseInspection inspection,
                                                  InspectionManager inspectionManager,
                                                  boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression))
            {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();

            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUS)) {
                return;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            if (!ControlFlowUtils.isInLoop(expression)) {
                return;
            }
            if (ControlFlowUtils.isInExitStatement(expression)) {
                return;
            }
            if (isEvaluatedAtCompileTime(expression)) {
                return;
            }
            if (m_ignoreUnlessAssigned && !isOnRHSOfAssignment(expression)) {
                return;
            }
            registerError(sign);
        }


        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();

            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSEQ)) {
                return;
            }
            final PsiExpression lhs = expression.getLExpression();

            final PsiType type = lhs.getType();
            if (type == null) {
                return;
            }
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            if (!ControlFlowUtils.isInLoop(expression)) {
                return;
            }
            if (ControlFlowUtils.isInExitStatement(expression)) {
                return;
            }
            registerError(sign);
        }
    }

    private static boolean isEvaluatedAtCompileTime(PsiExpression expression) {
        if (expression instanceof PsiLiteralExpression) {
            return true;
        }
        if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            return isEvaluatedAtCompileTime(lhs) &&
                    isEvaluatedAtCompileTime(rhs);
        }
        if (expression instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
            final PsiExpression operand = prefixExpression.getOperand();
            return isEvaluatedAtCompileTime(operand);
        }
        if (expression instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
            final PsiElement qualifier = referenceExpression.getQualifier();
            if (qualifier instanceof PsiThisExpression) {
                return false;
            }
            final PsiElement element = referenceExpression.resolve();
            if (element instanceof PsiField) {
                final PsiField field = (PsiField) element;
                final PsiExpression initializer = field.getInitializer();
                return field.hasModifierProperty(PsiModifier.FINAL) &&
                        isEvaluatedAtCompileTime(initializer);
            }
            if (element instanceof PsiVariable) {
                final PsiVariable variable = (PsiVariable) element;
                final PsiExpression initializer = variable.getInitializer();
                return variable.hasModifierProperty(PsiModifier.FINAL) &&
                        isEvaluatedAtCompileTime(initializer);
            }
        }
        if (expression instanceof PsiParenthesizedExpression) {
            final PsiParenthesizedExpression parenthesizedExpression =
                    (PsiParenthesizedExpression) expression;
            final PsiExpression unparenthesizedExpression = parenthesizedExpression.getExpression();
            return isEvaluatedAtCompileTime(unparenthesizedExpression);

        }
        if (expression instanceof PsiConditionalExpression) {
            final PsiConditionalExpression conditionalExpression =
                    (PsiConditionalExpression) expression;
            final PsiExpression condition = conditionalExpression.getCondition();
            final PsiExpression thenExpression = conditionalExpression.getThenExpression();
            final PsiExpression elseExpression = conditionalExpression.getElseExpression();
            return isEvaluatedAtCompileTime(condition) &&
                    isEvaluatedAtCompileTime(thenExpression) &&
                    isEvaluatedAtCompileTime(elseExpression);

        }
        if (expression instanceof PsiTypeCastExpression) {
            final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression) expression;
            final PsiTypeElement castType = typeCastExpression.getCastType();
            final PsiType type = castType.getType();
            return TypeUtils.typeEquals("java.lang.String", type);
        }
        return false;
    }

    private static boolean isOnRHSOfAssignment(PsiExpression expression) {
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiParenthesizedExpression) {
            final PsiExpression containedExpression = ((PsiParenthesizedExpression) parent).getExpression();
            return isOnRHSOfAssignment(containedExpression);
        }
        if (parent instanceof PsiAssignmentExpression) {
            return true;
        }
        if (parent instanceof PsiBinaryExpression) {
            return isOnRHSOfAssignment((PsiExpression) parent);
        }
        return false;
    }


}