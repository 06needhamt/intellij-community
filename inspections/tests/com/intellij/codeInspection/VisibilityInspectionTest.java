package com.intellij.codeInspection;

import com.intellij.codeInspection.visibility.VisibilityInspection;

public class VisibilityInspectionTest extends InspectionTestCase {
  private VisibilityInspection myTool = new VisibilityInspection();
  private void doTest() throws Exception {
    myTool.initialize(getManager());
    doTest("visibility/" + getTestName(false), myTool);
  }

  public void testinnerConstructor() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = false;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testpackageLevelTops() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;

    doTest();
  }

  public void testSCR5008() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testSCR6856() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testSCR11792() throws Exception {
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = true;

    doTest();
  }

  public void testDisabledInCurrentProfile() throws Exception{
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = false;
    myTool.SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true;
    myTool.SUGGEST_PRIVATE_FOR_INNERS = false;

    getManager().RUN_WITH_EDITOR_PROFILE = true;
    doTest();
  }

}
