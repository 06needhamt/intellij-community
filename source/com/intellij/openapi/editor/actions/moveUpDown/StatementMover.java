package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.util.IncorrectOperationException;

class StatementMover extends LineMover {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.StatementMover");

  private static class StatementInsertionInfo extends InsertionInfo {
    PsiElement statementToSurroundWithCodeBlock;

    public StatementInsertionInfo(final LineRange whatToMove, final int insertOffset, final PsiElement statementToSurroundWithCodeBlock) {
      super(whatToMove, insertOffset);
      this.statementToSurroundWithCodeBlock = statementToSurroundWithCodeBlock;
    }

    public void prepareToMove(boolean isDown) {
      super.prepareToMove(isDown);
      if (statementToSurroundWithCodeBlock != null) {
        try {
          final Document document = PsiDocumentManager.getInstance(statementToSurroundWithCodeBlock.getProject()).getDocument(statementToSurroundWithCodeBlock.getContainingFile());
          final int startOffset = document.getLineStartOffset(whatToMove.startLine);
          final int endOffset = document.getLineEndOffset(whatToMove.endLine);
          final RangeMarker lineRangeMarker = document.createRangeMarker(startOffset, endOffset);

          final PsiElementFactory factory = statementToSurroundWithCodeBlock.getManager().getElementFactory();
          PsiCodeBlock codeBlock = factory.createCodeBlock();
          codeBlock.add(statementToSurroundWithCodeBlock);
          final PsiBlockStatement blockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", statementToSurroundWithCodeBlock);
          blockStatement.getCodeBlock().replace(codeBlock);
          final PsiBlockStatement newStatement = (PsiBlockStatement)statementToSurroundWithCodeBlock.replace(blockStatement);

          whatToMove = new LineRange(document.getLineNumber(lineRangeMarker.getStartOffset()),
                                     document.getLineNumber(lineRangeMarker.getEndOffset()));
          insertOffset = isDown ? newStatement.getCodeBlock().getFirstBodyElement().getTextRange().getEndOffset() :
                         newStatement.getCodeBlock().getRBrace().getTextRange().getStartOffset();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  public InsertionInfo getInsertionInfo(Editor editor, PsiFile file, boolean isDown) {
    if (!(file instanceof PsiJavaFile)) return null;
    final InsertionInfo lineInsertionInfo = super.getInsertionInfo(editor, file, isDown);
    if (lineInsertionInfo == null) return null;
    LineRange range = lineInsertionInfo.whatToMove;

    range = expandLineRangeToCoverPsiElements(range, editor, file);
    if (range == null) return null;
    final int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    final int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine+1, 0));
    final PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    if (statements == null || statements.length == 0) return null;
    range.firstElement = statements[0];
    range.lastElement = statements[statements.length-1];
    if (!checkMovingInsideOutside(file, editor, range, isDown)) return InsertionInfo.ILLEGAL_INFO;
    return calcInsertOffset(editor, file, range, isDown);
  }

  private static StatementInsertionInfo calcInsertOffset(final Editor editor, PsiFile file, LineRange range, final boolean isDown) {
    int nearLine = isDown ? range.endLine + 2 : range.startLine - 1;
    int line = nearLine;

    while (true) {
      final int offset = editor.logicalPositionToOffset(new LogicalPosition(line, 0));
      PsiElement element = firstNonWhiteElement(offset, file, true);
      while (element != null && element != file) {
        if (!element.getTextRange().contains(offset)) {
          PsiElement elementToSurround = null;
          boolean found = false;
          if ((element instanceof PsiStatement || element instanceof PsiComment) && statementCanBePlacedAlong(element)) {
            if (!(element.getParent() instanceof PsiCodeBlock)) {
              elementToSurround = element;
            }
            found = true;
          }
          else if (element instanceof PsiJavaToken
              && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE
              && element.getParent() instanceof PsiCodeBlock) {
            // before code block closing brace
            found = true;
          }
          else if (element instanceof PsiMember) {
            found = true;
          }
          if (found) {
            return new StatementInsertionInfo(range, offset, elementToSurround);
          }
        }
        element = element.getParent();
      }
      line += isDown ? 1 : -1;
      if (line == 0 || line >= editor.getDocument().getLineCount()) {
        return new StatementInsertionInfo(range, editor.logicalPositionToOffset(new LogicalPosition(nearLine, 0)), null);
      }
    }
  }

  private static boolean statementCanBePlacedAlong(final PsiElement element) {
    if (element instanceof PsiBlockStatement) return false;
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiCodeBlock) return true;
    if (parent instanceof PsiIfStatement &&
    (element == ((PsiIfStatement)parent).getThenBranch() || element == ((PsiIfStatement)parent).getElseBranch())) {
      return true;
    }
    if (parent instanceof PsiWhileStatement && element == ((PsiWhileStatement)parent).getBody()) {
      return true;
    }
    if (parent instanceof PsiDoWhileStatement && element == ((PsiDoWhileStatement)parent).getBody()) {
      return true;
    }
    return false;
  }

  private static boolean checkMovingInsideOutside(final PsiFile file, final Editor editor, final LineRange result, final boolean isDown) {
    final int offset = editor.getCaretModel().getOffset();
    PsiElement elementAt = file.findElementAt(offset);
    if (elementAt == null) return false;

    final Class[] classes = new Class[]{PsiMethod.class, PsiClassInitializer.class, PsiClass.class, PsiComment.class,};
    final PsiElement guard = PsiTreeUtil.getParentOfType(elementAt, classes);
    // cannot move in/outside method/class/initializer/comment
    final StatementInsertionInfo insertionInfo = calcInsertOffset(editor, file, result, isDown);
    if (insertionInfo == null) return false;
    final int insertOffset = insertionInfo.insertOffset;
    elementAt = file.findElementAt(insertOffset);
    final PsiElement newGuard = PsiTreeUtil.getParentOfType(elementAt, classes);
    if (newGuard == guard && isInside(insertOffset, newGuard) == isInside(offset, guard)) return true;

    // moving in/out nested class is OK
    if (guard instanceof PsiClass && guard.getParent() instanceof PsiClass) return true;
    if (newGuard instanceof PsiClass && newGuard.getParent() instanceof PsiClass) return true;
    return false;
  }

  private static boolean isInside(final int offset, final PsiElement guard) {
    if (guard == null) return false;
    TextRange inside = guard instanceof PsiMethod ? ((PsiMethod)guard).getBody().getTextRange() : guard instanceof PsiClassInitializer
      ? ((PsiClassInitializer)guard).getBody().getTextRange()
      : guard instanceof PsiClass
      ? new TextRange(((PsiClass)guard).getLBrace().getTextOffset(), ((PsiClass)guard).getRBrace().getTextOffset())
      : guard.getTextRange();
    return inside != null && inside.contains(offset);
  }

  private static LineRange expandLineRangeToCoverPsiElements(final LineRange range, Editor editor, final PsiFile file) {
    Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, range);
    if (psiRange == null) return null;
    final PsiElement parent = PsiTreeUtil.findCommonParent(psiRange.getFirst(), psiRange.getSecond());
    Pair<PsiElement, PsiElement> elementRange = getElementRange(parent, psiRange.getFirst(), psiRange.getSecond());
    return new LineRange(editor.offsetToLogicalPosition(elementRange.getFirst().getTextOffset()).line,
                     editor.offsetToLogicalPosition(elementRange.getSecond().getTextRange().getEndOffset()).line);
  }
}
