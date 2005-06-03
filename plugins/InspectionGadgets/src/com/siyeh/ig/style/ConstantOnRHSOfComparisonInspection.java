package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NotNull;

public class ConstantOnRHSOfComparisonInspection extends ExpressionInspection {
    private final SwapComparisonFix fix = new SwapComparisonFix();

    public String getID(){
        return "ConstantOnRightSideOfComparison";
    }

    public String getDisplayName() {
        return "Constant on right side of comparison";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref: constant on right side of comparison #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConstantOnRHSOfComparisonVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class SwapComparisonFix extends InspectionGadgetsFix {
        public String getName() {
            return "Flip comparison";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiBinaryExpression expression = (PsiBinaryExpression) descriptor.getPsiElement();
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression lhs = expression.getLOperand();
            final PsiJavaToken sign = expression.getOperationSign();
            final String signText = sign.getText();
            assert rhs != null;
            final String rhsText = rhs.getText();
            final String flippedComparison = ComparisonUtils.getFlippedComparison(signText);
            final String lhsText = lhs.getText();
            replaceExpression(expression,
                    rhsText + ' ' + flippedComparison + ' ' + lhsText);
        }
    }

    private static class ConstantOnRHSOfComparisonVisitor extends BaseInspectionVisitor {


        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            if (!ComparisonUtils.isComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            if (!PsiUtil.isConstantExpression(rhs) ||
                    PsiUtil.isConstantExpression(lhs)) {
                return;
            }
            registerError(expression);
        }
    }

}
