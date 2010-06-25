package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.ComparisonWithNoneQuickFix;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexey.Ivanov
 */
public class PyComparisonWithNoneInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.comparison.with.none");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      final PyExpression rightExpression = node.getRightExpression();
      if (rightExpression instanceof PyReferenceExpression) {
        if (PyNames.NONE.equals(rightExpression.getName())) {
          final PyElementType operator = node.getOperator();
          if (operator == PyTokenTypes.EQEQ || operator == PyTokenTypes.NE || operator == PyTokenTypes.NE_OLD) {
            registerProblem(node, "Comparison with None performed with equality operators", new ComparisonWithNoneQuickFix());
          }
        }
      }
    }
  }
}
