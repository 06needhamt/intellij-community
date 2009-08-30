/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;

/**
 * @author ven
 */
public class AnnotationElement extends CompositeElement implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.AnnotationElement");

  public AnnotationElement() {
    super(ANNOTATION);
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);

    IElementType i = child.getElementType();
    if (i == ANNOTATION_PARAMETER_LIST) {
      return ChildRole.PARAMETER_LIST;
    }
    else if (i == JAVA_CODE_REFERENCE) {
      return ChildRole.CLASS_REFERENCE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.PARAMETER_LIST:
        return findChildByType(ANNOTATION_PARAMETER_LIST);

      case ChildRole.CLASS_REFERENCE:
        return findChildByType(JAVA_CODE_REFERENCE);
    }
  }
}
