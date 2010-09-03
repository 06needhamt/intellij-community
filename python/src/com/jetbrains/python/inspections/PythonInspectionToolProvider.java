package com.jetbrains.python.inspections;

import com.intellij.codeInspection.InspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: dcheryasov
 * Date: Nov 14, 2008
 */
public class PythonInspectionToolProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
    return new Class[] {
      PyArgumentListInspection.class,
      PyRedeclarationInspection.class,
      PyUnresolvedReferencesInspection.class,
      PyMethodParametersInspection.class,
      PyUnreachableCodeInspection.class,
      PyMethodFirstArgAssignmentInspection.class,
      PyStringFormatInspection.class,
      PyMethodOverridingInspection.class,
      PyInitNewSignatureInspection.class,
      PyTrailingSemicolonInspection.class,
      PyReturnFromInitInspection.class,
      PyUnusedLocalInspection.class,
      PyDeprecatedModulesInspection.class,
      PyDictCreationInspection.class,
      PyExceptClausesOrderInspection.class,
      PyTupleAssignmentBalanceInspection.class,
      PyClassicStyleClassInspection.class,
      PyExceptionInheritInspection.class,
      PyDefaultArgumentInspection.class,
      PyRaisingNewStyleClassInspection.class,
      PyDocstringInspection.class,
      PyUnboundLocalVariableInspection.class,
      PyStatementEffectInspection.class,
      PySimplifyBooleanCheckInspection.class,
      PyFromFutureImportInspection.class,
      PyComparisonWithNoneInspection.class,
      PyStringExceptionInspection.class,
      PySuperArgumentsInspection.class,
      PyByteLiteralInspection.class,
      PyTupleItemAssignmentInspection.class,
      PyCallingNonCallableInspection.class,
      PyPropertyAccessInspection.class,
      PyPropertyDefinitionInspection.class,
      PyInconsistentIndentationInspection.class
    };
  }
}
