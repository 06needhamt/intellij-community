package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class PointlessArithmeticExpressionInspection
        extends ExpressionInspection{
    public boolean m_ignoreExpressionsContainingConstants = false;

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel(
                "Ignore named constant in determinining pointless expressions",
                this, "m_ignoreExpressionsContainingConstants");
    }

    /**
     * @noinspection StaticCollection
     */
    private static final Set<IElementType> arithmeticTokens = new HashSet<IElementType>(
            4);

    static {
        arithmeticTokens.add(JavaTokenType.PLUS);
        arithmeticTokens.add(JavaTokenType.MINUS);
        arithmeticTokens.add(JavaTokenType.ASTERISK);
        arithmeticTokens.add(JavaTokenType.DIV);
    }

    private final PointlessArithmeticFix fix = new PointlessArithmeticFix();

    public String getDisplayName(){
        return "Pointless arithmetic expression";
    }

    public String getGroupDisplayName(){
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "#ref can be replaced with " +
                calculateReplacementExpression((PsiExpression) location)
                + " #loc";
    }

    private String calculateReplacementExpression(
            PsiExpression expression){
        final PsiBinaryExpression exp = (PsiBinaryExpression) expression;
        final PsiJavaToken sign = exp.getOperationSign();
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        assert rhs != null;
        final IElementType tokenType = sign.getTokenType();
        if(tokenType.equals(JavaTokenType.PLUS)){
            if(isZero(lhs)){
                return rhs.getText();
            } else{
                return lhs.getText();
            }
        } else if(tokenType.equals(JavaTokenType.MINUS)){
            return lhs.getText();
        } else if(tokenType.equals(JavaTokenType.ASTERISK)){
            if(isOne(lhs)){
                return rhs.getText();
            } else if(isOne(rhs)){
                return lhs.getText();
            } else{
                return "0";
            }
        } else if(tokenType.equals(JavaTokenType.DIV)){
            return lhs.getText();
        } else{
            return "";
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new PointlessArithmeticVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private class PointlessArithmeticFix extends InspectionGadgetsFix{
        public String getName(){
            return "Simplify";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiExpression expression = (PsiExpression) descriptor
                    .getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private class PointlessArithmeticVisitor extends BaseInspectionVisitor{
        public void visitClass(@NotNull PsiClass aClass){
            //to avoid drilldown
        }

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!arithmeticTokens.contains(tokenType)){
                return;
            }
            if(TypeUtils.expressionHasType("java.lang.String", expression)){
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression lhs = expression.getLOperand();
            final boolean isPointless;
            if(tokenType.equals(JavaTokenType.PLUS)){
                isPointless = additionExpressionIsPointless(lhs, rhs);
            } else if(tokenType.equals(JavaTokenType.MINUS)){
                isPointless = subtractionExpressionIsPointless(rhs);
            } else if(tokenType.equals(JavaTokenType.ASTERISK)){
                isPointless = multiplyExpressionIsPointless(lhs, rhs);
            } else if(tokenType.equals(JavaTokenType.DIV)){
                isPointless = divideExpressionIsPointless(rhs);
            } else{
                isPointless = false;
            }
            if(!isPointless){
                return;
            }
            registerError(expression);
        }
    }

    private  boolean subtractionExpressionIsPointless(PsiExpression rhs){
        return isZero(rhs);
    }

    private  boolean additionExpressionIsPointless(PsiExpression lhs,
                                                         PsiExpression rhs){
        return isZero(lhs) || isZero(rhs);
    }

    private  boolean multiplyExpressionIsPointless(PsiExpression lhs,
                                                         PsiExpression rhs){
        return isZero(lhs) || isZero(rhs) || isOne(lhs) || isOne(rhs);
    }

    private  boolean divideExpressionIsPointless(PsiExpression rhs){
        return isOne(rhs);
    }

    /**
     * @noinspection FloatingPointEquality
     */
    private boolean isZero(PsiExpression expression){
        if(m_ignoreExpressionsContainingConstants &&
                !(expression instanceof PsiLiteralExpression)){
            return false;
        }
        final Double value;
        value = (Double) ConstantExpressionUtil
                .computeCastTo(expression, PsiType.DOUBLE);
        return value != null && value == 0.0;
    }

    /**
     * @noinspection FloatingPointEquality
     */
    private boolean isOne(PsiExpression expression){
        if(m_ignoreExpressionsContainingConstants &&
                !(expression instanceof PsiLiteralExpression)){
            return false;
        }
        final Double value = (Double) ConstantExpressionUtil
                .computeCastTo(expression, PsiType.DOUBLE);
        return value != null && value == 1.0;
    }
}
