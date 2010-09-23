package com.jetbrains.python.editor;

import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
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
    PyParameterList.class};

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

    if (statementBefore != null && PsiTreeUtil.hasErrorElements(statementBefore)) {
      final Boolean autoWrapping = DataManager.getInstance().loadFromDataContext(dataContext, TypedHandler.AUTO_WRAP_LINE_IN_PROGRESS_KEY);
      if (autoWrapping == null) {
        // code is already bad, don't mess it up even further
        return Result.Continue;
      }
      // if we're in middle of typing, it's expected that we will have error elements
    }

    PsiElement wrappableBefore = findBeforeCaret(file, offset, IMPLICIT_WRAP_CLASSES);
    if (isCommentOnOtherLine(doc, offset, wrappableBefore)) {
      return Result.Continue;
    }
    PsiElement wrappableAfter = findAfterCaret(file, offset, IMPLICIT_WRAP_CLASSES);
    if (isCommentOnOtherLine(doc, offset, wrappableAfter)) {
      return Result.Continue;
    }
    while (wrappableBefore != null) {
      PsiElement next = PsiTreeUtil.getParentOfType(wrappableBefore, IMPLICIT_WRAP_CLASSES);
      if (next == null) {
        break;
      }
      wrappableBefore = next;
    }
    while (wrappableAfter != null) {
      PsiElement next = PsiTreeUtil.getParentOfType(wrappableAfter, IMPLICIT_WRAP_CLASSES);
      if (next == null) {
        break;
      }
      wrappableAfter = next;
    }
    if (wrappableAfter == null || wrappableBefore != wrappableAfter) {
      doc.insertString(offset, "\\");
      caretOffset.set(offset+1);
    }
    return Result.Continue;
  }

  private static boolean isCommentOnOtherLine(Document doc, int offset, PsiElement other) {
    if (other instanceof PsiComment) {
      int otherLine = doc.getLineNumber(other.getTextRange().getStartOffset());
      int thisLine = doc.getLineNumber(offset);
      if (otherLine != thisLine) {
        return true;
      }
    }
    return false;
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
        return PsiTreeUtil.getNonStrictParentOfType(element, classes);
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement findAfterCaret(PsiFile file, int offset, Class<? extends PsiElement>... classes) {
    while(offset < file.getTextLength()) {
      final PsiElement element = file.findElementAt(offset);
      if (element != null && !(element instanceof PsiWhiteSpace)) {
        return PsiTreeUtil.getNonStrictParentOfType(element, classes);
      }
      offset++;
    }
    return null;
  }
}
