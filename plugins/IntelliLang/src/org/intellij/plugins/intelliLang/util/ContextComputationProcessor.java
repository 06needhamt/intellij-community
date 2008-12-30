/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class that can compute the prefix and suffix of an expression inside a binary (usually additive) expression
 * that computes the values not only for compile-time constants, but also for elements annotated with a substitution
 * annotation.
 *
 * @see org.intellij.plugins.intelliLang.util.SubstitutedExpressionEvaluationHelper
 */
public class ContextComputationProcessor {

  private final SubstitutedExpressionEvaluationHelper myEvaluationHelper;

  private ContextComputationProcessor(final Project project) {
    myEvaluationHelper = new SubstitutedExpressionEvaluationHelper(project);
  }

  @Nullable
  private static PsiElement getContext(@NotNull PsiElement place) {
    final PsiConditionalExpression conditionalExpression = PsiTreeUtil.getParentOfType(place, PsiConditionalExpression.class);
    if (conditionalExpression != null && PsiTreeUtil.isAncestor(conditionalExpression.getCondition(), place, false)) return null;

    PsiElement element = place;
    PsiElement prev = place;
    while (true) {
      final PsiElement parent = element.getParent();

      if (!(parent instanceof PsiBinaryExpression ||
            parent instanceof PsiParenthesizedExpression ||
            parent instanceof PsiTypeCastExpression ||
            parent instanceof PsiConditionalExpression)) {
        break;
      }
      prev = element;
      element = parent;
    }
    if (element instanceof PsiConditionalExpression) {
      return prev;
    }

    return element;
  }

  @NotNull
  public static List<Object> collectOperands(@NotNull final PsiExpression place, final String prefix, final String suffix, final Ref<Boolean> unparsable) {
    final PsiElement parent = getContext(place);
    if (parent == null) return Collections.emptyList();

    final ArrayList<Object> result = new ArrayList<Object>();
    addStringFragment(prefix, result);
    new ContextComputationProcessor(place.getProject()).collectOperands(parent, result, unparsable);
    addStringFragment(suffix, result);
    return result;
  }

  private static void addStringFragment(final String string, final List<Object> result) {
    if (StringUtil.isEmpty(string)) return;
    final int size = result.size();
    final Object last = size > 0? result.get(size -1) : null;
    if (last instanceof String) {
      result.set(size - 1, last + string);
    }
    else {
      result.add(string);
    }
  }

  public void collectOperands(final PsiElement expression, final List<Object> result, final Ref<Boolean> unparsable) {
    final PsiElement firstChild;
    if (expression instanceof PsiParenthesizedExpression) {
      collectOperands(((PsiParenthesizedExpression)expression).getExpression(), result, unparsable);
    }
    else if (expression instanceof PsiTypeCastExpression) {
      collectOperands(((PsiTypeCastExpression)expression).getOperand(), result, unparsable);
    }
    else if (expression instanceof PsiConditionalExpression) {
      unparsable.set(Boolean.TRUE);
      collectOperands(((PsiConditionalExpression)expression).getThenExpression(), result, unparsable);
      collectOperands(((PsiConditionalExpression)expression).getElseExpression(), result, unparsable);
    }
    else if (expression instanceof PsiBinaryExpression &&
             ((PsiBinaryExpression)expression).getOperationSign().getTokenType() == JavaTokenType.PLUS) {
      PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      collectOperands(binaryExpression.getLOperand(), result, unparsable);
      collectOperands(binaryExpression.getROperand(), result, unparsable);
    }
    else if (expression instanceof PsiLanguageInjectionHost &&
             (firstChild = expression.getFirstChild()) instanceof PsiJavaToken &&
             ((PsiJavaToken)firstChild).getTokenType() == JavaTokenType.STRING_LITERAL) {
      result.add(expression);
    }
    else {
      final Object o = expression instanceof PsiExpression? myEvaluationHelper.computeSimpleExpression((PsiExpression)expression) : null;
      if (o == null) result.add(expression);
      else addStringFragment(String.valueOf(o), result);
    }
  }
}
