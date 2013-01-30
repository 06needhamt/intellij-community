package com.jetbrains.python;

import com.intellij.lexer.Lexer;
import com.jetbrains.python.codeInsight.regexp.PythonRegexpParserDefinition;
import com.jetbrains.python.codeInsight.regexp.PythonVerboseRegexpParserDefinition;
import com.jetbrains.python.fixtures.PyLexerTestCase;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyRegexpTest extends PyTestCase {
  public void testNestedCharacterClasses() {  // PY-2908
    doTestHighlighting();
  }

  public void testNestedCharacterClassesLexer() {
    doTestLexer("[][]", "CLASS_BEGIN", "CHARACTER", "CHARACTER", "CLASS_END");
  }

  public void testNestedCharacterClasses2() {  // PY-2908
    doTestHighlighting();
  }

  public void testOctal() {  // PY-2906
    doTestHighlighting();
  }

  public void testBraceInPythonCharacterClass() {  // PY-1929
    doTestHighlighting();
  }

  public void testNegatedBraceInCharacterClass() {
    doTestLexer("[^][]", "CLASS_BEGIN", "CARET", "CHARACTER", "CHARACTER", "CLASS_END");
  }

  public void testDanglingMetacharacters() {  // PY-2430
    doTestHighlighting();
  }

  public void testVerbose() {
    Lexer lexer = new PythonVerboseRegexpParserDefinition().createLexer(myFixture.getProject());
    PyLexerTestCase.doLexerTest("# abc", lexer, "COMMENT", "COMMENT");
  }

  public void testRedundantEscapeSingleQuote() {  // PY-5027
    doTestHighlighting();
  }

  public void testBraceCommaN() {  // PY-8304
    doTestHighlighting();
  }

  public void testVerboseAsKwArg() {  // PY-8143
    doTestHighlighting();
  }

  private void doTestHighlighting() {
    myFixture.testHighlighting(true, false, true, "regexp/" + getTestName(true) + ".py");
  }

  private void doTestLexer(final String text, String... expectedTokens) {
    Lexer lexer = new PythonRegexpParserDefinition().createLexer(myFixture.getProject());
    PyLexerTestCase.doLexerTest(text, lexer, expectedTokens);
  }
}
