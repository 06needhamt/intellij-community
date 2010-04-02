package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.TransformClassicClassQuickFix;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 28.02.2010
 * Time: 17:41:18
 */
public class PyClassicStyleClassInspection extends LocalInspectionTool {
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
    return PyBundle.message("INSP.NAME.classic.class.usage");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyClassicStyleClassInspection";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  private static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyClass(PyClass node) {
      if (!node.isNewStyleClass()) {
        PyExpression[] superClassExpressions = node.getSuperClassExpressions();
        if (superClassExpressions.length == 0) {
          registerProblem(node, "Old-style class", new TransformClassicClassQuickFix());
        } else {
          registerProblem(node, "Old-style class, because all classes from whom it inherited are old-style",
                          new TransformClassicClassQuickFix());
        }
      }
    }

  }
}
