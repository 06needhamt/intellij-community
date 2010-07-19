package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.run.RunnableScriptFilter;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonUnitTestRunnableScriptFilter implements RunnableScriptFilter {
  public boolean isRunnableScript(PsiFile script, @NotNull Module module, Location location) {
    return script instanceof PyFile && PythonUnitTestUtil.getTestCaseClassesFromFile((PyFile) script).size() > 0 && !isIfNameMain(location);
  }

  public static boolean isIfNameMain(Location location) {
    PsiElement element = location.getPsiElement();
    while (true) {
      final PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class);
      if (ifStatement == null) {
        break;
      }
      element = ifStatement;
    }
    if (element instanceof PyIfStatement) {
      PyIfStatement ifStatement = (PyIfStatement)element;
      final PyExpression condition = ifStatement.getIfPart().getCondition();
      if (condition instanceof PyBinaryExpression) {
        PyBinaryExpression binaryExpression = (PyBinaryExpression)condition;
        final PyExpression rhs = binaryExpression.getRightExpression();
        return binaryExpression.getOperator() == PyTokenTypes.EQEQ &&
               binaryExpression.getLeftExpression().getText().equals("__name__") &&
               rhs != null && rhs.getText().contains("__main__");
      }
    }
    return false;
  }
}
