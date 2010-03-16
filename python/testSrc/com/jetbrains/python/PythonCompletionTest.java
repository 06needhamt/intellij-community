/*
 * User: anna
 * Date: 06-Mar-2008
 */
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

public class PythonCompletionTest extends PyLightFixtureTestCase {

  private void doTest() throws Exception {
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testLocalVar() throws Exception {
    doTest();
  }

  public void testSelfMethod() throws Exception {
    doTest();
  }

  public void testSelfField() throws Exception {
    doTest();
  }

  public void testFuncParams() throws Exception {
    doTest();
  }

  public void testFuncParamsStar() throws Exception {
    doTest();
  }

  public void testInitParams() throws Exception {
    doTest();
  }

  public void testSuperInitParams() throws Exception {      // PY-505
    doTest();
  }

  public void testPredefinedMethodName() throws Exception {
    doTest();
  }

  public void testPredefinedMethodNot() throws Exception {
    doTest();
  }

  public void testKeywordAfterComment() throws Exception {  // PY-697
    doTest();
  }

  public void testPy255() throws Exception {
    final String dirname = "completion/";
    final String testName = dirname + "moduleClass";
    myFixture.configureByFile(testName + ".py");
    myFixture.copyDirectoryToProject(dirname + "mymodule", dirname + "mymodule");
    myFixture.copyDirectoryToProject(dirname + "mymodule/mysubmodule", dirname + "mymodule/mysubmodule");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }
}