/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 11:17:07
 * To change this template use File | Settings | File Templates.
 */
public class PyBinaryExpressionImpl extends PyElementImpl implements PyBinaryExpression {

  public PyBinaryExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyBinaryExpression(this);
  }

  @PsiCached
  public PyExpression getLeftExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  @PsiCached
  public PyExpression getRightExpression() {
    return PsiTreeUtil.getNextSiblingOfType(getLeftExpression(), PyExpression.class);
  }

  @PsiCached
  public PyElementType getOperator() {
    ASTNode node = getNode();
    if (node != null) {
      final ASTNode child = node.findChildByType(PyElementTypes.BINARY_OPS);
      if (child != null) return (PyElementType)child.getElementType();
    }
    return null;
  }

  @PsiCached
  public boolean isOperator(String chars) {
    ASTNode child = getNode().getFirstChildNode();
    StringBuffer buf = new StringBuffer();
    while (child != null) {
      IElementType elType = child.getElementType();
      if (elType instanceof PyElementType && PyElementTypes.BINARY_OPS.contains(elType)) {
        buf.append(child.getText());
      }
      child = child.getTreeNext();
    }
    return buf.toString().equals(chars);
  }

  public PyExpression getOppositeExpression(PyExpression expression) throws IllegalArgumentException {
    PyExpression right = getRightExpression();
    PyExpression left = getLeftExpression();
    if (expression.equals(left)) {
      return right;
    }
    if (expression.equals(right)) {
      return left;
    }
    throw new IllegalArgumentException("expression " + expression + " is neither left exp or right exp");
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    PyExpression left = getLeftExpression();
    PyExpression right = getRightExpression();
    if (left == child.getPsi()) {
      replace(right);
    }
    else if (right == child.getPsi()) {
      replace(left);
    }
    else {
      throw new IncorrectOperationException("Element " + child.getPsi() + " is neither left expression or right expression");
    }
  }

  public PyType getType() {
    return null;
  }
}
