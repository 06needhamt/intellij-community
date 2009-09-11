package com.intellij.refactoring;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.JavaTestUtil;

/**
 * @author ven
 */
public class RenameLocalTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/renameLocal/";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testIDEADEV3320() throws Exception {
    doTest("f");
  }

  public void testIDEADEV13849() throws Exception {
    doTest("aaaaa");
  }

  public void testConflictWithOuterClassField() throws Exception {  // IDEADEV-24564
    doTest("f");
  }

  private void doTest(final String newName) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtilBase
      .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, newName, true, true).run();
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
