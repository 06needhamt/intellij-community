/*
 * User: anna
 * Date: 06-Mar-2008
 */
package com.jetbrains.python;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

import java.util.Arrays;
import java.util.List;

public class PythonCompletionTest extends PyLightFixtureTestCase {

  private void doTest() {
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testLocalVar() {
    doTest();
  }

  public void testSelfMethod() {
    doTest();
  }

  public void testSelfField() {
    doTest();
  }

  public void testFuncParams() {
    doTest();
  }

  public void testFuncParamsStar() {
    doTest();
  }

  public void testInitParams() {
    doTest();
  }

  public void testSuperInitParams() {      // PY-505
    doTest();
  }

  public void testSuperInitKwParams() {      // PY-778
    doTest();
  }

  public void testPredefinedMethodName() {
    doTest();
  }

  public void testPredefinedMethodNot() {
    doTest();
  }

  public void testKeywordAfterComment() {  // PY-697
    doTest();
  }

  public void testClassPrivate() {
    doTest();
  }

  public void testClassPrivateNotInherited() {
    doTest();
  }

  public void testClassPrivateNotPublic() {
    doTest();
  }

  public void testTwoUnderscores() {
    doTest();
  }

  public void testOneUnderscore() {
    doTest();
  }

  public void testTwoUnderscoresNotOne() {
    doTest();
  }

  public void testKwParamsInCodeUsage() { //PY-1002
    doTest();
  }
  
  public void testKwParamsInCodeGetUsage() { //PY-1002 
    doTest();
  }

  public void testSuperInitKwParamsNotOnlySelfAndKwArgs() { //PY-1050 
    doTest();
  }

  public void testSuperInitKwParamsNoCompletion() {
    doTest();
  }

  public void testIsInstance() {
    doTest();    
  }

  public void testIsInstanceAssert() {
    doTest();
  }

  public void testIsInstanceTuple() {
    doTest();
  }

  public void testPropertyParens() {  // PY-1037
    doTest();
  }

  public void testClassNameFromVarName() {
    doTest();
  }

  public void testPropertyType() {
    doTest();
  }

  public void testSeenMembers() {  // PY-1181
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFile(testName + ".py");
    final LookupElement[] elements = myFixture.completeBasic();
    assertEquals(1, elements.length);
    assertEquals("children", elements [0].getLookupString());
  }

  public void testImportModule() {
    final String testName = "completion/" + getTestName(true);
    myFixture.configureByFiles(testName + ".py", "completion/someModule.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testPy255() {
    final String dirname = "completion/";
    final String testName = dirname + "moduleClass";
    myFixture.configureByFiles(testName + ".py", dirname + "__init__.py");
    myFixture.copyDirectoryToProject(dirname + "mymodule", dirname + "mymodule");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testPy874() {
    final String dirname = "completion/";
    final String testName = dirname + "py874";
    myFixture.configureByFile(testName + ".py");
    myFixture.copyDirectoryToProject(dirname + "root", dirname + "root");
    myFixture.completeBasic();
    myFixture.checkResultByFile(testName + ".after.py");
  }

  public void testClassMethod() {  // PY-833
    doTest();
  }

  public void testStarImport() {
    myFixture.configureByFiles("completion/starImport/starImport.py", "completion/starImport/importSource.py");
    myFixture.completeBasic();
    assertSameElements(myFixture.getLookupElementStrings(), Arrays.asList("my_foo", "my_bar"));
  }

  public void testSlots() {  // PY-1211
    doTest();
  }

  public void testReturnType() {  
    doTest();
  }

  public void testChainedCall() {  // PY-1565
    doTest();
  }

  public void testFromImportBinary() {
    myFixture.copyFileToProject("completion/root/binary_mod.pyd");
    myFixture.copyFileToProject("completion/root/binary_mod.so");
    myFixture.configureByFiles("completion/fromImportBinary.py", "completion/root/__init__.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/fromImportBinary.after.py");
  }

  public void testNonExistingProperty() {  // PY-1748
    doTest();
  }

  public void testEmptyFile() {  // PY-1845
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myFixture.configureByText(PythonFileType.INSTANCE, "");
      }
    });
    myFixture.completeBasic();
    final List<String> elements = myFixture.getLookupElementStrings();
    assertTrue(elements.contains("import"));
  }

  public void testImportItself() {  // PY-1895
    myFixture.copyDirectoryToProject("completion/importItself/package1", "package1");
    myFixture.configureFromTempProjectFile("package1/submodule1.py");
    myFixture.completeBasic();
    myFixture.checkResultByFile("completion/importItself.after.py");
  }
}