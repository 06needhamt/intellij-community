package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyTryExceptStatementImpl extends PyPartitionedElementImpl implements PyTryExceptStatement {
  private static final TokenSet EXCEPT_BLOCKS = TokenSet.create(PyElementTypes.EXCEPT_PART);

  public PyTryExceptStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTryExceptStatement(this);
  }

  @NotNull
  public PyExceptPart[] getExceptParts() {
    return childrenToPsi(EXCEPT_BLOCKS, PyExceptPart.EMPTY_ARRAY);
  }

  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }

  @NotNull
  public PyTryPart getTryPart() {
    return (PyTryPart)getPartNotNull(PyElementTypes.TRY_PART);
  }


  public PyFinallyPart getFinallyPart() {
    return (PyFinallyPart)getPart(PyElementTypes.FINALLY_PART);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    /*
    final PyStatementList tryStatementList = getTryPart().getStatementList();
    if (tryStatementList != null && tryStatementList != lastParent && !tryStatementList.processDeclarations(processor, substitutor, null, place)) {
      return false;
    }
    */

    for (PyStatementPart part : /*getExceptParts()*/ getParts()) {
      if (part != lastParent && !part.processDeclarations(processor, substitutor, null, place)) {
        return false;
      }
    }

    /*
    final PyElsePart elsePart = getElsePart();
    if (elsePart != null) {
      PyStatementList elseStatementList = elsePart.getStatementList();
      if (elseStatementList != null && elseStatementList != lastParent) {
        return elseStatementList.processDeclarations(processor, substitutor, null, place);
      }
    }
    */
    return true;
  }
}
