package com.jetbrains.python.validation;

import com.intellij.lang.annotation.Annotation;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.PythonDocStringFinder;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * Highlights doc strings in classes, functions, and files.
 */
public class DocStringAnnotator extends PyAnnotator {

  @Override
  public void visitPyFile(final PyFile node) {
    annotateDocStringStmt(PythonDocStringFinder.find(node));
  }

  @Override
  public void visitPyFunction(final PyFunction node) {
    annotateDocStringStmt(PythonDocStringFinder.find(node.getStatementList()));
  }

  @Override
  public void visitPyClass(final PyClass node) {
    annotateDocStringStmt(PythonDocStringFinder.find(node.getStatementList()));
  }

  private void annotateDocStringStmt(final PyStringLiteralExpression stmt) {
    if (stmt != null) {
      if (PydevConsoleRunner.isInPydevConsole(stmt)){
        return;
      }
      Annotation ann = getHolder().createInfoAnnotation(stmt, null);
      ann.setTextAttributes(PyHighlighter.PY_DOC_COMMENT);
    }
  }
}
