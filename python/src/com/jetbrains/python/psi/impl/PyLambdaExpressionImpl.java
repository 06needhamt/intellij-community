package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyLambdaExpressionImpl extends PyElementImpl implements PyLambdaExpression {
  public PyLambdaExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyLambdaExpression(this);
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    return null;
  }

  public PyParameterList getParameterList() {
    return childToPsiNotNull(PyElementTypes.PARAMETER_LIST_SET, 0);
  }

  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    // TODO: move it to PyParamList
    PyParameter[] parameters = getParameterList().getParameters();
    return processParamLayer(parameters, processor, state, lastParent);
  }

  private boolean processParamLayer(@NotNull final PyParameter[] parameters,
                                    @NotNull final PsiScopeProcessor processor,
                                    @NotNull final ResolveState state,
                                    final PsiElement lastParent) {
    for (PyParameter param : parameters) {
      if (param == lastParent) continue;
      PyTupleParameter t_param = param.getAsTuple();
      if (t_param != null) {
        PyParameter[] nested_params = t_param.getContents();
        if (!processParamLayer(nested_params, processor, state, lastParent)) return false;
      }
      else if (!processor.execute(param, state)) return false;
    }
    return true;
  }
}
