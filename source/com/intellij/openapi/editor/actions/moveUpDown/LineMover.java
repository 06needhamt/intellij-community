package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;

class LineMover implements Mover {
  public LineRange getRangeToMove(Editor editor, PsiFile file, boolean isDown) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int startLine;
    final int endLine;
    LineRange result;
    if (selectionModel.hasSelection()) {
      startLine = editor.offsetToLogicalPosition(selectionModel.getSelectionStart()).line;
      final LogicalPosition endPos = editor.offsetToLogicalPosition(selectionModel.getSelectionEnd());
      endLine = endPos.column == 0 ? endPos.line - 1 : endPos.line;
      result = new LineRange(startLine, endLine);
    }
    else {
      startLine = editor.getCaretModel().getLogicalPosition().line;
      endLine = startLine;
      result = new LineRange(startLine, endLine);
    }
    return result;
  }

  public int getOffsetToMoveTo(Editor editor, PsiFile file, LineRange range, boolean isDown) {
    final int maxLine = editor.offsetToLogicalPosition(editor.getDocument().getTextLength()).line;
    if (range.startLine <= 1 && !isDown) return -1;
    if (range.endLine >= maxLine - 1 && isDown) return -1;

    int nearLine = isDown ? range.endLine + 2 : range.startLine - 1;
    return editor.logicalPositionToOffset(new LogicalPosition(nearLine, 0));
  }

  protected static Pair<PsiElement, PsiElement> getElementRange(Editor editor, PsiFile file, final LineRange range) {
    final int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    PsiElement startingElement = firstNonWhiteElement(startOffset, file, true);
    if (startingElement == null) return null;
    final int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine+1, 0)) -1;

    PsiElement endingElement = firstNonWhiteElement(endOffset, file, false);
    if (endingElement == null) return null;
    return Pair.create(startingElement, endingElement);
  }

  static PsiElement firstNonWhiteElement(int offset, PsiFile file, final boolean lookRight) {
    PsiElement element = file.findElementAt(offset);
    return firstNonWhiteElement(element, lookRight);
  }

  static PsiElement firstNonWhiteElement(PsiElement element, final boolean lookRight) {
    if (element instanceof PsiWhiteSpace) {
      element = lookRight ? element.getNextSibling() : element.getPrevSibling();
    }
    return element;
  }

  protected static Pair<PsiElement, PsiElement> getElementRange(final PsiElement parent,
                                                              PsiElement element1,
                                                              PsiElement element2) {
    if (PsiTreeUtil.isAncestor(element1, element2, false) || PsiTreeUtil.isAncestor(element2, element1, false)) {
      return Pair.create(parent, parent);
    }
    // find nearset children that are parents of elements
    while (element1.getParent() != parent) {
      element1 = element1.getParent();
    }
    while (element2.getParent() != parent) {
      element2 = element2.getParent();
    }
    return Pair.create(element1, element2);
  }
}
