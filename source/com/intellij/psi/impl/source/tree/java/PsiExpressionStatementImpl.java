package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiExpressionStatementImpl extends CompositePsiElement implements PsiExpressionStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiExpressionStatementImpl");

  public PsiExpressionStatementImpl() {
    super(JavaElementType.EXPRESSION_STATEMENT);
  }

  @NotNull
  public PsiExpression getExpression() {
    PsiExpression expression = (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(TreeUtil.findChild(this, ElementType.EXPRESSION_BIT_SET));
    if (expression != null) return expression;
    LOG.error("Illegal PSI. Children: " + DebugUtil.psiToString(this, false));
    return null;
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.EXPRESSION:
        return TreeUtil.findChild(this, ElementType.EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.EXPRESSION;
      }
      return ChildRoleBase.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitExpressionStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiExpressionStatement";
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getChildRole(child) == ChildRole.EXPRESSION) {
      getTreeParent().deleteChildInternal(this);
    }
    else {
      super.deleteChildInternal(child);
    }
  }
}
