package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyIntentionTest extends PyTestCase {

  private void doTest(String hint) {
    myFixture.configureByFile("intentions/before" + getTestName(false) + ".py");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("intentions/after" + getTestName(false) + ".py");
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

  private void doTest(String hint, boolean ignoreWhiteSpaces) {
    myFixture.configureByFile("intentions/before" + getTestName(false) + ".py");
    final IntentionAction action = myFixture.findSingleIntention(hint);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("intentions/after" + getTestName(false) + ".py", ignoreWhiteSpaces);
  }

  /**
   * Ensures that intention with given hint <i>is not</i> active.
   * @param hint
   */
  private void doNegativeTest(String hint) {
    myFixture.configureByFile("intentions/before" + getTestName(false) + ".py");
    List<IntentionAction> ints = myFixture.filterAvailableIntentions(hint);
    assertEmpty(ints);
  }

  public void testConvertDictComp() {
    setLanguageLevel(LanguageLevel.PYTHON26);
    doTest(PyBundle.message("INTN.convert.dict.comp.to"));
  }

  public void testConvertSetLiteral() {
    setLanguageLevel(LanguageLevel.PYTHON26);
    doTest(PyBundle.message("INTN.convert.set.literal.to"));
  }

  public void testReplaceExceptPart() {
    doTest(PyBundle.message("INTN.convert.except.to"), LanguageLevel.PYTHON30);
  }

  public void testConvertBuiltins() {
    doTest(PyBundle.message("INTN.convert.builtin.import"), LanguageLevel.PYTHON30);
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

  public void testStringConcatToFormat1() {   //PY-5226
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testStringConcatToFormat2() {   //PY-6505
    doNegativeTest(PyBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testStringConcatToFormat3() {   //PY-6505
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testConvertFormatOperatorToMethod() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testConvertFormatOperatorToMethodMulti() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testConvertFormatOperatorToMethodEscaped() {
    doTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testFlipComparison() {
    doTest(PyBundle.message("INTN.flip.$0.to.$1", ">", "<"));
  }

  public void testReplaceListComprehensionWithFor() {
    doTest(PyBundle.message("INTN.replace.list.comprehensions.with.for"));
  }

  public void testReplaceListComprehension2() {    //PY-6731
    doTest(PyBundle.message("INTN.replace.list.comprehensions.with.for"));
  }

  public void testJoinIf() {
    doTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testJoinIfElse() {
    doNegativeTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testJoinIfBinary() {              //PY-4697
    doTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testJoinIfMultiStatements() {           //PY-2970
    doNegativeTest(PyBundle.message("INTN.join.if.text"));
  }

  public void testDictConstructorToLiteralForm() {
    doTest(PyBundle.message("INTN.convert.dict.constructor.to.dict.literal"));
  }

  public void testDictLiteralFormToConstructor() {
    doTest(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
  }

  public void testDictLiteralFormToConstructor1() {      //PY-2873
    myFixture.configureByFile("intentions/beforeDictLiteralFormToConstructor1" + ".py");
    final IntentionAction action = myFixture.getAvailableIntention(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
    assertNull(action);
  }

  public void testDictLiteralFormToConstructor2() {      //PY-5157
    myFixture.configureByFile("intentions/beforeDictLiteralFormToConstructor2" + ".py");
    final IntentionAction action = myFixture.getAvailableIntention(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
    assertNull(action);
  }
  public void testDictLiteralFormToConstructor3() {
    myFixture.configureByFile("intentions/beforeDictLiteralFormToConstructor3" + ".py");
    final IntentionAction action = myFixture.getAvailableIntention(PyBundle.message("INTN.convert.dict.literal.to.dict.constructor"));
    assertNull(action);
  }

  public void testQuotedString() {      //PY-2915
    doTest(PyBundle.message("INTN.quoted.string.double.to.single"));
  }

  public void testQuotedStringDoubleSlash() {      //PY-3295
    doTest(PyBundle.message("INTN.quoted.string.single.to.double"));
  }

  public void testEscapedQuotedString() { //PY-2656
    doTest(PyBundle.message("INTN.quoted.string.single.to.double"));
  }

  public void testDoubledQuotedString() { //PY-2689
    doTest(PyBundle.message("INTN.quoted.string.double.to.single"));
  }

  public void testConvertLambdaToFunction() {
    doTest(PyBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertLambdaToFunction1() {    //PY-6610
    doNegativeTest(PyBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertLambdaToFunction2() {    //PY-6835
    doTest(PyBundle.message("INTN.convert.lambda.to.function"));
  }

  public void testConvertVariadicParam() { //PY-2264
    doTest(PyBundle.message("INTN.convert.variadic.param"));
  }
  public void testConvertTripleQuotedString() { //PY-2697
    doTest(PyBundle.message("INTN.triple.quoted.string"));
  }

  public void testTransformConditionalExpression() { //PY-3094
    doTest(PyBundle.message("INTN.transform.into.if.else.statement"));
  }
  
  public void testImportFromToImport() {
    doTest("Convert to 'import sys'");
  }

  public void testTypeInDocstring() {
    doTest(PyBundle.message("INTN.specify.type"));
  }

  public void testTypeInDocstring1() {
    doTest(PyBundle.message("INTN.specify.return.type"));
  }

  public void testTypeInPy3Annotation() {      //PY-7045
    doTest(PyBundle.message("INTN.specify.type.in.annotation"), LanguageLevel.PYTHON32);
  }

  public void testReturnTypeInPy3Annotation() {      //PY-7085
    doTest(PyBundle.message("INTN.specify.type.in.annotation"), LanguageLevel.PYTHON32);
  }

  public void testTypeAssertion() {
    doTest(PyBundle.message("INTN.insert.assertion"));
  }

  public void testTypeAssertion1() { //PY-7089
    doTest(PyBundle.message("INTN.insert.assertion"));
  }

  public void testTypeAssertion2() {
    doTest(PyBundle.message("INTN.insert.assertion"));
  }

  public void testDocStub() {
    doDocStubTest();
  }

  public void testOneLineDocStub() {
    doDocStubTest();
  }

  // PY-7383
  public void testYieldFrom() {
    doTest(PyBundle.message("INTN.yield.from"), LanguageLevel.PYTHON33);
  }

  private void doDocStubTest() {
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    codeInsightSettings.JAVADOC_STUB_ON_ENTER = true;
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myFixture.getProject());
    documentationSettings.setFormat(DocStringFormat.REST);
    try {
      doTest(PyBundle.message("INTN.doc.string.stub"), true);
    }
    finally {
      documentationSettings.setFormat(DocStringFormat.PLAIN);
    }
  }
}
