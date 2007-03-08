/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class SimplifiableConditionalExpressionInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "simplifiable.conditional.expression.display.name");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiConditionalExpression expression =
                (PsiConditionalExpression)infos[0];
        return InspectionGadgetsBundle.message(
                "simplifiable.conditional.expression.problem.descriptor",
                calculateReplacementExpression(expression));
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryConditionalExpressionVisitor();
    }

    static String calculateReplacementExpression(
            PsiConditionalExpression expression) {
        final PsiExpression thenExpression = expression.getThenExpression();
        final PsiExpression elseExpression = expression.getElseExpression();
        final PsiExpression condition = expression.getCondition();
        assert thenExpression != null;
        assert elseExpression != null;

        if (BoolUtils.isTrue(thenExpression)) {
            final String elseExpressionText;
            if (ParenthesesUtils.getPrecedence(elseExpression) >
                    ParenthesesUtils.OR_PRECEDENCE) {
                elseExpressionText = '(' + elseExpression.getText() + ')';
            } else {
                elseExpressionText = elseExpression.getText();
            }
            return condition.getText() + " || " + elseExpressionText;
        } else if (BoolUtils.isFalse(thenExpression)) {
            final String elseExpressionText;
            if (ParenthesesUtils.getPrecedence(elseExpression) >
                    ParenthesesUtils.AND_PRECEDENCE) {
                elseExpressionText = '(' + elseExpression.getText() + ')';
            } else {
                elseExpressionText = elseExpression.getText();
            }
            return BoolUtils.getNegatedExpressionText(condition) + " && " +
                    elseExpressionText;
        }
        if (BoolUtils.isFalse(elseExpression)) {
            final String thenExpressionText;
            if (ParenthesesUtils.getPrecedence(thenExpression) >
                    ParenthesesUtils.AND_PRECEDENCE) {
                thenExpressionText = '(' + thenExpression.getText() + ')';
            } else {
                thenExpressionText = thenExpression.getText();
            }
            return condition.getText() + " && " + thenExpressionText;
        } else {
            final String thenExpressionText;
            if (ParenthesesUtils.getPrecedence(thenExpression) >
                    ParenthesesUtils.OR_PRECEDENCE) {
                thenExpressionText = '(' + thenExpression.getText() + ')';
            } else {
                thenExpressionText = thenExpression.getText();
            }
            return BoolUtils.getNegatedExpressionText(condition) + " || " +
                    thenExpressionText;
        }
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new SimplifiableConditionalFix();
    }

    private static class SimplifiableConditionalFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "constant.conditional.expression.simplify.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiConditionalExpression expression =
                    (PsiConditionalExpression)descriptor.getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private static class UnnecessaryConditionalExpressionVisitor
            extends BaseInspectionVisitor {

        public void visitConditionalExpression(
                PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            final PsiExpression thenExpression = expression.getThenExpression();
            if (thenExpression == null) {
                return;
            }
            final PsiExpression elseExpression = expression.getElseExpression();
            if (elseExpression == null) {
                return;
            }
            final boolean thenConstant = BoolUtils.isFalse(thenExpression) ||
                            BoolUtils.isTrue(thenExpression);
            final boolean elseConstant = BoolUtils.isFalse(elseExpression) ||
                    BoolUtils.isTrue(elseExpression);
            if (thenConstant == elseConstant) {
                return;
            }
            registerError(expression, expression);
        }
    }
}