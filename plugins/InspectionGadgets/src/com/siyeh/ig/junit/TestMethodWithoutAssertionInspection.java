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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiNameValuePair;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class TestMethodWithoutAssertionInspection extends ExpressionInspection {

    public String getID() {
        return "JUnitTestMethodWithNoAssertions";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }


    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "test.method.without.assertion.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TestMethodWithoutAssertionVisitor();
    }

    private static class TestMethodWithoutAssertionVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (!TestUtils.isJUnitTestMethod(method)) {
                return;
            }
            final PsiAnnotation testAnnotation = AnnotationUtil.findAnnotation(
                    method, Collections.singleton("org.junit.Test"));
            if (testAnnotation != null) {
                final PsiAnnotationParameterList parameterList =
                        testAnnotation.getParameterList();
                final PsiNameValuePair[] nameValuePairs =
                        parameterList.getAttributes();
                for (PsiNameValuePair nameValuePair : nameValuePairs) {
                    final String parameterName = nameValuePair.getName();
                    if ("expected".equals(parameterName)) {
                        return;
                    }
                }
            }
            final ContainsAssertionVisitor visitor = new ContainsAssertionVisitor();
            method.accept(visitor);
            if (visitor.containsAssertion()) {
                return;
            }
            registerMethodError(method);
        }
    }
}
