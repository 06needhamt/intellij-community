package com.siyeh.ipp.bool;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class RemoveBooleanEqualityIntention extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) element;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        return "Simplify " + sign.getText();
    }

    public String getFamilyName(){
        return "Remove Boolean Equality";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new BooleanLiteralEqualityPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiBinaryExpression exp =
                (PsiBinaryExpression) element;
        assert exp != null;
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final boolean isEquals = JavaTokenType.EQEQ.equals(tokenType);
        final PsiExpression lhs = exp.getLOperand();
        final String lhsText = lhs.getText();
        final PsiExpression rhs = exp.getROperand();
        assert rhs != null;
        final String rhsText = rhs.getText();
        if("true".equals(lhsText)){
            if(isEquals){
                replaceExpression(rhsText, exp);
            } else{
                replaceExpressionWithNegatedExpression(rhs, exp);
            }
        } else if("false".equals(lhsText)){
            if(isEquals){
                replaceExpressionWithNegatedExpression(rhs, exp);
            } else{
                replaceExpression(rhsText, exp);
            }
        } else if("true".equals(rhsText)){
            if(isEquals){
                replaceExpression(lhsText, exp);
            } else{
                replaceExpressionWithNegatedExpression(lhs, exp);
            }
        } else{
            if(isEquals){
                replaceExpressionWithNegatedExpression(lhs, exp);
            } else{
                replaceExpression(lhsText, exp);
            }
        }
    }
}
