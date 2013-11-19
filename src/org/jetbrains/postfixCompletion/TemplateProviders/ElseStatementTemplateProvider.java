package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;

import java.util.*;

// todo: fix 'parametersCount == 0.else' case

@TemplateProvider(
  templateName = "else",
  description = "Checks boolean expression to be 'false'",
  example = "if (!expr)")
public final class ElseStatementTemplateProvider extends BooleanTemplateProviderBase {
  @Override public boolean createBooleanItems(
    @NotNull PrefixExpressionContext context, @NotNull List<LookupElement> consumer) {
    if (context.canBeStatement) {
      consumer.add(new ElseLookupItem(context));
      return true;
    }

    return false;
  }

  static final class ElseLookupItem extends IfStatementPostfixLookupItem {
    public ElseLookupItem(@NotNull PrefixExpressionContext context) {
      super("else", context);
    }

    @Override protected void processStatement(
        @NotNull PsiElementFactory factory, @NotNull PsiIfStatement ifStatement, @NotNull PsiElement expression) {
      PsiExpression condition = ifStatement.getCondition();
      assert (condition != null) : "condition != null";

      PsiExpression inverted = CodeInsightServicesUtil.invertCondition((PsiExpression) expression);
      condition.replace(inverted);
    }
  }
}
