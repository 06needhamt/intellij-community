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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class InnerClassReferenceVisitor extends PsiRecursiveElementVisitor {
    private PsiClass innerClass;
    private boolean referencesStaticallyAccessible = true;

    public InnerClassReferenceVisitor(PsiClass innerClass) {
        super();
        this.innerClass = innerClass;
    }

    public boolean areReferenceStaticallyAccessible() {
        return referencesStaticallyAccessible;
    }

    private boolean isClassStaticallyAccessible(PsiClass aClass) {
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
            return true;
        }
        if (InheritanceUtil.isInheritorOrSelf(innerClass, aClass, true)) {
            return true;
        }
        PsiClass classScope = aClass;
        final PsiClass outerClass = PsiTreeUtil.getParentOfType(innerClass, PsiClass.class);
        while (classScope != null) {
            if (InheritanceUtil.isInheritorOrSelf(outerClass, classScope, true)) {
                return false;
            }
            final PsiElement scope = classScope.getScope();
            if (scope instanceof PsiClass) {
                classScope = (PsiClass) scope;
            } else {
                classScope = null;
            }
        }
        return true;
    }

    public void visitThisExpression(@NotNull PsiThisExpression expression){
        if(!referencesStaticallyAccessible)
        {
            return;
        }
        super.visitThisExpression(expression);
        final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
        if(qualifier == null)
        {
            return;
        }
        referencesStaticallyAccessible = false;
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement referenceElement) {
        if(!referencesStaticallyAccessible){
            return;
        }
        super.visitReferenceElement(referenceElement);
        final PsiElement element = referenceElement.resolve();
        if (!(element instanceof PsiClass)) {
            return;
        }
        final PsiClass aClass = (PsiClass) element;
        final PsiElement scope = aClass.getScope();
        if (!(scope instanceof PsiClass)) {
            return;
        }
        referencesStaticallyAccessible &=
                aClass.hasModifierProperty(PsiModifier.STATIC);
    }

    public void visitReferenceExpression(@NotNull PsiReferenceExpression referenceExpression) {
        if(!referencesStaticallyAccessible){
            return;
        }
        super.visitReferenceExpression(referenceExpression);
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();


        if (qualifier instanceof PsiSuperExpression) {
            return;
        }
        if (qualifier instanceof PsiReferenceExpression) {
            final PsiReferenceExpression expression = (PsiReferenceExpression) qualifier;
            final PsiElement resolvedExpression = expression.resolve();
            if (!(resolvedExpression instanceof PsiField) &&
                    !(resolvedExpression instanceof PsiMethod)) {
                return;
            }
        }
        final PsiElement element = referenceExpression.resolve();
        if (element instanceof PsiMethod || element instanceof PsiField) {
            final PsiMember member = (PsiMember) element;
            if (member.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiClass containingClass = member.getContainingClass();
            referencesStaticallyAccessible &=
                    isClassStaticallyAccessible(containingClass);
        }
        if(element instanceof PsiLocalVariable || element instanceof PsiParameter)
        {
            final PsiElement containingMethod =
                    PsiTreeUtil.getParentOfType(referenceExpression, PsiMethod.class);
            final PsiElement referencedMethod =
                    PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if(containingMethod!=null && referencedMethod!=null &&
                    !containingMethod.equals(referencedMethod))
            {
                referencesStaticallyAccessible = false;
            }
        }
    }
}