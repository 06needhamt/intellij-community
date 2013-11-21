package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.codeInsight.template.macro.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.command.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;
import org.jetbrains.postfixCompletion.LookupItems.*;
import org.jetbrains.postfixCompletion.*;

import java.util.*;

@TemplateProvider(
  templateName = "for",
  description = "Iterates over enumerable collection",
  example = "for (T item : collection)")
public final class ForeachIterationTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull PostfixTemplateContext context, @NotNull List<LookupElement> consumer) {
    PrefixExpressionContext expression = context.outerExpression();

    if (!context.executionContext.isForceMode) {
      PsiType expressionType = expression.expressionType;
      if (expressionType == null) return;

      // for-statements can take expressions of array or Iterable<T>-derived types
      if (!(expressionType instanceof PsiArrayType) &&
        !InheritanceUtil.isInheritor(expressionType, CommonClassNames.JAVA_LANG_ITERABLE)) return;
    }

    consumer.add(new ForeachLookupElement(expression));
  }

  private static final class ForeachLookupElement extends StatementPostfixLookupElement<PsiForeachStatement> {
    public ForeachLookupElement(@NotNull PrefixExpressionContext context) {
      super("for", context);
    }

    @NotNull @Override protected PsiForeachStatement createNewStatement(
      @NotNull PsiElementFactory factory, @NotNull PsiElement expression, @NotNull PsiElement context) {

      PsiForeachStatement forStatement = (PsiForeachStatement)
        factory.createStatementFromText("for(T item:expr)", context);

      PsiExpression iteratedValue = forStatement.getIteratedValue();
      assert (iteratedValue != null) : "iteratedValue != null";

      iteratedValue.replace(expression);

      return forStatement;
    }

    @Override protected void postProcess(
      @NotNull final InsertionContext context, @NotNull PsiForeachStatement forStatement) {
      final Project project = context.getProject();
      final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
      final SmartPsiElementPointer<PsiForeachStatement> statementPointer =
        pointerManager.createSmartPsiElementPointer(forStatement);

      final Runnable runnable = new Runnable() {
        @Override public void run() {
          PsiForeachStatement statement = statementPointer.getElement();
          if (statement == null) return;

          // create template for iteration statement
          TemplateBuilderImpl builder = new TemplateBuilderImpl(statement);
          PsiParameter iterationParameter = statement.getIterationParameter();

          // store pointer to iterated value
          PsiExpression iteratedValue = statement.getIteratedValue();
          assert (iteratedValue != null) : "iteratedValue != null";
          final SmartPsiElementPointer<PsiExpression> valuePointer =
            pointerManager.createSmartPsiElementPointer(iteratedValue);

          // use standard macro, pass parameter expression with expression to iterate
          MacroCallNode iterableTypeExpression = new MacroCallNode(new IterableComponentTypeMacro());
          iterableTypeExpression.addParameter(new PsiPointerExpression(valuePointer));

          MacroCallNode nameExpression = new MacroCallNode(new SuggestVariableNameMacro());

          // setup placeholders and final position
          builder.replaceElement(iterationParameter.getTypeElement(), iterableTypeExpression, false);
          builder.replaceElement(iterationParameter.getNameIdentifier(), nameExpression, true);
          builder.setEndVariableAfter(statement.getRParenth());

          // todo: braces insertion?

          // create inline template and place caret before statement
          Template template = builder.buildInlineTemplate();

          Editor editor = context.getEditor();
          CaretModel caretModel = editor.getCaretModel();
          caretModel.moveToOffset(statement.getTextRange().getStartOffset());

          TemplateManager manager = TemplateManager.getInstance(project);
          manager.startTemplate(editor, template);
        }
      };

      context.setLaterRunnable(new Runnable() {
        @Override public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override public void run() {
              CommandProcessor.getInstance().runUndoTransparentAction(runnable);
            }
          });
        }
      });
    }
  }
}