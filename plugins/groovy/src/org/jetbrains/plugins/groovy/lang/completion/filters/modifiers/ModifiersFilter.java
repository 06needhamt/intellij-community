/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.completion.filters.modifiers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author ilyas
 */
public class ModifiersFilter implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if (GroovyCompletionUtil.asSimpleVariable(context) ||
        GroovyCompletionUtil.asTypedMethod(context)) {
      return true;
    }
    if (context.getParent() instanceof GrReferenceElement &&
        context.getParent().getParent() instanceof GrTypeElement) {
      PsiElement parent = context.getParent().getParent().getParent();
      if (parent instanceof GrVariableDeclaration &&
          (parent.getParent() instanceof GrTypeDefinitionBody ||
          parent.getParent() instanceof GroovyFile)
          || parent instanceof GrMethod) {
        GrModifierList list = ((GrMembersDeclaration) parent).getModifierList();
        for (PsiElement modifier : list.getModifiers()) {
          if (!(modifier instanceof GrAnnotation)) return false;
          if ("def".equals(modifier.getText())) return false;
        }
        return true;
      }
    }
    if (context.getParent() instanceof GrExpression &&
        context.getParent().getParent() instanceof GroovyFile &&
        GroovyCompletionUtil.isNewStatement(context, false)) {
      return true;
    }
    if (context.getTextRange().getStartOffset() == 0 && !(context instanceof OuterLanguageElement)) {
      return true;
    }
    final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    if (leaf != null &&
        GroovyCompletionUtil.isNewStatement(context, false)) {
      PsiElement parent = leaf.getParent();
      if (parent instanceof GroovyFile) {
        return true;
      }
    }
    return context.getParent() instanceof GrExpression &&
        context.getParent().getParent() instanceof GrApplicationStatement &&
        context.getParent().getParent().getParent() instanceof GroovyFile &&
        GroovyCompletionUtil.isNewStatement(context, false);
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @NonNls
  public String toString() {
    return "First filter for modifier keywords";
  }

}