package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyWithStatementImpl extends PyElementImpl implements PyWithStatement {
  public PyWithStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  protected void acceptPyVisitor(final PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWithStatement(this);
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyWithItem[] items = PsiTreeUtil.getChildrenOfType(this, PyWithItem.class);
    List<PyElement> result = new ArrayList<PyElement>();
    if (items != null) {
      for (PyWithItem item : items) {
        result.add(item.getTargetExpression());
      }
    }
    return result;
  }

  public PsiElement getElementNamed(final String the_name) {
    PyElement named_elt = IterHelper.findName(iterateNames(), the_name);
    return named_elt;
  }

  public boolean mustResolveOutside() {
    return false;
  }
}
