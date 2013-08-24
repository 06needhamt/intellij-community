package com.jetbrains.python.codeInsight.editorActions.moveUpDown;

import com.intellij.codeInsight.editorActions.moveUpDown.LineMover;
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : ktisha
 */
public class PyStatementMover extends LineMover {
  @Override
  public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    if (!(file instanceof PyFile)) return false;
    final int offset = editor.getCaretModel().getOffset();
    PsiElement elementToMove = PyUtil.findNonWhitespaceAtOffset(file, offset);
    if (elementToMove == null) return false;
    elementToMove = getCommentOrStatement(editor.getDocument(), elementToMove);
    info.toMove = new MyLineRange(elementToMove);
    info.toMove2 = getDestinationScope(file, editor, elementToMove, down);
    info.indentTarget = false;
    info.indentSource = false;
    return true;
  }

  @Nullable
  private static LineRange getDestinationScope(@NotNull final PsiFile file, @NotNull final Editor editor,
                                               @NotNull final PsiElement elementToMove, boolean down) {
    final Document document = file.getViewProvider().getDocument();

    if (document == null) return null;
    if (elementToMove instanceof PyPassStatement || elementToMove instanceof PyContinueStatement ||
        elementToMove instanceof PyBreakStatement) return null;

    final int offset = down ? elementToMove.getTextRange().getEndOffset() : elementToMove.getTextRange().getStartOffset();
    int lineNumber = down ? document.getLineNumber(offset) + 1 : document.getLineNumber(offset) - 1;
    if (moveOutsideFile(elementToMove, document, lineNumber)) return null;
    int lineEndOffset = document.getLineEndOffset(lineNumber);
    final int startOffset = document.getLineStartOffset(lineNumber);
    lineEndOffset = startOffset != lineEndOffset ? lineEndOffset - 1 : lineEndOffset;

    final PyStatementList statementList = getStatementList(elementToMove);

    final PsiElement destination = getDestinationElement(elementToMove, document, lineEndOffset, down);
    if (elementToMove instanceof PsiComment && destination instanceof  PsiComment) {
      return new LineRange(lineNumber, lineNumber + 1);
    }

    if (elementToMove instanceof PyClass || elementToMove instanceof PyFunction) {
      PyElement scope = statementList == null ? (PyElement)elementToMove.getContainingFile() : statementList;
      if (destination != null)
        return new ScopeRange(scope, destination, !down, true);
    }
    final String lineText = document.getText(TextRange.create(startOffset, lineEndOffset));
    final boolean isEmptyLine = StringUtil.isEmptyOrSpaces(lineText);
    if (isEmptyLine && moveToEmptyLine(elementToMove, down)) return new LineRange(lineNumber, lineNumber + 1);

    LineRange scopeRange = moveOut(elementToMove, editor, down);
    if (scopeRange != null) return scopeRange;
    scopeRange = moveInto(elementToMove, file, editor, down, lineEndOffset);
    if (scopeRange != null) return scopeRange;

    final PyElement scope = statementList == null ? (PyElement)elementToMove.getContainingFile() : statementList;
    return new ScopeRange(scope, destination, !down, true);
  }

  private static boolean moveOutsideFile(@NotNull final PsiElement elementToMove, @NotNull final Document document, int lineNumber) {
    if (lineNumber >= document.getLineCount() || lineNumber < 0) {
      final int elementOffset = elementToMove.getTextRange().getStartOffset();
      final int lineStartOffset = document.getLineStartOffset(document.getLineNumber(elementOffset));
      final int insertIndex = lineNumber < 0 ? 0 : document.getTextLength();
      if (elementOffset != lineStartOffset) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.insertString(insertIndex, "\n");
            PsiDocumentManager.getInstance(elementToMove.getProject()).commitAllDocuments();
          }
        });
      }
      else return true;
    }
    return false;
  }

  private static boolean moveToEmptyLine(@NotNull final PsiElement elementToMove, boolean down) {
    final PyStatementList statementList = getStatementList(elementToMove);
    if (statementList != null) {
      if (down) {
        final PsiElement child = statementList.getLastChild();
        if (elementToMove == child && PsiTreeUtil.getNextSiblingOfType(statementList.getParent(), PyStatementPart.class) != null
            || child != elementToMove) {
          return true;
        }
      }
      else {
        return true;
      }
    }
    return statementList == null;
  }

  private static PyStatementList getStatementList(@NotNull final PsiElement elementToMove) {
    return PsiTreeUtil.getParentOfType(elementToMove, PyStatementList.class, true,
                                                                PyStatementWithElse.class, PyLoopStatement.class,
                                                                PyFunction.class, PyClass.class);
  }

  @Nullable
  private static ScopeRange moveOut(@NotNull final PsiElement elementToMove, @NotNull final Editor editor, boolean down) {
    final PyStatementList statementList = getStatementList(elementToMove);
    if (statementList == null) return null;

    if ((!down || statementList.getLastChild() != elementToMove) && (down || statementList.getFirstChild() != elementToMove)) {
      return null;
    }
    boolean addBefore = !down;
    final PsiElement parent = statementList.getParent();
    final PyStatementPart sibling = down ? PsiTreeUtil.getNextSiblingOfType(parent, PyStatementPart.class)
                                         : PsiTreeUtil.getPrevSiblingOfType(parent, PyStatementPart.class);

    if (sibling != null) {
      final PyStatementList list = sibling.getStatementList();
      assert list != null;
      return new ScopeRange(list, down ? list.getFirstChild() : list.getLastChild(), !addBefore);
    }
    else {
      PsiElement scope = getScopeForComment(elementToMove, editor, parent, !down);
      PsiElement anchor = PsiTreeUtil.getParentOfType(statementList, PyStatement.class);
      return scope == null ? null : new ScopeRange(scope, anchor, addBefore);
    }
  }

  private static PsiElement getScopeForComment(@NotNull final PsiElement elementToMove, @NotNull final Editor editor,
                                               @Nullable PsiElement parent, boolean down) {
    PsiElement scope = PsiTreeUtil.getParentOfType(parent, PyStatementList.class, PyFile.class);
    final int offset = elementToMove.getTextOffset();
    PsiElement sibling = elementToMove;
    while (scope != null && elementToMove instanceof PsiComment) { // stupid workaround for PY-6408. Related to PSI structure
      final PsiElement prevSibling = down ? PsiTreeUtil.getNextSiblingOfType(sibling, PyStatement.class) :
                                            PsiTreeUtil.getPrevSiblingOfType(sibling, PyStatement.class);
      if (prevSibling == null) break;
      if (editor.offsetToLogicalPosition(prevSibling.getTextOffset()).column ==
          editor.offsetToLogicalPosition(offset).column) break;
      sibling = scope;
      scope = PsiTreeUtil.getParentOfType(scope, PyStatementList.class, PyFile.class);
    }
    return scope;
  }

  @Nullable
  private static LineRange moveInto(@NotNull final PsiElement elementToMove, @NotNull final PsiFile file,
                                    @NotNull final Editor editor, boolean down, int offset) {

    PsiElement rawElement = PyUtil.findNonWhitespaceAtOffset(file, offset);
    if (rawElement == null) return null;

    return down ? moveDownInto(editor.getDocument(), rawElement) : moveUpInto(elementToMove, editor, rawElement, false);
  }

  @Nullable
  private static LineRange moveUpInto(@NotNull final PsiElement elementToMove, @NotNull final Editor editor,
                                      @NotNull final PsiElement rawElement, boolean down) {
    final Document document = editor.getDocument();
    PsiElement element = getCommentOrStatement(document, rawElement);
    final PyStatementList statementList = getStatementList(elementToMove);
    final PsiElement scopeForComment = statementList == null ? null :
                                       getScopeForComment(elementToMove, editor, elementToMove, down);
    PyStatementList statementList2 = getStatementList(element);
    final int start1 = elementToMove.getTextOffset() - document.getLineStartOffset(document.getLineNumber(elementToMove.getTextOffset()));
    final int start2 = element.getTextOffset() - document.getLineStartOffset(document.getLineNumber(element.getTextOffset()));
    if (start1 != start2) {
      PyStatementList parent2 = PsiTreeUtil.getParentOfType(statementList2, PyStatementList.class);
      while (parent2 != scopeForComment && parent2 != null) {
        element = PsiTreeUtil.getParentOfType(statementList2, PyStatement.class);
        statementList2 = parent2;
        parent2 = PsiTreeUtil.getParentOfType(parent2, PyStatementList.class);
      }
    }

    if (statementList2 != null && scopeForComment != statementList2 &&
        (statementList2.getLastChild() == element || statementList2.getLastChild() == elementToMove)) {
      return new ScopeRange(statementList2, element, false);
    }
    return null;
  }

  @Nullable
  private static LineRange moveDownInto(@NotNull final Document document, @NotNull final PsiElement rawElement) {
    PsiElement element = getCommentOrStatement(document, rawElement);
    PyStatementList statementList2 = getStatementList(element);
    if (statementList2 != null) {                     // move to one-line conditional/loop statement
      final int number = document.getLineNumber(element.getTextOffset());
      final int number2 = document.getLineNumber(statementList2.getParent().getTextOffset());
      if (number == number2) {
        return new ScopeRange(statementList2, statementList2.getFirstChild(), true);
      }
    }
    final PyStatementPart statementPart = PsiTreeUtil.getParentOfType(rawElement, PyStatementPart.class, true, PyStatement.class,
                                                                      PyStatementList.class);
    final PyFunction functionDefinition = PsiTreeUtil.getParentOfType(rawElement, PyFunction.class, true, PyStatement.class,
                                                                      PyStatementList.class);
    final PyClass classDefinition = PsiTreeUtil.getParentOfType(rawElement, PyClass.class, true, PyStatement.class,
                                                                PyStatementList.class);
    PyStatementList list = null;
    if (statementPart != null) list = statementPart.getStatementList();
    else if (functionDefinition != null) list = functionDefinition.getStatementList();
    else if (classDefinition != null) list = classDefinition.getStatementList();
    if (list != null) {
      return new ScopeRange(list, list.getFirstChild(), true);
    }
    return null;
  }

  private static PsiElement getDestinationElement(@NotNull final PsiElement elementToMove, @NotNull final Document document,
                                                  int lineEndOffset, boolean down) {
    PsiElement destination = elementToMove.getContainingFile().findElementAt(lineEndOffset);
    if (destination == null) return null;
    PsiElement sibling = down ? PsiTreeUtil.getNextSiblingOfType(elementToMove, PyStatement.class) :
                  PsiTreeUtil.getPrevSiblingOfType(elementToMove, PyStatement.class);
    if (elementToMove instanceof PyClass) {
      destination = sibling;
    }
    else if (elementToMove instanceof PyFunction) {
      if (!(sibling instanceof PyClass))
        destination = sibling;
      else destination = null;
    }
    else {
      destination = getCommentOrStatement(document, destination);
    }
    return destination;
  }

  @NotNull
  private static PsiElement getCommentOrStatement(@NotNull final Document document, @NotNull PsiElement destination) {
    final PsiElement statement = PsiTreeUtil.getParentOfType(destination, PyStatement.class, false);
    if (statement == null) return destination;
    if (destination instanceof PsiComment) {
      if (document.getLineNumber(destination.getTextOffset()) == document.getLineNumber(statement.getTextOffset()))
        destination = statement;
    }
    else
      destination = statement;
    return destination;
  }

  @Override
  public void beforeMove(@NotNull final Editor editor, @NotNull final MoveInfo info, final boolean down) {
    final LineRange toMove = info.toMove;
    final LineRange toMove2 = info.toMove2;

    if (toMove instanceof MyLineRange && toMove2 instanceof ScopeRange) {

      PostprocessReformattingAspect.getInstance(editor.getProject()).disablePostprocessFormattingInside(new Runnable() {
        @Override
        public void run() {
          final PsiElement elementToMove = ((MyLineRange)toMove).myElement;
          final SelectionModel selectionModel = editor.getSelectionModel();
          final CaretModel caretModel = editor.getCaretModel();
          final int shift = caretModel.getOffset() - elementToMove.getTextOffset();
          final boolean hasSelection = selectionModel.hasSelection();
          final int selectionStart = selectionModel.getSelectionStart();
          final int selectionEnd = selectionModel.getSelectionEnd();
          final int selectionShift = selectionStart - elementToMove.getTextOffset();

          int offset;
          if (((ScopeRange)toMove2).isTheSameLevel()) {
            offset = moveTheSameLevel((ScopeRange)toMove2, (MyLineRange)toMove);
          }
          else {
            offset = moveInOut(((MyLineRange)toMove).myElement, editor, info);
          }

          caretModel.moveToOffset(offset + shift);
          info.toMove2 = info.toMove;   //do not move further
          if (hasSelection) {
            int newSelectionStart = offset + selectionShift;
            int newSelectionEnd = newSelectionStart + selectionEnd - selectionStart;
            selectionModel.setSelection(newSelectionStart, newSelectionEnd);
          }
        }
      });
    }

  }

  private static int moveTheSameLevel(@NotNull final ScopeRange toMove2, @NotNull final MyLineRange toMove) {
    final PsiElement anchor = toMove2.getAnchor();
    final PsiElement elementToMove = toMove.myElement;

    final PsiElement anchorCopy = anchor.copy();
    final PsiElement addedElement = anchor.replace(elementToMove);
    elementToMove.replace(anchorCopy);

    return addedElement.getTextOffset();
  }

  private static int moveInOut(@NotNull final PsiElement elementToMove, @NotNull final Editor editor, @NotNull final MoveInfo info) {
    boolean removePass = false;
    final ScopeRange toMove2 = (ScopeRange)info.toMove2;
    final PsiElement scope = toMove2.getScope();
    final PsiElement anchor = toMove2.getAnchor();
    final Project project = scope.getProject();

    if (scope instanceof PyStatementList && !(elementToMove instanceof PsiComment)) {
      final PyStatement[] statements = ((PyStatementList)scope).getStatements();
      if (statements.length == 1 && statements[0] == anchor && statements[0] instanceof PyPassStatement) {
        removePass = true;
      }
    }
    final PsiElement addedElement = toMove2.isAddBefore() ? scope.addBefore(elementToMove, anchor) : scope.addAfter(elementToMove, anchor);
    addPassStatement(elementToMove, project);


    elementToMove.delete();

    final int addedElementLine = editor.getDocument().getLineNumber(addedElement.getTextOffset());
    final PsiFile file = scope.getContainingFile();

    adjustLineIndents(editor, scope, project, addedElement);

    if (removePass) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          final Document document = editor.getDocument();
          final int lineNumber = document.getLineNumber(anchor.getTextOffset());
          final int endOffset = document.getLineCount() <= lineNumber + 1 ? document.getLineEndOffset(lineNumber)
                                                                          : document.getLineStartOffset(lineNumber + 1);
          document.deleteString(document.getLineStartOffset(lineNumber), endOffset);
          PsiDocumentManager.getInstance(elementToMove.getProject()).commitAllDocuments();
        }
      });
    }

    int offset = addedElement.getTextRange().getStartOffset();
    if (addedElement instanceof PsiComment && offset == 0) {  // PsiComment gets broken after adjust indent
      final PsiElement psiElement = PyUtil.findNonWhitespaceAtOffset(file, editor.getDocument().getLineEndOffset(addedElementLine) - 1);
      if (psiElement != null) {
        offset = psiElement.getTextOffset();
      }
    }
    return offset;
  }

  private static void adjustLineIndents(@NotNull final Editor editor, @NotNull final PsiElement scope, @NotNull final Project project,
                                        @NotNull final PsiElement addedElement) {
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final Document document = editor.getDocument();

    if (!(scope instanceof PsiFile)) {
      int line1 = editor.offsetToLogicalPosition(scope.getTextRange().getStartOffset()).line;
      int line2 = editor.offsetToLogicalPosition(scope.getTextRange().getEndOffset()).line;
      codeStyleManager.adjustLineIndent(scope.getContainingFile(),
                                        new TextRange(document.getLineStartOffset(line1), document.getLineStartOffset(line2)));
    }
    else {
      int line1 = editor.offsetToLogicalPosition(addedElement.getTextRange().getStartOffset()).line;
      int line2 = editor.offsetToLogicalPosition(addedElement.getTextRange().getEndOffset()).line;
      codeStyleManager.adjustLineIndent(scope.getContainingFile(),
                                        new TextRange(document.getLineStartOffset(line1), document.getLineStartOffset(line2)));
    }
  }

  private static void addPassStatement(@NotNull final PsiElement elementToMove, @NotNull final Project project) {
    final PyStatementList initialScope = getStatementList(elementToMove);
    if (initialScope != null && !(elementToMove instanceof PsiComment)) {
      if (initialScope.getStatements().length == 1) {
        final PyPassStatement passStatement = PyElementGenerator.getInstance(project).createPassStatement();
        initialScope.addAfter(passStatement, initialScope.getStatements()[initialScope.getStatements().length - 1]);
      }
    }
  }

  // use to keep element
  static class MyLineRange extends LineRange {
    public PsiElement myElement;
    public MyLineRange(@NotNull PsiElement element) {
      super(element);
      myElement = element;
    }
  }

  // Use when element scope changed
  static class ScopeRange extends LineRange {
    private PsiElement myScope;
    private PsiElement myAnchor;
    private boolean addBefore;
    private boolean theSameLevel;

    public ScopeRange(@NotNull PsiElement scope, @Nullable PsiElement anchor, boolean before) {
      super(scope);
      myScope = scope;
      myAnchor = anchor;
      addBefore = before;
    }

    public ScopeRange(PyElement scope, PsiElement anchor, boolean before, boolean b) {
      super(scope);
      myScope = scope;
      myAnchor = anchor;
      addBefore = before;
      theSameLevel = b;
    }

    public PsiElement getAnchor() {
      return myAnchor;
    }

    public PsiElement getScope() {
      return myScope;
    }

    public boolean isAddBefore() {
      return addBefore;
    }

    public boolean isTheSameLevel() {
      return theSameLevel;
    }
  }
}
