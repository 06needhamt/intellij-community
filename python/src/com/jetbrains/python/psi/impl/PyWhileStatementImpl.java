package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyWhileStatementImpl extends PyPartitionedElementImpl implements PyWhileStatement {
  public PyWhileStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyWhileStatement(this);
  }

  @NotNull
  public PyWhilePart getWhilePart() {
    return (PyWhilePart)getPartNotNull(PyElementTypes.WHILE_PART);
  }

  public PyElsePart getElsePart() {
    return (PyElsePart)getPart(PyElementTypes.ELSE_PART);
  }

  @Override public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                                 @NotNull ResolveState substitutor,
                                                 PsiElement lastParent,
                                                 @NotNull PsiElement place)
    {
      if (lastParent != null) return true;

      for (PyStatementPart part : getParts()) {
        PyStatementList stmtList = part.getStatementList();
        if (stmtList != null) {
            return stmtList.processDeclarations(processor, substitutor, null, place);
        }
      }
      return true;
    }
}
