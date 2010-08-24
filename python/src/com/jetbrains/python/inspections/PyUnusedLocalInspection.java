package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author oleg
 */
public class PyUnusedLocalInspection extends PyInspection {
  private final ThreadLocal<PyUnusedLocalInspectionVisitor> myLastVisitor = new ThreadLocal<PyUnusedLocalInspectionVisitor>();

  public boolean ignoreTupleUnpacking = true;

  @NotNull
  @Nls
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unused");
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    final PyUnusedLocalInspectionVisitor visitor = new PyUnusedLocalInspectionVisitor(holder, ignoreTupleUnpacking);
    myLastVisitor.set(visitor);
    return visitor;
  }

  @Override
  public void inspectionFinished(LocalInspectionToolSession session) {
    final PyUnusedLocalInspectionVisitor visitor = myLastVisitor.get();
    if (visitor != null) {
      visitor.registerProblems();
      myLastVisitor.remove();
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Ignore variables used in tuple unpacking", this, "ignoreTupleUnpacking");
  }
}
