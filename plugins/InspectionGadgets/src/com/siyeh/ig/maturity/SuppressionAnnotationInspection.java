package com.siyeh.ig.maturity;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;

public class SuppressionAnnotationInspection extends ClassInspection{
    public String getDisplayName(){
        return "Inspection suppresion annotation";
    }

    public String getGroupDisplayName(){
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Inspection suppresion annotation #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new SuppressionAnnotationVisitor();
    }

    private static class SuppressionAnnotationVisitor
            extends BaseInspectionVisitor{
        public void visitComment(PsiComment comment){
            super.visitComment(comment);
            final String commentText = comment.getText();
            final IElementType tokenType = comment.getTokenType();
            if(!tokenType.equals(JavaTokenType.END_OF_LINE_COMMENT)
                    && !tokenType.equals(JavaTokenType.C_STYLE_COMMENT)){
                return;
            }
            final String strippedComment = commentText.substring(2).trim();
            if(strippedComment.startsWith("noinspection")){
                registerError(comment);
            }
        }

        public void visitAnnotation(PsiAnnotation annotation){
            super.visitAnnotation(annotation);
            final PsiJavaCodeReferenceElement reference =
                    annotation.getNameReferenceElement();
            if(reference == null)
            {
                return;
            }
            final String text = reference.getText();

            if("SuppressWarnings".equals(text) ||
                    "java.lang.SuppressWarnings".equals(text)){
                registerError(annotation);
            }
        }
    }
}
