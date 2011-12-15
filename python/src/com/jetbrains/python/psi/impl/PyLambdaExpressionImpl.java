package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @NotNull
  public PyParameterList getParameterList() {
    return childToPsiNotNull(PyElementTypes.PARAMETER_LIST_SET, 0);
  }

  public PyType getReturnType(TypeEvalContext context, @Nullable PyReferenceExpression callSite) {
    final PyExpression body = getBody();
    if (body != null) return body.getType(context);
    else return null;
  }

  @Nullable
  public PyExpression getBody() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  public PyFunction asMethod() {
    return null; // we're never a method
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    ControlFlowCache.clear(this);
  }
}
