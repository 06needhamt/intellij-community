package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;

/**
 * @author ven
 */
public class FieldCanBeLocalTest extends InspectionTestCase {
  private LocalInspectionTool myTool;
  private LocalInspectionToolWrapper myWrapper;

  protected void setUp() throws Exception {
    super.setUp();
    myWrapper = new LocalInspectionToolWrapper( new FieldCanBeLocalInspection());
    myTool = myWrapper.getTool();
    myWrapper.initialize(getManager());
  }

  private void doTest() throws Exception {
    doTest("fieldCanBeLocal/" + getTestName(false), myWrapper);
  }

  public void testSimple () throws Exception {
    doTest();
  }
}
