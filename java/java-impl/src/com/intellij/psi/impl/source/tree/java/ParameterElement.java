package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;

public class ParameterElement extends CompositeElement{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ParameterElement");

  public ParameterElement() {
    super(Constants.PARAMETER);
  }

  protected ParameterElement(IElementType type) {
    super(type);
  }

  public int getTextOffset() {
    return findChildByRole(ChildRole.NAME).getStartOffset();
  }

  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.MODIFIER_LIST:
        return findChildByType(JavaElementType.MODIFIER_LIST);

      case ChildRole.NAME:
        return findChildByType(JavaTokenType.IDENTIFIER);

      case ChildRole.TYPE:
        return findChildByType(JavaElementType.TYPE);

    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaElementType.MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == JavaElementType.TYPE) {
      return getChildRole(child, ChildRole.TYPE);
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}

