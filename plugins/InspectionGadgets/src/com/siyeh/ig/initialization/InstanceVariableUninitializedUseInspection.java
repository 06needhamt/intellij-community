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
package com.siyeh.ig.initialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.InitializationReadUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class InstanceVariableUninitializedUseInspection
        extends FieldInspection {
    /** @noinspection PublicField*/
    public boolean m_ignorePrimitives = false;

    public String getID(){
        return "InstanceVariableUsedBeforeInitialized";
    }
    public String getDisplayName() {
        return "Instance variable used before initialized";
    }

    public String getGroupDisplayName() {
        return GroupNames.INITIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Instance variable #ref used before initialized #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore primitive fields",
                this, "m_ignorePrimitives");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InstanceVariableInitializationVisitor();
    }

    private class InstanceVariableInitializationVisitor
            extends BaseInspectionVisitor {

        private InitializationReadUtils iru = new InitializationReadUtils();

        public void visitField(@NotNull PsiField field) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            if (field.getInitializer() != null) {
                return;
            }
            if (m_ignorePrimitives) {
                final PsiType fieldType = field.getType();
                if (ClassUtils.isPrimitive(fieldType)) {
                    return;
                }
            }
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return;
            }
            final PsiManager manager = field.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            if (searchHelper.isFieldBoundToForm(field)) {
                return;
            }

            if (!isInitializedInInitializer(field)) {
                final PsiMethod[] constructors = aClass.getConstructors();

                for(final PsiMethod constructor : constructors){
                    final PsiCodeBlock body = constructor.getBody();
                    iru.blockMustAssignVariable(field, body);
                }
            }

            final List<PsiExpression> badReads = iru.getUninitializedReads();
            for(PsiExpression expression : badReads){
                registerError(expression);
            }

        }

        private boolean isInitializedInInitializer(PsiField field) {
            final PsiClass aClass = field.getContainingClass();
            if(aClass == null)
            {
                return false;
            }
            final PsiClassInitializer[] initializers = aClass.getInitializers();
            for(final PsiClassInitializer initializer : initializers){
                if(!initializer.hasModifierProperty(PsiModifier.STATIC)){
                    final PsiCodeBlock body = initializer.getBody();
                    if(iru.blockMustAssignVariable(field, body)){
                        return true;
                    }
                }
            }
            return false;
        }

    }
}
