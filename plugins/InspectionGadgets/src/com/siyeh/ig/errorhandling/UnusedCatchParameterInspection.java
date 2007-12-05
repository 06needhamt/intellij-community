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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnusedCatchParameterInspection extends BaseInspection {

    /** @noinspection PublicField */
    public boolean m_ignoreCatchBlocksWithComments = false;
    /** @noinspection PublicField */
    public boolean m_ignoreTestCases = false;
    /** @noinspecion PublicField */
    public boolean m_ignoreIgnoreParameter = true;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unused.catch.parameter.display.name");
    }

    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "unused.catch.parameter.ignore.catch.option"),
                "m_ignoreCatchBlocksWithComments");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "unused.catch.parameter.ignore.empty.option"),
                "m_ignoreTestCases");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "unused.catch.parameter.ignore.name.option"),
                "m_ignoreIgnoreParameter");
        return optionsPanel;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unused.catch.parameter.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyCatchBlockVisitor();
    }

    private class EmptyCatchBlockVisitor extends BaseInspectionVisitor {

        @Override public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            if (m_ignoreTestCases && TestUtils.isPartOfJUnitTestMethod(
                    statement)) {
                return;
            }
            final PsiCatchSection[] catchSections =
                    statement.getCatchSections();
            for (PsiCatchSection catchSection : catchSections) {
                checkCatchSection(catchSection);
            }
        }

        private void checkCatchSection(PsiCatchSection section) {
            final PsiParameter parameter = section.getParameter();
            if (parameter == null) {
                return;
            }
            @NonNls final String parametername = parameter.getName();
            if (m_ignoreIgnoreParameter &&
                    ("ignore".equals(parametername) ||
                            "ignored".equals(parametername))) {
                return;
            }
            final PsiCodeBlock block = section.getCatchBlock();
            if (block == null) {
                return;
            }
            if (m_ignoreCatchBlocksWithComments) {
                final PsiElement[] children = block.getChildren();
                for (final PsiElement child : children) {
                    if (child instanceof PsiComment) {
                        return;
                    }
                }
            }
            final CatchParameterUsedVisitor visitor =
                    new CatchParameterUsedVisitor(parameter);
            block.accept(visitor);
            if (visitor.isUsed()) {
                return;
            }
            registerVariableError(parameter);
        }
    }
}