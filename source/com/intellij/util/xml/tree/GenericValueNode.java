package com.intellij.util.xml.tree;

import jetbrains.fabrique.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.DomElement;
import com.intellij.ui.SimpleTextAttributes;

public class GenericValueNode extends AbstractDomElementNode {
  protected GenericDomValue myModelElement;
  protected String myTagName;

  public GenericValueNode(final GenericDomValue modelElement, SimpleNode parent) {
    super(parent);

    myModelElement = modelElement;
    myTagName = modelElement.getXmlElementName();
   }

  public String getNodeName() {
    return getPropertyName();
  }

  public String getTagName() {
    return myTagName;
  }

  public DomElement getDomElement() {
    return myModelElement;
  }

  protected boolean doUpdate() {
    setUniformIcon(getNodeIcon());
    clearColoredText();
    if (myModelElement.getStringValue() != null) {
      addColoredFragment(getNodeName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment("=", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment("\"" + myModelElement.getStringValue() + "\"", SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
    } else {
      addColoredFragment(getNodeName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }

    return super.doUpdate();
  }

  public SimpleNode[] getChildren() {
    return NO_CHILDREN;
  }

  public Object[] getEqualityObjects() {
    return NONE;
  }
}
