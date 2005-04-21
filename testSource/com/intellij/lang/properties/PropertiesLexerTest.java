package com.intellij.lang.properties;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LightIdeaTestCase;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 10:44:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesLexerTest extends LightIdeaTestCase {
  private static void doTest(String text, String[] expectedTokens) {
    Lexer lexer = new PropertiesLexer();
    doTest(text, expectedTokens, lexer);
  }

  private static void doTestHL(String text, String[] expectedTokens) {
    Lexer lexer = new PropertiesHighlightingLexer();
    doTest(text, expectedTokens, lexer);
  }

  private static void doTest(String text, String[] expectedTokens,Lexer lexer) {
    lexer.start(text.toCharArray());
    int idx = 0;
    while (lexer.getTokenType() != null) {
      if (idx >= expectedTokens.length) fail("Too many tokens");
      String tokenName = lexer.getTokenType().toString();
      String expectedTokenType = expectedTokens[idx++];
      String expectedTokenText = expectedTokens[idx++];
      assertEquals(expectedTokenType, tokenName);
      String tokenText = new String(lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd() - lexer.getTokenStart());
      assertEquals(expectedTokenText, tokenText);
      lexer.advance();
    }

    if (idx < expectedTokens.length) fail("Not enough tokens");
  }

  public void testSimple() throws Exception {
    doTest("xxx=yyy", new String[]{
      "Properties:KEY_CHARACTERS", "xxx",
      "Properties:KEY_VALUE_SEPARATOR", "=",
      "Properties:VALUE_CHARACTERS", "yyy",
    });
  }

  public void testTwoWords() throws Exception {
    doTest("xxx=yyy zzz", new String[]{
      "Properties:KEY_CHARACTERS", "xxx",
      "Properties:KEY_VALUE_SEPARATOR", "=",
      "Properties:VALUE_CHARACTERS", "yyy zzz",
    });
  }

  public void testMulti() throws Exception {
    doTest("a  b\n \nx\ty", new String[]{
      "Properties:KEY_CHARACTERS", "a",
      "Properties:KEY_VALUE_SEPARATOR", "  ",
      "Properties:VALUE_CHARACTERS", "b",
      "WHITE_SPACE", "\n \n",
      "Properties:KEY_CHARACTERS", "x",
      "Properties:KEY_VALUE_SEPARATOR", "\t",
      "Properties:VALUE_CHARACTERS", "y"
    });
  }

  public void testIncompleteProperty() throws Exception {
    doTest("a", new String[]{
      "Properties:KEY_CHARACTERS", "a"
    });
  }

  public void testIncompleteProperty2() throws Exception {
    doTest("a.2=", new String[]{
      "Properties:KEY_CHARACTERS", "a.2",
      "Properties:KEY_VALUE_SEPARATOR", "="
    });
  }

  public void testEscaping() throws Exception {
    doTest("sdlfkjsd\\l\\\\\\:\\=gk   =   s\\nsssd", new String[]{
      "Properties:KEY_CHARACTERS", "sdlfkjsd\\l\\\\\\:\\=gk",
      "Properties:KEY_VALUE_SEPARATOR", "   =   ",
      "Properties:VALUE_CHARACTERS", "s\\nsssd"
    });
  }

  public void testCRLFEscaping() throws Exception {
    doTest("sdlfkjsdsssd:a\\\nb", new String[]{
      "Properties:KEY_CHARACTERS", "sdlfkjsdsssd",
      "Properties:KEY_VALUE_SEPARATOR", ":",
      "Properties:VALUE_CHARACTERS", "a\\\nb"
    });
  }

  public void testCRLFEscapingKey() throws Exception {
    doTest("x\\\ny:z", new String[]{
      "Properties:KEY_CHARACTERS", "x\\\ny",
      "Properties:KEY_VALUE_SEPARATOR", ":",
      "Properties:VALUE_CHARACTERS", "z"
    });
  }

  public void testWhitespace() throws Exception {
    doTest("x y", new String[]{
      "Properties:KEY_CHARACTERS", "x",
      "Properties:KEY_VALUE_SEPARATOR", " ",
      "Properties:VALUE_CHARACTERS", "y"
    });
  }

  public void testHighlighting() throws Exception {
    doTestHL("x y", new String[]{
      "Properties:KEY_CHARACTERS", "x",
      "Properties:KEY_VALUE_SEPARATOR", " ",
      "Properties:VALUE_CHARACTERS", "y"
    });
  }

  public void testHighlighting2() throws Exception {
    doTestHL("x\\n\\kz y", new String[]{
      "Properties:KEY_CHARACTERS", "x",
      "VALID_STRING_ESCAPE_TOKEN", "\\n",
      "INVALID_STRING_ESCAPE_TOKEN", "\\k",
      "Properties:KEY_CHARACTERS", "z",
      "Properties:KEY_VALUE_SEPARATOR", " ",
      "Properties:VALUE_CHARACTERS", "y"
    });
  }

  public void testHighlighting3() throws Exception {
    doTestHL("x  \\uxyzt\\pz\\tp", new String[]{
      "Properties:KEY_CHARACTERS", "x",
      "Properties:KEY_VALUE_SEPARATOR", "  ",
      "INVALID_STRING_ESCAPE_TOKEN", "\\uxyzt",
      "INVALID_STRING_ESCAPE_TOKEN", "\\p",
      "Properties:VALUE_CHARACTERS", "z",
      "VALID_STRING_ESCAPE_TOKEN", "\\t",
      "Properties:VALUE_CHARACTERS", "p",
    });
  }

}
