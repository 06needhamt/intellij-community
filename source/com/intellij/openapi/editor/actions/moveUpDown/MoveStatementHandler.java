/**
 * @author cdr
 */
package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

class MoveStatementHandler extends EditorWriteActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.MoveStatementHandler");

  private final boolean isDown;
  private final Mover[] myMovers;

  public MoveStatementHandler(boolean down) {
    isDown = down;

    myMovers = new Mover[]{ new StatementMover(), new DeclarationMover(), new LineMover()};
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    final Project project = editor.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = editor.getDocument();
    final PsiFile file = documentManager.getPsiFile(document);

    final Mover mover = getMover(editor, file);
    final InsertionInfo insertionInfo = mover.getInsertionInfo(editor, file, isDown);
    insertionInfo.prepareToMove(isDown);
    final LineRange lineRange = insertionInfo.whatToMove;
    final int startLine = lineRange.startLine;
    final int endLine = lineRange.endLine;

    final int insertOffset = insertionInfo.insertOffset;

    final int start = editor.logicalPositionToOffset(new LogicalPosition(startLine, 0));
    final int end = editor.logicalPositionToOffset(new LogicalPosition(endLine+1, 0));
    final String toInsert = document.getCharsSequence().subSequence(start, end).toString();
    final int insStart = isDown ? insertOffset - toInsert.length() : insertOffset;
    final int insEnd = insStart + toInsert.length();

    final CaretModel caretModel = editor.getCaretModel();
    final int caretRelativePos = caretModel.getOffset() - start;
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    final boolean hasSelection = selectionModel.hasSelection();

    // to prevent flicker
    caretModel.moveToOffset(0);

    document.deleteString(start, end);
    document.insertString(insStart, toInsert);
    documentManager.commitDocument(document);

    if (hasSelection) {
      restoreSelection(editor, selectionStart, selectionEnd, start, insStart);
    }

    try {
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      final int line1 = editor.offsetToLogicalPosition(insStart).line;
      final int line2 = editor.offsetToLogicalPosition(insEnd).line;
      caretModel.moveToOffset(insStart + caretRelativePos);

      for (int line = line1; line <= line2; line++) {
        int lineStart = document.getLineStartOffset(line);
        codeStyleManager.adjustLineIndent(file, lineStart);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private static void restoreSelection(final Editor editor, final int selectionStart, final int selectionEnd, final int moveOffset, int insOffset) {
    final int selectionRelativeOffset = selectionStart - moveOffset;
    int newSelectionStart = insOffset + selectionRelativeOffset;
    int newSelectionEnd = newSelectionStart + selectionEnd - selectionStart;
    editor.getSelectionModel().setSelection(newSelectionStart, newSelectionEnd);
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    if (editor.isOneLineMode()) {
      return false;
    }
    final Project project = editor.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = editor.getDocument();
    final PsiFile file = documentManager.getPsiFile(document);
    final Mover mover = getMover(editor, file);
    if (mover == null) return false;
    final InsertionInfo insertionInfo = mover.getInsertionInfo(editor, file, isDown);
    if (insertionInfo == null) return false;
    final int maxLine = editor.offsetToLogicalPosition(editor.getDocument().getTextLength()).line;
    final LineRange range = insertionInfo.whatToMove;
    if (range.startLine <= 1 && !isDown) return false;
    if (range.endLine >= maxLine - 1 && isDown) return false;

    return true;
  }

  private Mover getMover(final Editor editor, final PsiFile file) {
    for (int i = 0; i < myMovers.length; i++) {
      final Mover mover = myMovers[i];
      final InsertionInfo range = mover.getInsertionInfo(editor, file, isDown);
      if (range != null) return mover;
    }
    return null;
  }

}

