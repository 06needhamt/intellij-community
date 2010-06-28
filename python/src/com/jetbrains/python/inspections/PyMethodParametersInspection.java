package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.AddSelfQuickFix;
import com.jetbrains.python.actions.RenameParameterQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.jetbrains.python.psi.PyFunction.Flag.CLASSMETHOD;
import static com.jetbrains.python.psi.PyFunction.Flag.STATICMETHOD;

/**
 * Looks for the 'self' or its equivalents.
 * @author dcheryasov
 */
public class PyMethodParametersInspection extends PyInspection {
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.problematic.first.parameter");
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.INFO;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyFunction(final PyFunction node) {
      PsiElement cap = PyUtil.getConcealingParent(node);
      if (cap instanceof PyClass) {
        PyParameterList plist = node.getParameterList();
        PyParameter[] params = plist.getParameters();
        Set<PyFunction.Flag> flags = PyUtil.detectDecorationsAndWrappersOf(node);
        boolean is_special_metaclass_method = false;
        PyClass type_cls = PyBuiltinCache.getInstance(node).getClass("type");
        for (PyClass ancestor_cls : ((PyClass)cap).iterateAncestors()) {
          if (ancestor_cls == type_cls) {
            is_special_metaclass_method = true;
            break;
          }
        }
        final String method_name = node.getName();
        is_special_metaclass_method &= PyNames.INIT.equals(method_name) || "__call__".equals(method_name);
        final boolean is_staticmethod = flags.contains(STATICMETHOD);
        if (params.length == 0) {
          // check for "staticmetod"
          if (is_staticmethod) return; // no params may be fine
          // check actual param list
          ASTNode name_node = node.getNameNode();
          if (name_node != null) {
            PsiElement open_paren = plist.getFirstChild();
            PsiElement close_paren = plist.getLastChild();
            if (
              open_paren != null && close_paren != null &&
              "(".equals(open_paren.getText()) && ")".equals(close_paren.getText())
            ) {
              String paramName = flags.contains(CLASSMETHOD) || is_special_metaclass_method ? "cls" : "self";
              registerProblem(
                plist, PyBundle.message("INSP.must.have.first.parameter", paramName),
                ProblemHighlightType.GENERIC_ERROR, null, new AddSelfQuickFix(paramName)
              );
            }
          }
        }
        else {
          PyNamedParameter first_param = params[0].getAsNamed();
          if (first_param != null) {
            String pname = first_param.getText();
            // every dup, swap, drop, or dup+drop of "self"
            @NonNls String[] mangled = {"eslf", "sself", "elf", "felf", "slef", "seelf", "slf", "sslf", "sefl", "sellf", "sef", "seef"};
            for (String typo : mangled) {
              if (typo.equals(pname)) {
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyBundle.message("INSP.probably.mistyped.self"),
                  new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
                );
                return;
              }
            }
            // TODO: check for style settings
            if (flags.contains(CLASSMETHOD) || is_special_metaclass_method) {
              String CLS = "cls";
              if (!CLS.equals(pname)) {
                registerProblem(
                  PyUtil.sure(params[0].getNode()).getPsi(),
                  PyBundle.message("INSP.usually.named.cls"),
                  new RenameParameterQuickFix(CLS)
                );
              }
            }
            else if (!is_staticmethod && !first_param.isPositionalContainer() && !PyNames.CANONICAL_SELF.equals(pname)) {
              registerProblem(
                PyUtil.sure(params[0].getNode()).getPsi(),
                PyBundle.message("INSP.usually.named.self"),
                new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
              );
            }
          }
          else { // the unusual case of a method with first tuple param
            if (!is_staticmethod) {
              registerProblem(plist, PyBundle.message("INSP.first.param.must.not.be.tuple"));
            }
          }
        }
      }
    }
  }

}
