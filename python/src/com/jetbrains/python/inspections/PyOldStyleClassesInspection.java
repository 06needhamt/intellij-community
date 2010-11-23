package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.AugmentedAssignmentQuickFix;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * Inspection to detect occurrences of new-style class features in old-style classes
 */
public class PyOldStyleClassesInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.oldstyle.class");
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
    public void visitPyClass(final PyClass node) {
      if (!node.isNewStyleClass()) {
        for (PyTargetExpression attr : node.getClassAttributes()) {
          if ("__slots__".equals(attr.getName())) {
            registerProblem(attr, "Old-style class contains __slots__ definition");
          }
        }
        for (PyFunction attr : node.getMethods()) {
          if ("__getattribute__".equals(attr.getName())) {
            registerProblem(attr, "Old-style class contains __getattribute__ definition");
          }
        }
      }
    }
  }
}
