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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.*;

public class MissortedModifiersInspection extends ClassInspection {

    /** @noinspection StaticCollection*/
    @NonNls static final Map<String, Integer> s_modifierOrder =
            new HashMap<String, Integer>(11);

    /** @noinspection PublicField*/
    public boolean m_requireAnnotationsFirst = true;

    static {
        s_modifierOrder.put(PsiKeyword.PUBLIC, Integer.valueOf(0));
        s_modifierOrder.put(PsiKeyword.PROTECTED, Integer.valueOf(1));
        s_modifierOrder.put(PsiKeyword.PRIVATE, Integer.valueOf(2));
        s_modifierOrder.put(PsiKeyword.STATIC, Integer.valueOf(3));
        s_modifierOrder.put(PsiKeyword.ABSTRACT, Integer.valueOf(4));
        s_modifierOrder.put(PsiKeyword.FINAL, Integer.valueOf(5));
        s_modifierOrder.put(PsiKeyword.TRANSIENT, Integer.valueOf(6));
        s_modifierOrder.put(PsiKeyword.VOLATILE, Integer.valueOf(7));
        s_modifierOrder.put(PsiKeyword.SYNCHRONIZED, Integer.valueOf(8));
        s_modifierOrder.put(PsiKeyword.NATIVE, Integer.valueOf(9));
        s_modifierOrder.put("strictfp", Integer.valueOf(10));
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "missorted.modifiers.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    @Nullable
    protected String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message(
                "missorted.modifiers.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MissortedModifiersVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new SortModifiersFix();
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "missorted.modifiers.require.option"),
                this, "m_requireAnnotationsFirst");
    }

    private static class SortModifiersFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "missorted.modifiers.sort.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {

            final PsiModifierList modifierList =
                    (PsiModifierList)descriptor.getPsiElement();
            final List<String> modifiers = new ArrayList<String>();
            final PsiElement[] children = modifierList.getChildren();
            for (final PsiElement child : children) {
                if (child instanceof PsiJavaToken) {
                    modifiers.add(child.getText());
                }
                if (child instanceof PsiAnnotation) {
                    modifiers.add(0, child.getText());
                }
            }
            Collections.sort(modifiers, new ModifierComparator());
            @NonNls final StringBuffer buffer = new StringBuffer();
            for (String modifier : modifiers) {
                buffer.append(modifier);
                buffer.append(' ');
            }
            final PsiManager manager = modifierList.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            buffer.append("void x() {}");
            final String text = buffer.toString();
            final PsiMethod method =
                    factory.createMethodFromText(text, modifierList);
            final PsiModifierList newModifierList = method.getModifierList();
            modifierList.replace(newModifierList);
        }
    }

    private class MissortedModifiersVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            super.visitClass(aClass);
            checkForMissortedModifiers(aClass);
        }

        public void visitClassInitializer(
                @NotNull PsiClassInitializer initializer) {
            super.visitClassInitializer(initializer);
            checkForMissortedModifiers(initializer);
        }

        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            checkForMissortedModifiers(variable);
        }

        public void visitParameter(@NotNull PsiParameter parameter) {
            super.visitParameter(parameter);
            checkForMissortedModifiers(parameter);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            checkForMissortedModifiers(method);
        }

        public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            checkForMissortedModifiers(field);
        }

        private void checkForMissortedModifiers(
                PsiModifierListOwner listOwner) {
            final PsiModifierList modifierList = listOwner.getModifierList();
            if (!isModifierListMissorted(modifierList)) {
                return;
            }
            registerError(modifierList);
        }

        private boolean isModifierListMissorted(PsiModifierList modifierList) {
            if (modifierList == null) {
                return false;
            }
            final PsiElement[] children = modifierList.getChildren();
            int currentModifierIndex = -1;
            for (final PsiElement child : children) {
                if (child instanceof PsiJavaToken) {
                    final String text = child.getText();
                    final Integer modifierIndex = s_modifierOrder.get(text);
                    if (modifierIndex == null) {
                        return false;
                    }
                    if (currentModifierIndex >= modifierIndex.intValue()) {
                        return true;
                    }
                    currentModifierIndex = modifierIndex.intValue();
                }
                if (child instanceof PsiAnnotation) {
                    if (m_requireAnnotationsFirst &&
                        currentModifierIndex != -1) {
                        //things aren't in order, since annotations come first
                        return true;
                    }
                }
            }
            return false;
        }
    }
}