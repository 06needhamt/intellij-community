package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.SimplifyBooleanCheckQuickFix;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   17.03.2010
 * Time:   18:03:35
 */
public class PySimplifyBooleanCheckInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.check.can.be.simplified");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PySimplifyBooleanCheckInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
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
      final PyElementType operator = node.getOperator();
      if (node.getRightExpression() == null) {
        return;
      }
      final String leftExpressionText = node.getLeftExpression().getText();
      final String rightExpressionText = node.getRightExpression().getText();
      if ("True".equals(leftExpressionText) ||
          "False".equals(leftExpressionText) ||
          "True".equals(rightExpressionText) ||
          "False".equals(rightExpressionText)) {
        if (TokenSet.create(PyTokenTypes.EQEQ, PyTokenTypes.IS_KEYWORD, PyTokenTypes.NE, PyTokenTypes.NE_OLD).contains(operator)) {
          registerProblem(node);
        }
      }
    }

    private void registerProblem(PyBinaryExpression binaryExpression) {
      registerProblem(binaryExpression, PyBundle.message("INSP.expression.can.be.simplified"), new SimplifyBooleanCheckQuickFix());
    }
  }
}
