package com.jetbrains.python;

import com.jetbrains.python.refactoring.*;
import com.jetbrains.python.refactoring.classes.PyExtractSuperclassTest;
import com.jetbrains.python.refactoring.classes.PyPullUpTest;
import com.jetbrains.python.refactoring.classes.PyPushDownTest;
import junit.framework.TestSuite;

/**
 * A suite to explicitly include all actual tests, in fail-fast order.
 * User: dcheryasov
 * Date: Nov 19, 2009 1:23:22 AM
 */
public class PythonAllTestsSuite {

  public static final Class[] tests = {
    PythonLexerTest.class,
    PyStringLiteralLexerTest.class,
    PyEncodingTest.class,
    PythonParsingTest.class,
    PyStringLiteralTest.class,
    PyIndentTest.class,
    PyStatementPartsTest.class,
    PythonHighlightingTest.class,
    PyStubsTest.class,
    PyResolveTest.class,
    Py3ResolveTest.class,
    PyMultiFileResolveTest.class,
    PyResolveCalleeTest.class,
    PyAssignmentMappingTest.class,
    PythonCompletionTest.class,
    PyInheritorsSearchTest.class,
    PyParameterInfoTest.class,
    PyDecoratorTest.class,
    PyQuickDocTest.class,
    PythonInspectionsTest.class,
    PythonDemorganLawIntentionTest.class,
    PyQuickFixTest.class,
    PyIntentionTest.class,
    PySelectWordTest.class,
    PyEditingTest.class,
    PySurroundWithTest.class,
    PyEditingTest.class,
    PyFormatterTest.class,
    PyRenameTest.class,
    PyExtractMethodTest.class,
    PyPullUpTest.class,
    PyPushDownTest.class,
    PyExtractSuperclassTest.class,
    PyInlineLocalTest.class,
    PyAutoUnindentTest.class,
    PyFindUsagesTest.class,
    PyTypeTest.class,
    PyControlFlowBuilderTest.class,
    PyCodeFragmentTest.class,
    PyOptimizeImportsTest.class,
    PySmartEnterTest.class,
    PyStatementMoverTest.class,
    PyIntroduceVariableTest.class,
    PyIntroduceFieldTest.class,
    PyClassNameCompletionTest.class,
    PySuppressInspectionsTest.class,
    PyPropertyTestSuite.PyClassicPropertyTest.class,
    PyPropertyTestSuite.PyDecoratedPropertyTest.class,
    PythonRunConfigurationTest.class
  };

  public static TestSuite suite() {
    return new TestSuite(tests);
  }

  
}
