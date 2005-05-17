package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 30, 2005
 * Time: 9:15:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertyImpl extends PropertiesElementImpl implements Property {
  public PropertyImpl(final ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Property";
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    ASTNode keyNode = getKeyNode();
    PropertyImpl property = (PropertyImpl)PropertiesElementFactory.createProperty(getProject(), name,"xxx");
    if (keyNode == null) {
      getNode().addChild(property.getKeyNode());
    }
    else {
      getNode().replaceChild(keyNode, property.getKeyNode());
    }
    return this;
  }

  public void setValue(String value) throws IncorrectOperationException {
    StringBuffer escapedName = new StringBuffer(value.length());
    for (int i=0; i<value.length();i++) {
      char c = value.charAt(i);
      if (c == '\n' && (i == 0 || value.charAt(i-1) != '\\')) {
        escapedName.append('\\');
      }
      escapedName.append(c);
    }
    ASTNode node = getValueNode();
    PropertyImpl property = (PropertyImpl)PropertiesElementFactory.createProperty(getProject(), "xxx",escapedName.toString());
    ASTNode valueNode = property.getValueNode();
    if (node == null) {
      if (valueNode != null) {
        getNode().addChild(valueNode);
      }
    }
    else {
      if (valueNode == null) {
        getNode().removeChild(node);
      }
      else {
        getNode().replaceChild(node, valueNode);
      }
    }
  }

  public String getName() {
    return getKey();
  }

  public String getKey() {
    final ASTNode node = getKeyNode();
    if (node == null) {
      return null;
    }
    return node.getText();
  }

  public @Nullable ASTNode getKeyNode() {
    return getNode().findChildByType(PropertiesTokenTypes.KEY_CHARACTERS);
  }
  private @Nullable ASTNode getValueNode() {
    return getNode().findChildByType(PropertiesTokenTypes.VALUE_CHARACTERS);
  }

  public String getValue() {
    final ASTNode node = getNode().findChildByType(PropertiesTokenTypes.VALUE_CHARACTERS);
    if (node == null) {
      return "";
    }
    return node.getText();
  }

  public @Nullable String getKeyValueSeparator() {
    final ASTNode node = getNode().findChildByType(PropertiesTokenTypes.KEY_VALUE_SEPARATOR);
    if (node == null) {
      return null;
    }
    return node.getText();
  }

  public Icon getIcon(int flags) {
    return Icons.PROPERTY_ICON;
  }

  public void delete() throws IncorrectOperationException {
    getParent().getNode().removeChild(getNode());
  }
}
