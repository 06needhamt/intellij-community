package com.jetbrains.python;

import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyEditingTest extends PyLightFixtureTestCase {
  public void testNoPairedParenthesesBeforeIdentifier() {       // PY-290
    assertEquals("(abc", doTestTyping("abc", 0, '('));
  }

  public void testPairedParenthesesAtEOF() {
    assertEquals("abc()", doTestTyping("abc", 3, '('));
  }

  public void testPairedQuotesInRawString() {   // PY-263
    assertEquals("r''", doTestTyping("r", 1, '\''));
  }

  public void testNonClosingQuoteAtIdent() {   // PY-380
    assertEquals("'abc", doTestTyping("abc", 0, '\''));
  }

  public void testNonClosingQuoteAtNumber() {   // PY-380
    assertEquals("'123", doTestTyping("123", 0, '\''));
  }

  public void testAutoClosingQuoteAtRBracket() {
    assertEquals("'']", doTestTyping("]", 0, '\''));
  }

  public void testAutoClosingQuoteAtRParen() {
    assertEquals("'')", doTestTyping(")", 0, '\''));
  }

  public void testAutoClosingQuoteAtComma() {
    assertEquals("'',", doTestTyping(",", 0, '\''));
  }

  public void testAutoClosingQuoteAtSpace() {
    assertEquals("'' ", doTestTyping(" ", 0, '\''));
  }

  public void testNoClosingTriple() {
    assertEquals("'''", doTestTyping("''", 2, '\''));
  }

  public void testOvertypeFromInside() {
    assertEquals("''", doTestTyping("''", 1, '\''));
  }

  public void testGreedyBackspace() {  // PY-254
    doTestBackspace("py254", new LogicalPosition(4, 8));
  }

  public void testUnindentBackspace() {  // PY-853
    doTestBackspace("smartUnindent", new LogicalPosition(1, 4));
  }

  public void testUnindentTab() {  // PY-1270
    doTestBackspace("unindentTab", new LogicalPosition(4, 4));
  }

  private void doTestBackspace(final String fileName, final LogicalPosition pos) {
    myFixture.configureByFile("/editing/" + fileName + ".before.py");
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(pos);
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      public void run() {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
      }
    }, "", null);
    myFixture.checkResultByFile("/editing/" + fileName + ".after.py", true);
  }

  public void testUncommentWithSpace() throws Exception {   // PY-980
    myFixture.configureByFile("/editing/uncommentWithSpace.before.py");
    myFixture.getEditor().getCaretModel().moveToLogicalPosition(new LogicalPosition(0, 1));
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
      public void run() {
        CommentByLineCommentAction action = new CommentByLineCommentAction();
        action.actionPerformed(new AnActionEvent(null, DataManager.getInstance().getDataContext(), "", action.getTemplatePresentation(),
                                                 ActionManager.getInstance(), 0));
      }
    }, "", null);
    myFixture.checkResultByFile("/editing/uncommentWithSpace.after.py", true);
  }

  public void testEnterInLineComment() {  // PY-1739
    doTestEnter("# foo <caret>bar", "# foo \n# bar");
  }

  public void testEnterInStatement() {
    doTestEnter("if a <caret>and b: pass", "if a \\\nand b: pass");
  }

  public void testEnterBeforeStatement() {
    doTestEnter("def foo(): <caret>pass", "def foo(): \n    pass");
  }

  public void testEnterInParameterList() {
    doTestEnter("def foo(a,<caret>b): pass", "def foo(a,\n        b): pass");
  }

  public void testEnterInTuple() {
    doTestEnter("for x in 'a', <caret>'b': pass", "for x in 'a', \\\n         'b': pass");
  }

  public void testEnterInCodeWithErrorElements() {
    doTestEnter("z=1 <caret>2", "z=1 \n2");
  }

  public void testEnterAtStartOfComment() {
    doTestEnter("# bar\n<caret># foo", "# bar\n\n# foo");
  }

  public void testEnterAtEndOfComment() {
    doTestEnter("# bar<caret>\n# foo", "# bar\n\n# foo");
  }

  private void doTestEnter(String before, final String after) {
    int pos = before.indexOf("<caret>");
    before = before.replace("<caret>", "");
    assertEquals(after, doTestTyping(before, pos, '\n'));
  }

  private String doTestTyping(final String text, final int offset, final char character) {
    final PsiFile file = ApplicationManager.getApplication().runWriteAction(new Computable<PsiFile>() {
      public PsiFile compute() {
        final PsiFile file = myFixture.configureByText(PythonFileType.INSTANCE, text);
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        myFixture.type(character);
        return file;
      }
    });
    return myFixture.getDocument(file).getText();
  }
}
