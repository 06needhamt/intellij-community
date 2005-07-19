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
package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class WaitCalledOnConditionInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "'wait()' called on Condition object";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Call to '#ref()' on Condition object #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new WaitCalledOnConditionVisitor();
    }

    private static class WaitCalledOnConditionVisitor
                                                      extends BaseInspectionVisitor{

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!"wait".equals(methodName)){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            if(paramList == null){
                return;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            final int numParams = parameters.length;
            if(numParams > 2){
                return;
            }
            if(numParams > 0){
                final PsiType parameterType = parameters[0].getType();
                if(!parameterType.equals(PsiType.LONG)){
                    return;
                }
            }

            if(numParams > 1){
                final PsiType parameterType = parameters[1].getType();
                if(!parameterType.equals(PsiType.INT)){
                    return;
                }
            }

            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(!TypeUtils.expressionHasTypeOrSubtype("java.util.concurrent.locks.Condition",
                                                     qualifier)){
                return;
            }
            registerMethodCallError(expression);
        }
    }
}
