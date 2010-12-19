package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyFoldingTest extends PyLightFixtureTestCase {
  private void doTest() {
    myFixture.testFolding(getTestDataPath() + "/folding/" + getTestName(false) + ".py");
  }

  public void testClassTrailingSpace() {  // PY-2544
    doTest();
  }
}
