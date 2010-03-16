package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

/**
 * @author yole
 */
public class PyFormatterTest extends PyLightFixtureTestCase {
  public void testBlankLineBetweenMethods() throws Exception {
    doTest();
  }

  public void testBlankLineAroundClasses() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    myFixture.configureByFile("formatter/" + getTestName(true) + ".py");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CodeStyleManager.getInstance(myFixture.getProject()).reformat(myFixture.getFile());
      }
    });
    myFixture.checkResultByFile("formatter/" + getTestName(true) + "_after.py");
  }
}
