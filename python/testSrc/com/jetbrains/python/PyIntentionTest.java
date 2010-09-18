package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

/**
 * @author Alexey.Ivanov
 */
public class PyIntentionTest extends PyLightFixtureTestCase {

  private void doTest(String hint) {
    myFixture.configureByFile("before" + getTestName(false) + ".py");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("after" + getTestName(false) + ".py");
  }

  private void doTest(String hint, LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    try {
      doTest(hint);
    }
    finally {
      PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    }
  }

  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/intentions/";
  }

  public void testConvertDictComp() {
    doTest(PyBundle.message("INTN.convert.dict.comp.to"));
  }

  public void testConvertSetLiteral() {
    doTest(PyBundle.message("INTN.convert.set.literal.to"));
  }

  public void testReplaceExceptPart() {
    doTest(PyBundle.message("INTN.convert.except.to"), LanguageLevel.PYTHON30);
  }

  public void testConvertBuiltins() {
    doTest(PyBundle.message("INTN.convert.builtin.import"), LanguageLevel.PYTHON30);
  }

  public void testReplaceNotEqOperator() {
    doTest(PyBundle.message("INTN.replace.noteq.operator"));
  }

  public void testRemoveLeadingU() {
    doTest(PyBundle.message("INTN.remove.leading.u"), LanguageLevel.PYTHON30);
  }

  public void testRemoveTrailingL() {
    doTest(PyBundle.message("INTN.remove.trailing.l"), LanguageLevel.PYTHON30);
  }

  public void testReplaceOctalNumericLiteral() {
    doTest(PyBundle.message("INTN.replace.octal.numeric.literal"), LanguageLevel.PYTHON30);
  }

  public void testReplaceListComprehensions() {
    doTest(PyBundle.message("INTN.replace.list.comprehensions"), LanguageLevel.PYTHON30);
  }

  public void testReplaceRaiseStatement() {
    doTest(PyBundle.message("INTN.replace.raise.statement"), LanguageLevel.PYTHON30);
  }

  public void testReplaceBackQuoteExpression() {
    doTest(PyBundle.message("INTN.replace.backquote.expression"), LanguageLevel.PYTHON30);
  }

  /*
  public void testReplaceMethod() {
    doTest(PyBundle.message("INTN.replace.method"), LanguageLevel.PYTHON30);
  }
  */

  public void testSplitIf() {
    doTest(PyBundle.message("INTN.split.if.text"));
  }

  public void testNegateComparison() {
    doTest(PyBundle.message("INTN.negate.$0.to.$1", "<=", ">"));
  }

  public void testNegateComparison2() {
    doTest(PyBundle.message("INTN.negate.$0.to.$1", ">", "<="));
  }

  public void testStringConcatToFormat() {
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testConvertFormatOperatorToMethod() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testConvertFormatOperatorToMethodMulti() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testFlipComparison() {
    doTest(PyBundle.message("INTN.flip.$0.to.$1", ">", "<"));
  }

}
