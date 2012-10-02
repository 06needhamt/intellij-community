package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.ListCreationQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User :catherine
 */
public class PyListCreationInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.list.creation");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      if (node.getAssignedValue() instanceof PyListLiteralExpression) {
        if (node.getTargets().length != 1) {
          return;
        }
        final PyExpression target = node.getTargets()[0];
        String name = target.getName();
        if (name == null) {
          return;
        }
        PyExpression statement = null;
        PyStatement expressionStatement = PsiTreeUtil.getNextSiblingOfType(node, PyStatement.class);
        if (!(expressionStatement instanceof PyExpressionStatement))
          return;
        statement = ((PyExpressionStatement)expressionStatement).getExpression();
        ListCreationQuickFix quickFix = null;
        boolean availableFix = false;
loop:
        while (statement instanceof PyCallExpression) {
          PyCallExpression callExpression = (PyCallExpression)statement;
          PyExpression callee = callExpression.getCallee();
          if (callee instanceof PyQualifiedExpression) {
            PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
            String funcName = ((PyQualifiedExpression)callee).getReferencedName();
            if (qualifier != null && qualifier.getText().equals(name) && "append".equals(funcName)) {
              PyArgumentList argList = callExpression.getArgumentList();
              if (argList != null) {
                for (PyExpression argument : argList.getArguments()) {
                  if (argument.getText().equals(name))
                    break loop;
                  if (!availableFix) {
                    quickFix = new ListCreationQuickFix(node);
                    availableFix = true;
                  }
                }
                if(availableFix)
                  quickFix.addStatement((PyExpressionStatement)expressionStatement);
              }
            }
          }
          if (quickFix == null) {
            return;
          }
          expressionStatement = PsiTreeUtil.getNextSiblingOfType(expressionStatement, PyStatement.class);
          if (expressionStatement instanceof PyExpressionStatement)
            statement = ((PyExpressionStatement)expressionStatement).getExpression();
          else
            statement = null;
        }
        
        if (availableFix) {
          registerProblem(node, "This list creation could be rewritten as a list literal", quickFix);
        }
      }
    }
  }
}
