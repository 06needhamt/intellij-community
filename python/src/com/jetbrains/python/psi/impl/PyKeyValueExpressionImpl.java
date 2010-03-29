package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyKeyValueExpressionImpl extends PyElementImpl implements PyKeyValueExpression {
  public PyKeyValueExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType() {
    return null;
  }

  @NotNull
  public PyExpression getKey() {
    return (PyExpression)getNode().getFirstChildNode().getPsi();
  }

  @Nullable
  public PyExpression getValue() {
    return PsiTreeUtil.getNextSiblingOfType(getKey(), PyExpression.class);
  }
}
