/*
 * Copyright 2006-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class BeforeOrAfterIsPublicVoidNoArgInspection extends BaseInspection {

    @NotNull
    public String getID() {
        return "BeforeOrAfterWithIncorrectSignature";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "before.or.after.is.public.void.no.arg.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "before.or.after.is.public.void.no.arg.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new BeforeOrAfterIsPublicVoidNoArgVisitor();
    }

    private static class BeforeOrAfterIsPublicVoidNoArgVisitor
            extends BaseInspectionVisitor {

        @Override public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super;
            if (!AnnotationUtil.isAnnotated(method, "org.junit.Before", true) &&
                    !AnnotationUtil.isAnnotated(method,
                            "org.junit.After", true)) {
                return;
            }
            final PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return;
            }
            final PsiClass targetClass = method.getContainingClass();
            if (targetClass == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 0) {
                registerMethodError(method);
            } else if (!returnType.equals(PsiType.VOID)) {
                registerMethodError(method);
            } else if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                registerMethodError(method);
            } else if (method.hasModifierProperty(PsiModifier.STATIC)) {
                registerMethodError(method);
            }
        }
    }
}