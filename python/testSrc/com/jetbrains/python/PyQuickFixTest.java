package com.jetbrains.python;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;

/**
 * Test actions that various inspections add.
 * User: dcheryasov
 * Date: Nov 29, 2008 12:47:08 AM
 */
@TestDataPath("$CONTENT_ROOT/../testData/inspections/")
public class PyQuickFixTest extends PyLightFixtureTestCase {

  public void testAddImport() {
    doInspectionTest(new String[] { "AddImport.py", "ImportTarget.py" }, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.add.import"), true, true);
  }

  public void testAddImportDoc() {
    doInspectionTest(new String[] { "AddImportDoc.py", "ImportTarget.py" }, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.add.import"), true, true);
  }

  public void testAddImportDocComment() {  // PY-728
    doInspectionTest(new String[] { "AddImportDocComment.py", "ImportTarget.py" }, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.add.import"), true, true);
  }

  public void testQualifyByImport() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean oldPreferFrom = settings.PREFER_FROM_IMPORT;
    boolean oldHighlightUnused = settings.HIGHLIGHT_UNUSED_IMPORTS;
    settings.PREFER_FROM_IMPORT = false;
    settings.HIGHLIGHT_UNUSED_IMPORTS = false;
    try {
      doInspectionTest(new String[]{"QualifyByImport.py", "QualifyByImportFoo.py"}, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.qualify.with.module"), true, true);
    }
    finally {
      settings.PREFER_FROM_IMPORT = oldPreferFrom;
      settings.HIGHLIGHT_UNUSED_IMPORTS = oldHighlightUnused;
    }
  }

  public void testAddToImportFromList() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    boolean oldHighlightUnused = settings.HIGHLIGHT_UNUSED_IMPORTS;
    settings.HIGHLIGHT_UNUSED_IMPORTS = false;
    try {
      doInspectionTest(new String[]{"AddToImportFromList.py", "AddToImportFromFoo.py"}, PyUnresolvedReferencesInspection.class, PyBundle.message("ACT.NAME.use.import"), true, true);
    }
    finally {
      settings.HIGHLIGHT_UNUSED_IMPORTS = oldHighlightUnused;
    }
  }
  // TODO: add a test for multiple variants of above

  // TODO: add tests for stub indexes-based autoimport of unimported somehow.

  public void testAddSelf() {
    doInspectionTest("AddSelf.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.add.parameter.self", "self"), true, true);
  }

  public void testAddCls() {
    doInspectionTest("AddCls.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.add.parameter.self", "cls"), true, true);
  }

  public void testRenameToSelf() {
    doInspectionTest("RenameToSelf.py", PyMethodParametersInspection.class, PyBundle.message("QFIX.rename.parameter.to.$0", "self"), true,
                     true);
  }

  public void testAddFieldFromMethod() {
    doInspectionTest("AddFieldFromMethod.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "y", "A"),
                     true, true);
  }

  public void testAddFieldFromInstance() {
    doInspectionTest("AddFieldFromInstance.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "y", "A"),
                     true, true);
  }

  public void testAddFieldAddConstructor() {
    doInspectionTest("AddFieldAddConstructor.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "x", "B"),
                     true, true);
  }

  public void testAddFieldNewConstructor() {
    doInspectionTest("AddFieldNewConstructor.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.field.$0.to.class.$1", "x", "B"),
                     true, true);
  }

  public void testAddMethodFromInstance() {
    doInspectionTest("AddMethodFromInstance.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"),
                     true, true);
  }

  public void testAddMethodFromMethod() {
    doInspectionTest("AddMethodFromMethod.py", PyUnresolvedReferencesInspection.class, PyBundle.message("QFIX.NAME.add.method.$0.to.class.$1", "y", "A"),
                     true, true);
  }

  public void testRemoveTrailingSemicolon() {
    doInspectionTest("RemoveTrailingSemicolon.py", PyTrailingSemicolonInspection.class, PyBundle.message("QFIX.remove.trailing.semicolon"),
                     true, true);
  }

  public void testDictCreation() {
    doInspectionTest("DictCreation.py", PyDictCreationInspection.class, PyBundle.message("QFIX.dict.creation"), true, true);
  }

  public void testTransformClassicClass() {
    doInspectionTest("TransformClassicClass.py", PyClassicStyleClassInspection.class,
                     PyBundle.message("QFIX.classic.class.transform"), true, true);
  }

  public void testAddGlobalQuickFix() {
    doInspectionTest("AddGlobalStatement.py", PyUnboundLocalVariableInspection.class,
                     PyBundle.message("QFIX.add.global"), true, true);
  }

  public void testAddGlobalExistingQuickFix() {
    doInspectionTest("AddGlobalExistingStatement.py", PyUnboundLocalVariableInspection.class,
                     PyBundle.message("QFIX.add.global"), true, true);
  }

  public void testSimplifyBooleanCheckQuickFix() {
    doInspectionTest("SimplifyBooleanCheck.py", PySimplifyBooleanCheckInspection.class,
                     PyBundle.message("QFIX.simplify"), true, true);
  }

  public void testFromFutureImportQuickFix() {
    doInspectionTest("MoveFromFutureImport.py", PyFromFutureImportInspection.class,
                     PyBundle.message("QFIX.move.from.future.import"), true, true);
  }

  public void testComparisonWithNoneQuickFix() {
    doInspectionTest("ComparisonWithNone.py", PyComparisonWithNoneInspection.class,
                     PyBundle.message("QFIX.replace.equality"), true, true);
  }

  public void testAddClassFix() {
    doInspectionTest("AddClass.py", PyUnresolvedReferencesInspection.class, "Create class 'Xyzzy'", true, true);
  }

  public void testFieldFromUnusedParameter() {  // PY-1398
    doInspectionTest("FieldFromUnusedParameter.py", PyUnusedLocalInspection.class, "Add field 'foo' to class A", true, true);
  }

  public void testFieldFromUnusedParameterKeyword() {  // PY-1602
    doInspectionTest("FieldFromUnusedParameterKeyword.py", PyUnusedLocalInspection.class, "Add field 'foo' to class A", true, true);
  }

  public void testAddFunctionToModule() {  // PY-1602
    doInspectionTest(
      "AddFunctionToModule.py",
      PyUnresolvedReferencesInspection.class,
      PyBundle.message("QFIX.NAME.add.function.$0.to.module.$1", "frob", "AddFunctionToModule.py"),
      true, true
    );
  }

  public void testRedundantParentheses() {  // PY-1470
    String[] testFiles = new String[]{"RedundantParentheses.py"};
    myFixture.enableInspections(PyRedundantParenthesesInspection.class);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, true);
    final IntentionAction intentionAction = myFixture.findSingleIntention(PyBundle.message("QFIX.redundant.parentheses"));
    assertNotNull(intentionAction);
    myFixture.launchAction(intentionAction);
    myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));

  }

  public void testAugmentAssignment() {  // PY-1415
    doInspectionTest("AugmentAssignment.py", PyAugmentAssignmentInspection.class,
                          PyBundle.message("QFIX.augment.assignment"), true, true);
  }

  public void testAugmentAssignmentWithContext() {  // PY-2481
    doInspectionTest("AugmentAssignmentWithContext.py", PyAugmentAssignmentInspection.class,
                          PyBundle.message("QFIX.augment.assignment"), true, true);
  }

  public void testChainedComparisons() {  // PY-1020
    doInspectionTest("ChainedComparisons.py", PyChainedComparisonsInspection.class,
                          PyBundle.message("QFIX.chained.comparison"), true, true);
  }

  public void testStatementEffect() {  // PY-1362, PY-2585
    doInspectionTest("StatementEffect.py", PyStatementEffectInspection.class,
                          PyBundle.message("QFIX.statement.effect"), true, true);
  }

  public void testStatementEffectIntroduceVariable() {  // PY-1265
    doInspectionTest("StatementEffectIntroduceVariable.py", PyStatementEffectInspection.class,
                          PyBundle.message("QFIX.statement.effect.introduce.variable"), true, true);
  }

  public void testUnresolvedWith() {  // PY-2083
    setLanguageLevel(LanguageLevel.PYTHON25);
    doInspectionTest("UnresolvedWith.py", PyUnresolvedReferencesInspection.class,
                          PyBundle.message("QFIX.unresolved.reference.add.future"), true, true);
  }

  public void testUnresolvedRefCreateFunction() {  // PY-2092
    doInspectionTest("UnresolvedRefCreateFunction.py", PyUnresolvedReferencesInspection.class,
                          PyBundle.message("QFIX.unresolved.reference.create.function"), true, true);
  }

  public void testReplaceNotEqOperator() {
    doInspectionTest("ReplaceNotEqOperator.py", PyCompatibilityInspection.class,
                     PyBundle.message("INTN.replace.noteq.operator"), true, true);
  }

  public void testListCreation() {
    doInspectionTest("ListCreation.py", PyListCreationInspection.class,
                     PyBundle.message("QFIX.list.creation"), true, true);
  }

  @Override
  @NonNls
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/inspections/";
  }

  protected void doInspectionTest(@TestDataFile @NonNls String testFileName,
                                  final Class inspectionClass,
                                  @NonNls String quickFixName,
                                  boolean applyFix,
                                  boolean available) {
    doInspectionTest(new String[]{testFileName}, inspectionClass, quickFixName, applyFix, available);
  }

  /**
   * Runs daemon passes and looks for given fix within infos.
   *
   * @param testFiles       names of files to participate; first is used for inspection and then for check by "_after".
   * @param inspectionClass what inspection to run
   * @param quickFixName    how the resulting fix should be named (the human-readable name users see)
   * @param applyFix        true if the fix needs to be applied
   * @param available       true if the fix should be available, false if it should be explicitly not available.
   * @throws Exception
   */
  protected void doInspectionTest(@NonNls String[] testFiles,
                                  final Class inspectionClass,
                                  @NonNls String quickFixName,
                                  boolean applyFix,
                                  boolean available) {
    myFixture.enableInspections(inspectionClass);
    myFixture.configureByFiles(testFiles);
    myFixture.checkHighlighting(true, false, false);
    final IntentionAction intentionAction = myFixture.findSingleIntention(quickFixName);
    if (available) {
      assertNotNull(intentionAction);
      if (applyFix) {
        myFixture.launchAction(intentionAction);

        myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"));
      }
    }
    else {
      assertNull(intentionAction);
    }
  }

  // Turns "name.ext" to "name_insertion.ext"

  @NonNls
  private static String graftBeforeExt(String name, String insertion) {
    int dotpos = name.indexOf('.');
    if (dotpos < 0) dotpos = name.length();
    return name.substring(0, dotpos) + insertion + name.substring(dotpos, name.length());
  }
}
