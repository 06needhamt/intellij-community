/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ArrayEqualsInspection extends ExpressionInspection{
    private InspectionGadgetsFix fix = new ArrayEqualsFix();

    public String getDisplayName(){
        return "'.equals()' called on array type";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return ".#ref() between arrays should probably be Arrays.equals() #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class ArrayEqualsFix extends InspectionGadgetsFix{
        public String getName(){
            return "replace with Arrays.equals";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiIdentifier name =
                    (PsiIdentifier) descriptor.getPsiElement();
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) name.getParent();
            assert expression != null;
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) expression.getParent();
            final PsiExpression qualifier = expression.getQualifierExpression();
            final String qualifierText = qualifier.getText();
            assert call != null;
            final PsiExpressionList argumentList = call.getArgumentList();
            assert argumentList != null;
            final PsiExpression[] args = argumentList.getExpressions();
            final String argText = args[0].getText();
            final String newExpressionText =
                    "java.util.Arrays.equals(" + qualifierText + ", " +
                    argText + ')';
            replaceExpressionAndShorten(project, call, newExpressionText);
        }

        private void replaceExpressionAndShorten(Project project,
                                                 PsiMethodCallExpression call,
                                                 String newExpressionText)
                throws IncorrectOperationException{
                final PsiManager manager = PsiManager.getInstance(project);
                final PsiElementFactory factory = manager.getElementFactory();
                final PsiExpression newExp =
                        factory.createExpressionFromText(newExpressionText,
                                                         null);
                final PsiElement replacementExp = call.replace(newExp);
                final CodeStyleManager styleManager =
                        manager.getCodeStyleManager();
                styleManager.reformat(replacementExp);
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ArrayEqualsVisitor();
    }

    private static class ArrayEqualsVisitor extends BaseInspectionVisitor{

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            if(!IsEqualsUtil.isEquals(expression))
            {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpressionList argumentList = expression.getArgumentList();
            assert argumentList != null;
            final PsiExpression[] args = argumentList.getExpressions();
            final PsiExpression arg = args[0];
            if(arg == null){
                return;
            }
            final PsiType argType = arg.getType();
            if(!(argType instanceof PsiArrayType)){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier == null){
                return;
            }
            final PsiType qualifierType = qualifier.getType();
            if(!(qualifierType instanceof PsiArrayType)){
                return;
            }
            registerMethodCallError(expression);
        }
    }
}