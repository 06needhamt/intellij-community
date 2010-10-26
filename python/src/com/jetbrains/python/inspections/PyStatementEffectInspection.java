package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyStatementEffectInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.statement.effect");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  @Override
  protected boolean isSuppressForCodeFragment() {
    return true;
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyExpressionStatement(final PyExpressionStatement node) {
      if (PydevConsoleRunner.isInPydevConsole(node)) {
        return;
      }
      final PyExpression expression = node.getExpression();
      if (hasEffect(expression)) return;

      final PyTryPart tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart.class);
      if (tryPart != null) {
        final PyStatementList statementList = tryPart.getStatementList();
        if (statementList == null) {
          return;
        }
        if (statementList.getStatements().length == 1 && statementList.getStatements()[0] == node) {
          return;
        }
      }
      registerProblem(expression, "Statement seems to have no effect");
    }

    private boolean hasEffect(@Nullable PyExpression expression) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PyCallExpression || expression instanceof PyYieldExpression) {
        return true;
      }

      if (expression instanceof PyStringLiteralExpression) {
        final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(expression, PyDocStringOwner.class);
        if (docStringOwner != null) {
          if (docStringOwner.getDocStringExpression() == expression) {
            return true;
          }
        }
      }
      else if (expression instanceof PyListCompExpression) {
        if (hasEffect(((PyListCompExpression)expression).getResultExpression())) {
          return true;
        }
      }
      else if (expression instanceof PyBinaryExpression) {
        PyBinaryExpression binary = (PyBinaryExpression)expression;
        final PyElementType operator = binary.getOperator();
        String method = operator == null ? null : operator.getSpecialMethodName();
        if (method != null) {
          // maybe the op is overridden and may produce side effects, like cout << "hello"
          PyType type = myTypeEvalContext.getType(binary.getLeftExpression());
          if (type != null &&
              !type.isBuiltin() &&
              type.resolveMember(method, AccessDirection.READ, PyResolveContext.defaultContext()) != null) {
            return true;
          }
          final PyExpression rhs = binary.getRightExpression();
          if (rhs != null) {
            type = myTypeEvalContext.getType(rhs);
            if (type != null) {
              String rmethod = "__r" + method.substring(2); // __add__ -> __radd__
              if (!type.isBuiltin() && type.resolveMember(rmethod, AccessDirection.READ, PyResolveContext.defaultContext()) != null) {
                return true;
              }
            }
          }
        }
      }
      else if (expression instanceof PyConditionalExpression) {
        PyConditionalExpression conditionalExpression = (PyConditionalExpression)expression;
        return hasEffect(conditionalExpression.getTruePart()) || hasEffect(conditionalExpression.getFalsePart());
      }
      return false;
    }
  }
}
