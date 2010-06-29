package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class PyCallingNonCallableInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Trying to call a non-callable object";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      super.visitPyCallExpression(node);
      PyExpression callee = node.getCallee();
      if (callee != null) {
        PyType calleeType = callee.getType(TypeEvalContext.fast());
        if (calleeType != null && calleeType instanceof PyClassType) {
          PyClassType classType = (PyClassType) calleeType;
          if (isMethodType(node, classType)) {
            return;
          }
          if (!classType.isDefinition()) {
            final List<? extends PsiElement> calls = classType.resolveMember("__call__", AccessDirection.READ);
            if (calls == null || calls.size() == 0) {
              PyClass pyClass = classType.getPyClass();
              if (pyClass != null) {
                registerProblem(node, "'" + pyClass.getName() + "' object is not callable");
              }
            }
          }
        }
      }
    }

    private static boolean isMethodType(PyCallExpression node, PyClassType classType) {
      final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(node);
      return classType.equals(builtinCache.getClassMethodType()) || classType.equals(builtinCache.getStaticMethodType());
    }
  }
}
