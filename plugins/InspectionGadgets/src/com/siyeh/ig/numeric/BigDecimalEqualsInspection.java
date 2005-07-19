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
package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.bugs.IsEqualsUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class BigDecimalEqualsInspection extends ExpressionInspection {
    private final BigDecimalEqualsFix fix = new BigDecimalEqualsFix();

    public String getDisplayName() {
        return "'.equals()' called on BigDecimal";
    }

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return ".#ref() between BigDecimal values should probably be .compareTo() #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class BigDecimalEqualsFix extends InspectionGadgetsFix {
        public String getName() {
            return "replace with .compareTo()==0";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiIdentifier name = (PsiIdentifier) descriptor.getPsiElement();
            final PsiReferenceExpression expression = (PsiReferenceExpression) name.getParent();
            assert expression != null;
            final PsiMethodCallExpression call = (PsiMethodCallExpression) expression.getParent();
            final PsiExpression qualifier = expression.getQualifierExpression();
            final String qualifierText = qualifier.getText();
            assert call != null;
            final PsiExpressionList argumentList = call.getArgumentList();
            assert argumentList != null;
            final PsiExpression[] args = argumentList.getExpressions();
            final String argText = args[0].getText();
            replaceExpression(call, qualifierText + ".compareTo(" + argText + ")==0");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new BigDecimalEqualsVisitor();
    }

    private static class BigDecimalEqualsVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if(!IsEqualsUtil.isEquals(expression)){
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final PsiExpressionList argumentList = expression.getArgumentList();
            assert argumentList != null;
            final PsiExpression[] args = argumentList.getExpressions();

            final PsiExpression arg = args[0];
            if (!TypeUtils.expressionHasType("java.math.BigDecimal", arg)) {
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (!TypeUtils.expressionHasType("java.math.BigDecimal", qualifier)) {
                return;
            }
            final PsiElement context = expression.getParent();
            if (context instanceof PsiExpressionStatement) {
                return; //cheesy, but necessary, because otherwise the quickfix will produce
                //uncompilable code (out of merely incorrect code).
            }
            registerMethodCallError(expression);
        }

    }

}
