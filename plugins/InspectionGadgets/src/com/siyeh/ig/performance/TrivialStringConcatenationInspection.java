package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TrivialStringConcatenationInspection extends ExpressionInspection{
    /** @noinspection StaticCollection*/
    private static final Map<String,String> s_typeToWrapperMap = new HashMap<String, String>(6);

    static{
        s_typeToWrapperMap.put("char", "Character");
        s_typeToWrapperMap.put("short", "Short");
        s_typeToWrapperMap.put("int", "Integer");
        s_typeToWrapperMap.put("long", "Long");
        s_typeToWrapperMap.put("float", "Float");
        s_typeToWrapperMap.put("double", "Double");
        s_typeToWrapperMap.put("boolean", "Boolean");
        s_typeToWrapperMap.put("byte", "Byte");
    }

    public String getID(){
        return "ConcatenationWithEmptyString";
    }

    public String getDisplayName(){
        return "Concatenation with empty string";
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final String replacementString =
                calculateReplacementExpression(location);
        return "#ref can be simplified to " + replacementString + " #loc";
    }

    private static String calculateReplacementExpression(PsiElement location){
        final PsiBinaryExpression expression = (PsiBinaryExpression) location;
        final PsiExpression lOperand = expression.getLOperand();
        final PsiExpression rOperand = expression.getROperand();
        final PsiExpression replacement;
        if(isEmptyString(lOperand)){
            replacement = rOperand;
        } else{

            replacement = lOperand;
        }
        final PsiType type = replacement.getType();
        final String text = type.getCanonicalText();
        if(s_typeToWrapperMap.containsKey(text)){
            return s_typeToWrapperMap.get(text) + ".toString(" +
                    replacement.getText() + ')';
        } else if("java.lang.String".equals(text)){
            return replacement.getText();
        } else{
            return replacement.getText() + ".toString()";
        }
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new UnnecessaryTemporaryObjectFix((PsiBinaryExpression) location);
    }

    private static class UnnecessaryTemporaryObjectFix
            extends InspectionGadgetsFix{
        private final String m_name;

        private UnnecessaryTemporaryObjectFix(PsiBinaryExpression expression){
            super();
            m_name = "Replace  with " +
                    calculateReplacementExpression(expression);
        }

        public String getName(){
            return m_name;
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            final PsiBinaryExpression expression =
                    (PsiBinaryExpression) descriptor.getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new TrivialStringConcatenationVisitor();
    }

    private static class TrivialStringConcatenationVisitor
            extends BaseInspectionVisitor{


        public void visitBinaryExpression(@NotNull PsiBinaryExpression exp){
            super.visitBinaryExpression(exp);
            if(!WellFormednessUtils.isWellFormed(exp))
            {
                return;
            }
            if(!TypeUtils.expressionHasType("java.lang.String", exp)){
                return;
            }
            final PsiExpression lhs = exp.getLOperand();
            final PsiExpression rhs = exp.getROperand();
            if(isEmptyString(lhs)){
                if(isStringLiteral(rhs)){
                    return;
                }
                registerError(exp);
            } else if(isEmptyString(rhs)){
                if(isStringLiteral(lhs)){
                    return;
                }
                registerError(exp);
            }
        }
    }

    private static boolean isStringLiteral(PsiExpression expression){
        if(!(expression instanceof PsiLiteralExpression)){
            return false;
        }
        return TypeUtils.expressionHasType("java.lang.String", expression);
    }

    private static boolean isEmptyString(PsiExpression exp){
        if(!(exp instanceof PsiLiteralExpression)){
            return false;
        }
        final String text = exp.getText();
        return "\"\"".equals(text);
    }
}
