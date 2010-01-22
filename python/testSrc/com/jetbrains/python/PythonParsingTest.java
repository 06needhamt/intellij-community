package com.jetbrains.python;

import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/psi/")
public class PythonParsingTest extends ParsingTestCase {
  public PythonParsingTest() {
    super("", "py");
    PyLightFixtureTestCase.initPlatformPrefix();
  }

  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  public void testHelloWorld() throws Exception {
    doTest();
  }

  public void testIfStatement() throws Exception {
    doTest();
  }

  public void testConditionalExpression() throws Exception {
    doTest();
  }

  public void testSubscribedAssignmentLHS() throws Exception {
    doTest();
  }

  public void testConditionalParenLambda() throws Exception {
    doTest();
  }

  public void testLambdaComprehension() throws Exception {
    doTest();
  }

  public void testLambdaConditional() throws Exception {
    doTest();
  }

  public void testTryExceptFinally() throws Exception {
    doTest();
  }

  public void testTryFinally() throws Exception {
    doTest();
  }

  public void testYieldStatement() throws Exception {
    doTest();
  }

  public void testYieldInAssignment() throws Exception {
    doTest();
  }

  public void testYieldInAugAssignment() throws Exception {
    doTest();
  }

  public void testYieldInParentheses() throws Exception {
    doTest();
  }

  public void _testYieldAsArgument() throws Exception {
    // this is a strange case: PEP 342 says this syntax is valid, but
    // Python 2.5 doesn't accept it. let's stick with Python behavior for now
    doTest();
  }

  public void testWithStatement() throws Exception {
    doTest();
  }

  public void testWithStatement2() throws Exception {
    doTest();
  }

  public void testImportStmt() throws Exception {
    doTest();
  }

  public void testDecoratedFunction() throws Exception {
    doTest();
  }

  public void testTryExceptAs() throws Exception {   // PY-293
    doTest();
  }

  public void testWithStatement26() throws Exception {
    doTest(LanguageLevel.PYTHON26);
  }

  public void testPrintAsFunction26() throws Exception {
    doTest(LanguageLevel.PYTHON26);
  }

  public void testClassDecorators() throws Exception {
    doTest(LanguageLevel.PYTHON26);
  }

  public void testEmptySuperclassList() throws Exception {  // PY-321
    doTest();
  }

  public void testListComprehensionNestedIf() throws Exception {  // PY-322
    doTest();
  }

  public void testKeywordOnlyArgument() throws Exception {   // PEP 3102
    doTest(LanguageLevel.PYTHON30);
  }

  public void testPy3KKeywords() throws Exception {
    doTest(LanguageLevel.PYTHON30);
  }

  public void testExecPy2() throws Exception {
    doTest();
  }

  public void testExecPy3() throws Exception {
    doTest(LanguageLevel.PYTHON30);
  }

  public void testSuperclassKeywordArguments() throws Exception {
    doTest(LanguageLevel.PYTHON30);
  }

  public void testDictLiteral() throws Exception {
    doTest();
  }

  public void testSetLiteral() throws Exception {
    doTest(LanguageLevel.PYTHON30);
  }

  public void doTest() throws Exception {
    doTest(LanguageLevel.PYTHON25);
  }

  public void doTest(LanguageLevel languageLevel) throws Exception {
    PythonLanguageLevelPusher.FORCE_LANGUAGE_LEVEL = languageLevel;
    PythonLanguageLevelPusher.pushLanguageLevel(getProject());
    try {
      doTest(true);
    }
    finally {
      PythonLanguageLevelPusher.FORCE_LANGUAGE_LEVEL = null;
    }
  }
}
