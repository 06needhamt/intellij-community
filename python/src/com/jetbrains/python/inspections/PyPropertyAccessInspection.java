package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Checks that properties are accessed correctly.
 * User: dcheryasov
 * Date: Jun 29, 2010 5:55:52 AM
 */
public class PyPropertyAccessInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.property.access");
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

  public static class Visitor extends PyInspectionVisitor {
    private final HashMap<Pair<PyClass, String>, Property> myPropertyCache = new HashMap<Pair<PyClass, String>, Property>();

    public Visitor(@Nullable final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      super.visitPyReferenceExpression(node);
      checkExpression(node);
    }

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
      super.visitPyTargetExpression(node);
      checkExpression(node);
    }

    private void checkExpression(PyQualifiedExpression node) {
      PyExpression qualifier = node.getQualifier();
      if (qualifier != null) {
        PyType type = qualifier.getType(TypeEvalContext.fast());
        if (type instanceof PyClassType) {
          PyClass cls = ((PyClassType)type).getPyClass();
          String name = node.getName();
          if (cls != null && name != null) {
            final Pair<PyClass, String> key = new Pair<PyClass, String>(cls, name);
            Property property;
            if (myPropertyCache.containsKey(key)) property = myPropertyCache.get(key);
            else property = cls.findProperty(name);
            myPropertyCache.put(key, property); // we store nulls, too, to know that a property does not exist
            if (property != null) {
              AccessDirection dir = AccessDirection.of(node);
              checkAccessor(node, name, dir, property);
              if (dir == AccessDirection.READ && node.getParent() instanceof PyAugAssignmentStatement) {
                checkAccessor(node, name, AccessDirection.WRITE, property);
              }
            }
          }
        }
      }
    }

    private void checkAccessor(PyExpression node, String name, AccessDirection dir, Property property) {
      Maybe<PyFunction> accessor = property.getByDirection(dir);
      if (accessor.isDefined() && accessor.value() == null) {
        String message;
        if (dir == AccessDirection.WRITE) message = PyBundle.message("INSP.property.$0.cant.be.set", name);
        else if (dir == AccessDirection.DELETE) message = PyBundle.message("INSP.property.$0.cant.be.deleted", name);
        else message = PyBundle.message("INSP.property.$0.cant.be.read", name);
        registerProblem(node, message);
      }
    }

  }
}
