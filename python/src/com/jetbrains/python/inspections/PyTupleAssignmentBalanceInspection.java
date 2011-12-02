package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyTupleAssignmentBalanceInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.incorrect.assignment");
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
      PyExpression lhsExpression = node.getLeftHandSideExpression();
      PsiElement assignedValue = node.getAssignedValue();
      if (assignedValue instanceof PyParenthesizedExpression)     // PY-2659
        assignedValue = ((PyParenthesizedExpression)assignedValue).getContainedExpression();
      if (lhsExpression instanceof PyParenthesizedExpression)     // PY-4360
        lhsExpression = ((PyParenthesizedExpression)lhsExpression).getContainedExpression();
      if (assignedValue instanceof PyReferenceExpression) {          // PY-4357
        assignedValue = ((PyReferenceExpression)assignedValue).followAssignmentsChain(resolveWithoutImplicits()).getElement();
      }
      if (lhsExpression instanceof PyTupleExpression) {
        int valuesLength = 1;
        PyExpression[] elements = ((PyTupleExpression) lhsExpression).getElements();
        if (assignedValue instanceof PySequenceExpression)
          valuesLength = ((PySequenceExpression)assignedValue).getElements().length;
        else if (assignedValue instanceof PyDictLiteralExpression)
          valuesLength = ((PyDictLiteralExpression)assignedValue).getElements().length;

        boolean containsStarExpression = false;
        VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
        if (virtualFile != null && LanguageLevel.forFile(virtualFile).isPy3K()) {
          for (PyExpression target: elements) {
            if (target instanceof PyStarExpression) {
              if (containsStarExpression) {
                registerProblem(target, "Only one starred expression allowed in assignment");
                return;
              }
              containsStarExpression = true;
              ++valuesLength;
            }
          }
        }

        int targetsLength = elements.length;
        if (targetsLength > valuesLength) {
          registerProblem(node.getAssignedValue(), "Need more values to unpack");
        } else if (!containsStarExpression && targetsLength < valuesLength) {
          registerProblem(node.getAssignedValue(), "Too many values to unpack");
        }
      }
    }
  }
}
