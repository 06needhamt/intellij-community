package com.jetbrains.python;

import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPathEvaluator;

import java.util.HashSet;

/**
 * @author yole
 */
public class PyPathEvaluatorTest extends PyTestCase {
  public void testDirName() {
    assertEquals("/foo/bar", doEvaluate("os.path.dirname(__file__)", "/foo/bar/baz.py"));
  }

  public void testOsPathJoin() {
    assertEquals("/foo/bar/db.sqlite3", doEvaluate("os.path.join(__file__, 'db.sqlite3'", "/foo/bar"));
  }

  public void testReplace() {
    assertEquals("/foo/Bar/Baz.py", doEvaluate("__file__.replace('b', 'B')", "/foo/bar/baz.py"));
  }

  public void testConstants() {
    myFixture.configureByText(PythonFileType.INSTANCE, "ROOT_PATH = '/foo'\nTEMPLATES_DIR = os.path.join(ROOT_PATH, 'templates')");
    PyFile file = (PyFile) myFixture.getFile();
    final PyTargetExpression expression = file.findTopLevelAttribute("TEMPLATES_DIR");
    final PyExpression value = expression.findAssignedValue();
    final String result = FileUtil.toSystemIndependentName(PyPathEvaluator.evaluate(value, "", new HashSet<PyExpression>()));
    assertEquals(result, "/foo/templates");
  }

  public void testParDir() {
    assertEquals("/foo/subfolder/../bar.py", doEvaluate("os.path.abspath(os.path.join(os.path.join('/foo/subfolder',  os.path.pardir, 'bar.py')))", "/foo/bar.py"));
  }

  private String doEvaluate(final String text, final String file) {
    final PyExpression expression = PyElementGenerator.getInstance(myFixture.getProject()).createExpressionFromText(text);
    return FileUtil.toSystemIndependentName(PyPathEvaluator.evaluate(expression, file, new HashSet<PyExpression>()));
  }
}
