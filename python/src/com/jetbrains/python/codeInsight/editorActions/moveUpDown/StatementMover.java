package com.jetbrains.python.codeInsight.editorActions.moveUpDown;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.editorActions.moveUpDown.LineMover;
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange;
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class StatementMover extends LineMover {
  private @Nullable PyStatementList myStatementListToAddPass;
  private @Nullable PyStatementList myStatementListToAddPassAfter;  
  private @Nullable PyStatement myStatementToIncreaseIndent;
  private @Nullable PyStatement myStatementToDecreaseIndent;
  private @Nullable PyStatement myStatementToAddLinebreak;
  private @Nullable PyStatement myStatementToMove;
  private @Nullable PyStatementPart myStatementPartToRemovePass;

  private void init(@NotNull final Editor editor, @NotNull final MoveInfo info, final boolean down) {
    LineRange range = StatementUpDownMover.getLineRangeFromSelection(editor);
    int nearLine = down ? range.endLine : range.startLine - 1;
    info.toMove = range;
    info.toMove2 = new LineRange(nearLine, nearLine + 1);
  }

  @Override
  public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    if (!(file instanceof PyFile)) return false;
    init(editor, info, down);
    // Do not move in case of selection
    if (editor.getSelectionModel().hasSelection()){
      return false;
    }
    Document document = editor.getDocument();
    String lineToMove = document.getText(new TextRange(getLineStartSafeOffset(document, info.toMove.startLine), getLineStartSafeOffset(document, info.toMove.endLine)));
    if (StringUtil.isEmptyOrSpaces(lineToMove)) {
      info.toMove2 = info.toMove;
      return true;
    }

    //reset
    myStatementListToAddPass = null;
    myStatementListToAddPassAfter = null;
    myStatementToDecreaseIndent = null;
    myStatementToIncreaseIndent = null;
    myStatementToAddLinebreak = null;
    myStatementPartToRemovePass = null;

    //find statement
    myStatementToMove = findStatement(editor, file, info);

    // check if we want to move statement
    if (myStatementToMove == null) return false;
    //do not move pass statement
    if (myStatementToMove instanceof PyPassStatement || myStatementToMove instanceof PyBreakStatement
        || myStatementToMove instanceof PyContinueStatement) {
      info.toMove2 = info.toMove;
      return true;
    }
    // do not move last statement down and first up
    if (isFirstOrLastStatement(down)) {
      info.toMove2 = info.toMove;
      return true;
    }

    expandLineRangeToStatement(info, editor, down, file);

    //is move from one part of compound statement to another
    boolean theSameLevel = isTheSameIndentLevel(info, editor, file, down);

    //check we move statement into compound or out of compound
    if (isMoveOut(info, editor, file, down)) {
      myStatementToDecreaseIndent = myStatementToMove;
      if (down)
        info.toMove2 = new LineRange(myStatementToMove);
    }
    else {
      LineRange range = StatementUpDownMover.getLineRangeFromSelection(editor);
      final int maxLine = editor.getDocument().getLineCount();
      if (range.startLine == 0 && !down) return false;
      if (range.endLine >= maxLine && down) return false;
    }
    if (isMoveToCompound(info, editor, file, down)) {
      if (isMoveToEmptyLine(editor, info)) return true;
      myStatementToIncreaseIndent = myStatementToMove;
      if (!down)
        info.toMove2 = new LineRange(myStatementToMove);
    }

    //find statement to add pass if we removed last statement from PyStatementPart
    PyStatementPart statementPart = PsiTreeUtil.getParentOfType(myStatementToMove, PyStatementPart.class, false);
    if (statementPart != null) {
      PyStatementList statementList = statementPart.getStatementList();
      if (statementList != null && statementList.getStatements().length == 1) {
        if (theSameLevel) {
          myStatementListToAddPassAfter = statementList;
        }
        else
          myStatementListToAddPass = statementList;
      }
    }
    return true;
  }

  private boolean isFirstOrLastStatement(boolean down) {
    PyFunction function = PsiTreeUtil.getParentOfType(myStatementToMove, PyFunction.class, true);
    if (function != null) {
      PyStatementList statementList = function.getStatementList();
      if (statementList != null && statementList.getStatements().length > 0) {
        if ((statementList.getStatements()[0] == myStatementToMove && !down)
            || (statementList.getStatements()[statementList.getStatements().length-1] == myStatementToMove &&  down)) {
          return true;
        }
      }
    }
    PyClass cl = PsiTreeUtil.getParentOfType(myStatementToMove, PyClass.class, true);
    if (cl != null) {
      PyStatementList statementList = cl.getStatementList();
      if (statementList.getStatements().length > 0) {
        if ((statementList.getStatements()[0] == myStatementToMove && !down)
            || (statementList.getStatements()[statementList.getStatements().length-1] == myStatementToMove &&  down)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private PyStatement findStatement(Editor editor, PsiFile file, MoveInfo info) {
    Document doc = editor.getDocument();
    int offset1 = getLineStartSafeOffset(doc, info.toMove.startLine);
    PsiElement element1 = file.findElementAt(offset1);
    if (element1 != null) {
      if (element1 instanceof PsiWhiteSpace) {
        element1 = PyPsiUtils.getSignificantToTheRight(element1, true);
      }
      return PsiTreeUtil.getParentOfType(element1, PyStatement.class, false);
    }
    return null;
  }

  private void expandLineRangeToStatement(MoveInfo info, Editor editor, boolean down, PsiFile file) {
    Document doc = editor.getDocument();

    TextRange textRange = myStatementToMove.getTextRange();
    LineRange range = new LineRange(doc.getLineNumber(textRange.getStartOffset()),
                                doc.getLineNumber(textRange.getEndOffset())+1);
    int nearLine = down ? range.endLine : range.startLine - 1;
    if (nearLine < 0) nearLine = 0;
    info.toMove = range;
    info.toMove2 = new LineRange(nearLine, nearLine + 1);

    if (isMoveToEmptyLine(editor, info)) return;
    // if try to move to the function or to the class

    int offset2 = getLineStartSafeOffset(doc, info.toMove2.startLine);
    PsiElement element2 = file.findElementAt(offset2);
    if (element2 != null) {
      if (element2 instanceof PsiWhiteSpace) {
        PsiElement tmp = PyPsiUtils.getSignificantToTheRight(element2, true);
        if (tmp != null &&
                  editor.offsetToLogicalPosition(tmp.getTextRange().getStartOffset()).line
                          == info.toMove2.startLine) {
          element2 = tmp;
        }
      }
      PyElement parent2 = PsiTreeUtil.getParentOfType(element2, PyFunction.class);
      PyElement parent1 = PsiTreeUtil.getParentOfType(myStatementToMove, PyFunction.class);
      if (parent2 != null && parent2 != parent1) {
        TextRange textRange2 = parent2.getTextRange();
        info.toMove2 = new LineRange(doc.getLineNumber(textRange2.getStartOffset()),
                                              doc.getLineNumber(textRange2.getEndOffset())+1);
      }
      parent2 = PsiTreeUtil.getParentOfType(element2, PyClass.class);
      parent1 = PsiTreeUtil.getParentOfType(myStatementToMove, PyClass.class);
      if (parent2 != null && parent2 != parent1) {
        TextRange textRange2 = parent2.getTextRange();
        info.toMove2 = new LineRange(doc.getLineNumber(textRange2.getStartOffset()),
                                              doc.getLineNumber(textRange2.getEndOffset())+1);
      }
    }
  }
  
  private boolean isMoveToEmptyLine(Editor editor, MoveInfo info) {
    Document document = editor.getDocument();
    if (document.getLineCount() > info.toMove2.endLine) {
      String lineToMoveTo = document.getText(new TextRange(getLineStartSafeOffset(document, info.toMove2.startLine), getLineStartSafeOffset(document, info.toMove2.endLine)));
      if (StringUtil.isEmptyOrSpaces(lineToMoveTo)) return true;
    }
    return false;
  }

  /**
   * main indent logic
   * @return first is the element which we move
   *         second is the element we move to
   */
  private Pair<PyElement, PyElement> getStatementParts(MoveInfo info, Editor editor, PsiFile file, boolean down) {
    PsiElement element1 = myStatementToMove;
    PyElement statementPart1 = PsiTreeUtil.getParentOfType(element1, PyStatementPart.class, PyWithStatement.class);
    int offset2 = getLineStartSafeOffset(editor.getDocument(), info.toMove2.startLine);
    PsiElement element2 = file.findElementAt(offset2-1);
    if (element2 instanceof PsiWhiteSpace) {
      if (down) {
        PsiElement tmp = PyPsiUtils.getSignificantToTheRight(element2, true);
        if (tmp != null &&
            editor.offsetToLogicalPosition(tmp.getTextRange().getStartOffset()).line == info.toMove2.startLine)
          element2 = tmp;

      } else {
        PsiElement tmp = PyPsiUtils.getSignificantToTheRight(element2, true);
        if (tmp != null) {
          int start = editor.offsetToLogicalPosition(tmp.getParent().getTextRange().getStartOffset()).line;
          int end = editor.offsetToLogicalPosition(tmp.getParent().getTextRange().getEndOffset()).line;
          if (start == info.toMove2.startLine && (start == end || tmp.getParent() instanceof PyClass || tmp.getParent() instanceof PyFunction))
            element2 = tmp;
          else
            element2 = PyPsiUtils.getSignificantToTheLeft(element2, true);
        }
        else {
          element2 = PyPsiUtils.getSignificantToTheLeft(element2, true);
        }
      }
    }
    PyElement statementPart2 = PsiTreeUtil.getParentOfType(element2, PyStatementPart.class, PyWithStatement.class);

    //in case we move very last line outside if statement
    if (statementPart2 instanceof PyStatementPart) {
      PyStatementList stList = ((PyStatementPart)statementPart2).getStatementList();
      if (stList != null && stList.getStatements().length > 0) {
        if (down && stList.getStatements()[stList.getStatements().length-1] == element2) {
          PyStatementPart parent = PsiTreeUtil.getParentOfType(statementPart2, PyStatementPart.class);
          if (parent != null) {
            stList = parent.getStatementList();
            if (stList != null && stList.getStatements().length > 0) {
              if (stList.getStatements()[stList.getStatements().length-1] == statementPart2) {
                statementPart2 = parent;
              }
            }
          }
        }
      }
    }

    return new Pair<PyElement, PyElement>(statementPart1, statementPart2);
  }

  private boolean isMoveToCompound(MoveInfo info, Editor editor, PsiFile file, boolean down) {
    Pair<PyElement, PyElement> statementParts = getStatementParts(info, editor, file, down);
    PyElement statementPart1 = statementParts.first;
    PyElement statementPart2 = statementParts.second;
    if (statementPart2 != null) {
      if (statementPart2 instanceof PyStatementPart)
        prepareToStatement((PyStatementPart)statementPart2, editor.getDocument());
      if (statementPart1 == null) return true;
      if (statementPart1.getParent() != statementPart2.getParent()) {
        PsiElement commonParent = PsiTreeUtil.findCommonParent(statementPart1, statementPart2);
        if (PsiTreeUtil.isAncestor(statementPart2, statementPart1, false)) return false;
        if ((commonParent instanceof PyIfStatement) || (commonParent instanceof PyLoopStatement) ||
            (commonParent instanceof PyStatementPart) || (commonParent instanceof PyWithStatement))
          return true;
      }
    }
    return false;
  }

  private boolean isMoveOut(MoveInfo info, Editor editor, PsiFile file, boolean down) {
    Pair<PyElement, PyElement> insertDeleteParts = getStatementParts(info, editor, file, down);
    PyElement statementPart1 = insertDeleteParts.first;
    PyElement statementPart2 = insertDeleteParts.second;
    if (statementPart1 != null) {
      if (statementPart2 == null) return true;
      if (statementPart1.getParent() != statementPart2.getParent()) {
        PsiElement commonParent = PsiTreeUtil.findCommonParent(statementPart1, statementPart2);
        if (!(commonParent instanceof PyIfStatement) && !(commonParent instanceof PyLoopStatement) && !(commonParent instanceof PyStatementPart)
            && !(commonParent instanceof PyWithStatement))
          return true;
        if (PsiTreeUtil.isAncestor(statementPart2, statementPart1, false)) return true;
      }
    }
    return false;
  }

  private void prepareToStatement(PyStatementPart part, Document document) {
    int partLine = document.getLineNumber(part.getTextRange().getStartOffset());
    PyStatementList stList = part.getStatementList();
    if (stList == null) return;
    if (stList.getStatements().length < 1) return;
    int statementLine = document.getLineNumber(stList.getStatements()[0].getTextRange().getStartOffset());
    if (partLine == statementLine) {
      myStatementToAddLinebreak = stList.getStatements()[0];
    }
  }

  private boolean isTheSameIndentLevel(MoveInfo info, Editor editor, PsiFile file, boolean down) {
    Pair<PyElement, PyElement> statementParts = getStatementParts(info, editor, file, down);
    if (statementParts.second instanceof PyStatementPart)
      myStatementPartToRemovePass = (PyStatementPart)statementParts.second;
    PyElement statementPart1 = statementParts.first;
    PyElement statementPart2 = statementParts.second;

    if (statementPart2 != null && statementPart1 != null && statementPart1.getParent() == statementPart2.getParent() ||
        statementPart2 == statementPart1) return true;
    return false;
  }

  private void decreaseIndent(Editor editor) {
    CodeStyleSettings.IndentOptions indentOptions = CodeStyleSettingsManager.getInstance(editor.getProject()).
                                                                getCurrentSettings().getIndentOptions(PythonFileType.INSTANCE);
    assert indentOptions != null;
    final Document document = editor.getDocument();
    TextRange textRange = myStatementToDecreaseIndent.getTextRange();
    document.deleteString(textRange.getStartOffset()-indentOptions.INDENT_SIZE, textRange.getStartOffset());
    myStatementToDecreaseIndent = null;
  }

  private void increaseIndent(final Editor editor) {
    CodeStyleSettings.IndentOptions indentOptions = CodeStyleSettingsManager.getInstance(editor.getProject()).
                                                                  getCurrentSettings().getIndentOptions(PythonFileType.INSTANCE);
    assert indentOptions != null;
    final Document document = editor.getDocument();
    final int startLine = editor.offsetToLogicalPosition(myStatementToIncreaseIndent.getTextRange().getStartOffset()).line;
    final int endLine = editor.offsetToLogicalPosition(myStatementToIncreaseIndent.getTextRange().getEndOffset()).line;
    for (int line = startLine; line <= endLine; ++line) {
      final int offset = document.getLineStartOffset(line);
      document.insertString(offset, StringUtil.repeat(" ", indentOptions.INDENT_SIZE));
    }
    myStatementToIncreaseIndent = null;
  }

  @Override
  public void beforeMove(@NotNull Editor editor, @NotNull MoveInfo info, boolean down) {
    if (myStatementToAddLinebreak != null) {
      //PY-950
      TextRange textRange = myStatementToAddLinebreak.getTextRange();
      int line = editor.getDocument().getLineNumber(textRange.getStartOffset());
      CodeStyleSettings.IndentOptions indentOptions = CodeStyleSettingsManager.getInstance(editor.getProject()).
                                                                getCurrentSettings().getIndentOptions(PythonFileType.INSTANCE);
      
      PsiElement whiteSpace = myStatementToAddLinebreak.getContainingFile().findElementAt(editor.getDocument().getLineStartOffset(line));
      String indent = StringUtil.repeatSymbol(' ', indentOptions.INDENT_SIZE);

      PyStatementWithElse statementWithElse = PsiTreeUtil.getParentOfType(myStatementToAddLinebreak, PyStatementWithElse.class);
      if (statementWithElse != null && statementWithElse.getParent() instanceof PyFile) indent = "\n";
      if (whiteSpace instanceof PsiWhiteSpace) indent += whiteSpace.getText();
      if (down) indent += StringUtil.repeatSymbol(' ', indentOptions.INDENT_SIZE);
      editor.getDocument().insertString(textRange.getStartOffset(), indent);
    }
    // add pass statement if needed
    if (myStatementListToAddPass != null) {
      final PyPassStatement passStatement =
        PyElementGenerator.getInstance(editor.getProject()).createFromText(LanguageLevel.getDefault(), PyPassStatement.class, PyNames.PASS);
      if (!down) {
        myStatementListToAddPass.add(passStatement);
      }
      else
        myStatementListToAddPass.addBefore(passStatement, myStatementListToAddPass.getStatements()[0]);
      CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(myStatementListToAddPass);
    }
    if (myStatementToIncreaseIndent != null) {
      increaseIndent(editor);
    }
    if (myStatementToDecreaseIndent != null) {
      decreaseIndent(editor);
    }
  }

  @Override
  public void afterMove(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    // add pass statement if needed
    if (myStatementListToAddPassAfter != null && myStatementListToAddPassAfter.isValid()) {
      final PyPassStatement passStatement =
        PyElementGenerator.getInstance(editor.getProject()).createFromText(LanguageLevel.getDefault(), PyPassStatement.class, PyNames.PASS);
      myStatementListToAddPassAfter.add(passStatement);
      CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(myStatementListToAddPassAfter);
    }

    // remove obsolete pass statement
    if (myStatementPartToRemovePass != null) {
      PyStatementList statementList = myStatementPartToRemovePass.getStatementList();
      if (statementList != null) {
        PyPsiUtils.removeRedundantPass(statementList);
      }
    }
  }

}
