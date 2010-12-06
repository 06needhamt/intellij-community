package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Icons;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;


import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * User: catherine
 *
 * Complete known keys for dictionaries
 */
public class PyDictKeyNamesCompletionContributor extends PySeeingOriginalCompletionContributor {

  private static final FilterPattern DICT_KEY = new FilterPattern(
    new InSequenceFilter(psiElement(PySubscriptionExpression.class))
  );

  public PyDictKeyNamesCompletionContributor() {
    extend(
      CompletionType.BASIC,
      psiElement()
        .withLanguage(PythonLanguage.getInstance())
        .and(DICT_KEY)
     ,
      new CompletionProvider<CompletionParameters>() {
        protected void addCompletions(
          @NotNull final CompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result
        ) {
          PsiElement original = parameters.getOriginalPosition();
          int offset = parameters.getOffset();
          final CompletionResultSet dictCompletion = createResult(original, result, offset);

          PsiElement operand = PsiTreeUtil.getParentOfType(original, PySubscriptionExpression.class).getOperand();
          if (operand != null) {
            PsiReference reference = operand.getReference();
            if (reference != null) {
              PsiElement resolvedElement = reference.resolve();
              if (resolvedElement instanceof PyTargetExpression) {
                PyDictLiteralExpression dict = PsiTreeUtil.getNextSiblingOfType(resolvedElement, PyDictLiteralExpression.class);
                if (dict != null) {
                  addDictLiteralKeys(dict, dictCompletion);
                  PsiFile file = parameters.getOriginalFile();
                  addAdditionalKeys(file, operand, dictCompletion);
                }
                PyCallExpression dictConstructor = PsiTreeUtil.getNextSiblingOfType(resolvedElement, PyCallExpression.class);
                if (dictConstructor != null) {
                  addDictConstructorKeys(dictConstructor, dictCompletion);
                  PsiFile file = parameters.getOriginalFile();
                  addAdditionalKeys(file, operand, dictCompletion);
                }
              }
            }
          }
          // filter unusable lookup elements
          dictCompletion.runRemainingContributors(parameters, new Consumer<LookupElement>() {
            public void consume(final LookupElement lookupElement) {
              if (lookupElement.getLookupString().equals("dict key"))
                dictCompletion.addElement(lookupElement);
            }
          });
        }
      }
    );
  }

  /**
   * create completion result with prefix matcher if needed
   *
   * @param original is original element
   * @param result is initial completion result
   * @param offset
   * @return
   */
  private static CompletionResultSet createResult(PsiElement original, CompletionResultSet result, int offset) {
    PsiElement prevElement = original.getPrevSibling();
    if (prevElement != null) {
      ASTNode prevNode = prevElement.getNode();
      if (prevNode != null) {
        if (prevNode.getElementType() != PyTokenTypes.LBRACKET)
          return result.withPrefixMatcher(findPrefix(prevElement, offset));
      }
    }
    PsiElement parentElement = original.getParent();
    if (parentElement != null) {
      if (parentElement instanceof PyStringLiteralExpression)
        return result.withPrefixMatcher(findPrefix(parentElement, offset));
    }
    return result;
  }

  /**
   * finds prefix. For *'str'* returns just *'str*.
   * @param element to find prefix of
   * @return prefix
   */
  private static String findPrefix(final PsiElement element, int offset) {
    if (element instanceof PyStringLiteralExpression) {
      String text = TextRange.create(element.getTextRange().getStartOffset(), offset).substring(element.getContainingFile().getText());
      char[] chs = text.toCharArray();
      char start = chs[0];
      StringBuilder builder = new StringBuilder();
      builder.append(start);
      for (int i = 1; i != chs.length; ++i) {
        char ch = chs[i];
        if (ch == ']' || ch == '\n') break;
        if (ch == start) {
          break;
        }
        builder.append(ch);
      }
      return builder.toString();
    }
    return element.getText();
  }

  /**
   * add keys to completion result from dict constructor
   */
  private static void addDictConstructorKeys(PyCallExpression dictConstructor, CompletionResultSet result) {
    String name = dictConstructor.getCallee().getText();
    if ("dict".equals(name)) {
      PyType type = dictConstructor.getType(TypeEvalContext.fast());
      if (type != null) {
        if (type.isBuiltin()) {
          PyExpression[] argumentList = dictConstructor.getArgumentList().getArguments();
          for (PyExpression argument : argumentList) {
            if (argument instanceof PyKeywordArgument) {
              LookupElementBuilder item;
              item = LookupElementBuilder
                .create("'" + ((PyKeywordArgument)argument).getKeyword() + "'")
                .setTypeText("dict key")
                .setIcon(Icons.PARAMETER_ICON);
              result.addElement(item);
            }
          }
        }
      }
    }
  }

  /**
   * add keys from assignment statements
   * For instance, dictionary['b']=b
   * @param file to get additional keys
   * @param operand is operand of origin element
   * @param result is completion result set
   */
  private static void addAdditionalKeys(PsiFile file, PsiElement operand, CompletionResultSet result) {
    PySubscriptionExpression[] subscriptionExpressions = PyUtil.getAllChildrenOfType(file, PySubscriptionExpression.class);
    for (PySubscriptionExpression expr : subscriptionExpressions) {
      if (expr.getOperand().getText().equals(operand.getText())) {
        PsiElement parent = expr.getParent();
        if (parent instanceof PyAssignmentStatement) {
          if (expr.equals(((PyAssignmentStatement)parent).getLeftHandSideExpression())) {
            PyExpression key = expr.getIndexExpression();
            if (key != null) {
              LookupElementBuilder item;
              item = LookupElementBuilder
                .create(key.getText())
                .setTypeText("dict key")
                .setIcon(Icons.PARAMETER_ICON);
              result.addElement(item);
            }
          }
        }
      }
    }
  }

  /**
   * add keys from dict literal expression
   */
  private static void addDictLiteralKeys(PyDictLiteralExpression dict, CompletionResultSet result) {
    PyKeyValueExpression[] keyValues = dict.getElements();
    for (PyKeyValueExpression expression : keyValues) {
      LookupElementBuilder item;
      item = LookupElementBuilder
        .create(expression.getKey().getText())
        .setTypeText("dict key")
        .setIcon(Icons.PARAMETER_ICON);
      result.addElement(item);
    }
  }
  @Override
  public void duringCompletion(@NotNull CompletionInitializationContext context) {
    PyStringLiteralExpression str = PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(),
                                                                           PyStringLiteralExpression.class, false);
    if (str != null) {
      if (str.getText().startsWith("'") && str.getText().endsWith("'") ||
          str.getText().startsWith("\"") && str.getText().endsWith("\""))
        context.setReplacementOffset(context.getStartOffset()+1);
    }
  }
}
