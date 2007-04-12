/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.internationalization;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class StringConcatenationInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean ignoreAsserts = false;

    /** @noinspection PublicField*/
    public boolean ignoreSystemOuts = false;

    /** @noinspection PublicField*/
    public boolean ignoreSystemErrs = false;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.concatenation.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.concatenation.problem.descriptor");
    }

    @Nullable
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "string.concatenation.ignore.assert.option"),
                "ignoreAsserts");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "string.concatenation.ignore.system.out.option"),
                "ignoreSystemOuts");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "string.concatenation.ignore.system.err.option"),
                "ignoreSystemErrs");
        return optionsPanel;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringConcatenationVisitor();
    }

    private class StringConcatenationVisitor
            extends BaseInspectionVisitor {

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiExpression rhs = expression.getROperand();
            if(rhs == null) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!JavaTokenType.PLUS.equals(tokenType)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiType lhsType = lhs.getType();
            final PsiType rhsType = rhs.getType();
            if(!TypeUtils.isJavaLangString(lhsType) &&
               !TypeUtils.isJavaLangString(rhsType)){
                return;
            }
            if (NonNlsUtils.isNonNlsAnnotated(lhs) ||
                    NonNlsUtils.isNonNlsAnnotated(rhs)) {
                return;
            }
            final PsiElement element =
                    PsiTreeUtil.getParentOfType(expression,
                            PsiAssertStatement.class,
                            PsiMethodCallExpression.class);
            if (ignoreAsserts && element instanceof PsiAssertStatement) {
                return;
            }
            if (element instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression)element;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                @NonNls
                final String canonicalText =
                        methodExpression.getCanonicalText();
                if (ignoreSystemOuts &&
                    "System.out.println".equals(canonicalText) ||
                        "System.out.print".equals(canonicalText)) {
                    return;
                }
                if (ignoreSystemErrs &&
                        "System.err.println".equals(canonicalText) ||
                        "System.err.print".equals(canonicalText)) {
                    return;
                }

            }
            if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
                return;
            }
            registerError(sign);
        }
    }
}