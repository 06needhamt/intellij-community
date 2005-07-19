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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class StaticCallOnSubclassInspection extends ExpressionInspection {
    public String getID(){
        return "StaticMethodReferencedViaSubclass";
    }
    private final StaticCallOnSubclassFix fix = new StaticCallOnSubclassFix();


    public String getDisplayName() {
        return "Static method referenced via subclass";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiReferenceExpression methodExpression = (PsiReferenceExpression) location.getParent();
        assert methodExpression != null;
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) methodExpression.getParent();
        assert methodCall != null;
        final PsiMethod method = methodCall.resolveMethod();
        assert method != null;
        final PsiClass containingClass = method.getContainingClass();
        assert containingClass != null;
        final String declaringClass = containingClass.getName();
        final PsiElement qualifier = methodExpression.getQualifier();
        assert qualifier != null;
        final String referencedClass = qualifier.getText();
        return "Static method '#ref' declared on class " + declaringClass + " but referenced via class " + referencedClass + "    #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class StaticCallOnSubclassFix extends InspectionGadgetsFix {
        public String getName() {
            return "Rationalize static method call";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiIdentifier name = (PsiIdentifier) descriptor.getPsiElement();
            final PsiReferenceExpression expression = (PsiReferenceExpression) name.getParent();
            assert expression != null;
            final PsiMethodCallExpression call = (PsiMethodCallExpression) expression.getParent();
            final String methodName = expression.getReferenceName();
            assert call != null;
            final PsiMethod method = call.resolveMethod();
            assert method != null;
            final PsiClass containingClass = method.getContainingClass();
            final PsiExpressionList argumentList = call.getArgumentList();
            assert containingClass != null;
            final String containingClassName = containingClass.getName();
            assert argumentList != null;
            final String argText = argumentList.getText();
            replaceExpression(call, containingClassName + '.' + methodName + argText );
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticCallOnSubclassVisitor();
    }

    private static class StaticCallOnSubclassVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final PsiElement qualifier = methodExpression.getQualifier();
            if(!(qualifier instanceof PsiReferenceExpression)){
                return;
            }
            final PsiMethod method = call.resolveMethod();
            if(method == null){
                return;
            }
            if(!method.hasModifierProperty(PsiModifier.STATIC))
            {
                return;
            }

            final PsiElement referent = ((PsiReference) qualifier).resolve();
            if (!(referent instanceof PsiClass)) {
                return;
            }
            final PsiClass referencedClass = (PsiClass) referent;


            final PsiClass declaringClass = method.getContainingClass();
            if(declaringClass == null)
            {
                return;
            }
            if (declaringClass.equals(referencedClass)) {
                return;
            }

            final PsiClass containingClass = ClassUtils.getContainingClass(call);
            if(!ClassUtils.isClassVisibleFromClass(containingClass, declaringClass))
            {
                return;
            }
            registerMethodCallError(call);
        }


    }

}
