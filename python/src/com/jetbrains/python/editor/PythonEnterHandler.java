package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.AutoHardWrapHandler;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PythonEnterHandler implements EnterHandlerDelegate {
  private static final Class[] IMPLICIT_WRAP_CLASSES = new Class[]{
    PsiComment.class,
    PyStringLiteralExpression.class,
    PyParenthesizedExpression.class,
    PyListCompExpression.class,
    PyDictCompExpression.class,
    PySetCompExpression.class,
    PyDictLiteralExpression.class,
    PySetLiteralExpression.class,
    PyListLiteralExpression.class,
    PyArgumentList.class,
    PyParameterList.class,
    PyFunction.class,
    PySliceExpression.class,
    PySubscriptionExpression.class,
  };

  @Override
  public Result preprocessEnter(PsiFile file,
                                Editor editor,
                                Ref<Integer> caretOffset,
                                Ref<Integer> caretAdvance,
                                DataContext dataContext,
                                EditorActionHandler originalHandler) {
    if (!(file instanceof PyFile)) {
      return Result.Continue;
    }
    Document doc = editor.getDocument();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(doc);
    final int offset = caretOffset.get();
    final PsiElement element = file.findElementAt(offset);
    if (element == null) {
      return Result.Continue;
    }
    if (offset > 0) {
      final PsiElement beforeCaret = file.findElementAt(offset-1);
      if (beforeCaret instanceof PsiWhiteSpace && beforeCaret.getText().indexOf('\\') > 0) {
        // we've got a backslash at EOL already, don't need another one
        return Result.Continue;
      }
    }
    PsiElement statementBefore = findStatementBeforeCaret(file, offset);
    PsiElement statementAfter = findStatementAfterCaret(file, offset);
    if (statementBefore != statementAfter) {  // Enter pressed at statement break
      return Result.Continue;
    }
    if (statementBefore == null) {  // empty file
      return Result.Continue;
    }

    if (PsiTreeUtil.hasErrorElements(statementBefore)) {
      final Boolean autoWrapping = DataManager.getInstance().loadFromDataContext(dataContext, AutoHardWrapHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY);
      if (autoWrapping == null) {
        // code is already bad, don't mess it up even further
        return Result.Continue;
      }
      // if we're in middle of typing, it's expected that we will have error elements
    }

    if (inFromImportParentheses(statementBefore, offset)) {
      return Result.Continue;
    }

    PsiElement wrappableBefore = findWrappable(file, offset, true);
    PsiElement wrappableAfter = findWrappable(file, offset, false);
    if (!(wrappableBefore instanceof PsiComment)) {
      while (wrappableBefore != null) {
        PsiElement next = PsiTreeUtil.getParentOfType(wrappableBefore, IMPLICIT_WRAP_CLASSES);
        if (next == null) {
          break;
        }
        wrappableBefore = next;
      }
    }
    if (!(wrappableAfter instanceof PsiComment)) {
      while (wrappableAfter != null) {
        PsiElement next = PsiTreeUtil.getParentOfType(wrappableAfter, IMPLICIT_WRAP_CLASSES);
        if (next == null) {
          break;
        }
        wrappableAfter = next;
      }
    }
    if (wrappableBefore instanceof PsiComment || wrappableAfter instanceof PsiComment) {
      return Result.Continue;
    }
    if (wrappableAfter == null || wrappableBefore != wrappableAfter) {
      doc.insertString(offset, "\\");
      caretOffset.set(offset+1);
    }
    return Result.Continue;
  }

  @Nullable
  private static PsiElement findWrappable(PsiFile file, int offset, boolean before) {
    PsiElement wrappable = before
                                 ? findBeforeCaret(file, offset, IMPLICIT_WRAP_CLASSES)
                                 : findAfterCaret(file, offset, IMPLICIT_WRAP_CLASSES);
    if (wrappable == null) {
      PsiElement emptyTuple = before
                              ? findBeforeCaret(file, offset, PyTupleExpression.class)
                              : findAfterCaret(file, offset, PyTupleExpression.class);
      if (emptyTuple != null && emptyTuple.getNode().getFirstChildNode().getElementType() == PyTokenTypes.LPAR) {
        wrappable = emptyTuple;
      }
    }
    return wrappable;
  }

  @Nullable
  private static PsiElement findStatementBeforeCaret(PsiFile file, int offset) {
    return findBeforeCaret(file, offset, PyStatement.class);
  }

  @Nullable
  private static PsiElement findStatementAfterCaret(PsiFile file, int offset) {
    return findAfterCaret(file, offset, PyStatement.class);
  }

  @Nullable
  private static PsiElement findBeforeCaret(PsiFile file, int offset, Class<? extends PsiElement>... classes) {
    while(offset > 0) {
      offset--;
      final PsiElement element = file.findElementAt(offset);
      if (element != null && !(element instanceof PsiWhiteSpace)) {
        return getNonStrictParentOfType(element, classes);
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement findAfterCaret(PsiFile file, int offset, Class<? extends PsiElement>... classes) {
    while(offset < file.getTextLength()) {
      final PsiElement element = file.findElementAt(offset);
      if (element != null && !(element instanceof PsiWhiteSpace)) {
        return getNonStrictParentOfType(element, classes);
      }
      offset++;
    }
    return null;
  }

  @Nullable
  private static <T extends PsiElement> T getNonStrictParentOfType(@NotNull PsiElement element, @NotNull Class<? extends T>... classes) {
    PsiElement run = element;
    while (run != null) {
      for (Class<? extends T> aClass : classes) {
        if (aClass.isInstance(run)) return (T)run;
      }
      if (run instanceof PsiFile || run instanceof PyStatementList) break;
      run = run.getParent();
    }

    return null;
  }

  private static boolean inFromImportParentheses(PsiElement statement, int offset) {
    if (!(statement instanceof PyFromImportStatement)) {
      return false;
    }
    PyFromImportStatement fromImportStatement = (PyFromImportStatement)statement;
    PsiElement leftParen = fromImportStatement.getLeftParen();
    if (leftParen != null && offset > leftParen.getTextRange().getEndOffset()) {
      return true;
    }
    return false;
  }
}
