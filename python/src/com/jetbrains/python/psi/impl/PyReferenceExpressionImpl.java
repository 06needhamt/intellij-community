package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.console.PydevConsoleReference;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.console.pydev.PydevConsoleCommunication;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.refactoring.PyDefUseUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Implements reference expression PSI.
 *
 * @author yole
 */
public class PyReferenceExpressionImpl extends PyElementImpl implements PyReferenceExpression {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.psi.impl.PyReferenceExpressionImpl");

  public PyReferenceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  @NotNull
  public PsiPolyVariantReference getReference() {
    return getReference(PyResolveContext.defaultContext());
  }

  @NotNull
  public PsiPolyVariantReference getReference(PyResolveContext context) {
    final PsiFile file = getContainingFile();
    final PyExpression qualifier = getQualifier();
    if (file != null) {
      // Return special reference
      final PydevConsoleCommunication communication = file.getCopyableUserData(PydevConsoleRunner.CONSOLE_KEY);
      if (communication != null){
        if (qualifier != null) {
          return new PydevConsoleReference(this, communication, qualifier.getText() + ".");
        }
        return new PydevConsoleReference(this, communication, "");
      }
    }

    // Handle import reference
    if (PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class) != null) {
      return new PyImportReferenceImpl(this, context);
    }

    if (qualifier != null) {
      return new PyQualifiedReferenceImpl(this, context);
    }

    return new PyReferenceImpl(this, context);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyReferenceExpression(this);
  }

  @Nullable
  public PyExpression getQualifier() {
    final ASTNode[] nodes = getNode().getChildren(PyElementTypes.EXPRESSIONS);
    return (PyExpression)(nodes.length == 1 ? nodes[0].getPsi() : null);
  }

  @Nullable
  public String getReferencedName() {
    final ASTNode nameElement = getNameElement();
    return nameElement != null ? nameElement.getText() : null;
  }

  @Nullable
  public ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Nullable
  @Override
  public String getName() {
    return getReferencedName();
  }


  @NotNull
  public ResolveResult followAssignmentsChain() {
    PyReferenceExpression seeker = this;
    ResolveResult ret = null;
    SEARCH:
    while (ret == null) {
      ResolveResult[] targets = seeker.getReference().multiResolve(false);
      for (ResolveResult target : targets) {
        PsiElement elt = target.getElement();
        if (elt instanceof PyTargetExpression) {
          PyExpression assigned_from = ((PyTargetExpression)elt).findAssignedValue();
          if (assigned_from instanceof PyReferenceExpression) {
            seeker = (PyReferenceExpression)assigned_from;
            continue SEARCH;
          }
          else if (assigned_from != null) ret = new PsiElementResolveResult(assigned_from);
        }
        else if (ret == null && elt instanceof PyElement) { // remember this result, but a further reference may be the next resolve result
          ret = target;
        }
      }
      // all resolve results checked, reassignment not detected, nothing more to do
      break;
    }
    if (ret == null) {
      ret = new ResolveResult() {
        public PsiElement getElement() {
          return null;
        }

        public boolean isValidResult() {
          return false;
        }
      };
    }
    return ret;
  }

  @Nullable
  public PyQualifiedName asQualifiedName() {
    final List<PyReferenceExpression> components = PyResolveUtil.unwindQualifiers((PyReferenceExpression) this);
    if (components == null) {
      return null;
    }
    return new PyQualifiedName(components);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    // in statements, process only the section in which the original expression was located
    PsiElement parent = getParent();
    if (parent instanceof PyStatement && lastParent == null && PsiTreeUtil.isAncestor(parent, place, true)) {
      return true;
    }

    // never resolve to references within the same assignment statement
    if (getParent() instanceof PyAssignmentStatement) {
      PsiElement placeParent = place.getParent();
      while (placeParent != null && placeParent instanceof PyExpression) {
        placeParent = placeParent.getParent();
      }
      if (placeParent == getParent()) {
        return true;
      }
    }

    if (this == place) return true;
    return processor.execute(this, substitutor);
  }

  @Override
  public String toString() {
    return "PyReferenceExpression: " + getReferencedName();
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    if (!TypeEvalStack.mayEvaluate(this)) {
      return null;
    }
    try {
      if (getQualifier() == null) {
        String name = getReferencedName();
        if (PyNames.NONE.equals(name)) {
          return PyNoneType.INSTANCE;
        }
      }
      else {
        PyType maybe_type = PyUtil.getSpecialAttributeType(this);
        if (maybe_type != null) return maybe_type;
      }
      PyType pyType = getTypeFromProviders(context);
      if (pyType != null) {
        return pyType;
      }

      ResolveResult[] targets = getReference(PyResolveContext.noImplicits()).multiResolve(false);
      if (targets.length == 0) return null;
      PsiElement target = targets[0].getElement();
      if (target == this) {
        return null;
      }
      return getTypeFromTarget(target, context, this);
    }
    finally {
      TypeEvalStack.evaluated(this);
    }
  }

  @Nullable
  private PyType getTypeFromProviders(TypeEvalContext context) {
    for(PyTypeProvider provider: Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      try {
        final PyType type = provider.getReferenceExpressionType(this, context);
        if (type != null) {
          return type;
        }
      }
      catch (AbstractMethodError e) {
        LOG.info(e);
      }
    }
    return null;    
  }

  @Nullable
  public static PyType getTypeFromTarget(final PsiElement target, final TypeEvalContext context, @Nullable PyReferenceExpression anchor) {
    final PyType pyType = getReferenceTypeFromProviders(target, context);
    if (pyType != null) {
      return pyType;
    }
    if (target instanceof PyTargetExpression) {
      final String name = ((PyTargetExpression)target).getName();
      if (PyNames.NONE.equals(name)) {
        return PyNoneType.INSTANCE;
      }
      if (PyNames.TRUE.equals(name) || PyNames.FALSE.equals(name)) {
        return PyBuiltinCache.getInstance(target).getBoolType();
      }
    }
    if (target instanceof PyFile) {
      return new PyModuleType((PyFile) target);
    }
    if (target instanceof PyTargetExpression && context.allowDataFlow() && anchor != null) {
      final ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(anchor, ScopeOwner.class);
      if (scopeOwner != null && scopeOwner == PsiTreeUtil.getParentOfType(target, ScopeOwner.class)) {
        PyAugAssignmentStatement augAssignment = PsiTreeUtil.getParentOfType(anchor, PyAugAssignmentStatement.class);
        final PyElement[] defs = PyDefUseUtil.getLatestDefs(scopeOwner, (PyTargetExpression) target, 
                                                            augAssignment != null ? augAssignment : anchor);
        if (defs.length > 0) {
          PyType type = getTypeIfExpr(defs [0], context);
          for (int i = 1; i < defs.length; i++) {
            type = PyUnionType.union(type, getTypeIfExpr(defs [i], context));
          }
          return type;
        }
      }
    }
    if (target instanceof PyExpression) {
      return context.getType((PyExpression) target);
    }
    if (target instanceof PyClass) {
      return new PyClassType((PyClass) target, true);
    }
    if (target instanceof PsiDirectory) {
      PsiFile file = ((PsiDirectory)target).findFile(PyNames.INIT_DOT_PY);
      if (file != null) return getTypeFromTarget(file, context, anchor);
    }
    return null;
  }

  @Nullable
  private static PyType getTypeIfExpr(PyElement def, TypeEvalContext context) {
    return def instanceof PyExpression ? context.getType((PyExpression)def) : null;
  }

  @Nullable
  public static PyType getReferenceTypeFromProviders(final PsiElement target, TypeEvalContext context) {
    for(PyTypeProvider provider: Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType result = provider.getReferenceType(target, context);
      if (result != null) return result;
    }

    return null;
  }
}
