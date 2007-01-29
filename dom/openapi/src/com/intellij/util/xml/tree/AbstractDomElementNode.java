package com.intellij.util.xml.tree;

import com.intellij.openapi.util.Key;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.DomElement;

import javax.swing.*;
import java.lang.reflect.Type;
import java.util.*;

abstract public class AbstractDomElementNode extends SimpleNode {

  public static final Key<Map<Class, Boolean>> TREE_NODES_HIDERS_KEY = Key.create("TREE_NODES_HIDERS_KEY");

  private final static Comparator<Class> INHERITORS_COMPARATOR = new Comparator<Class>() {
    public int compare(final Class o1, final Class o2) {
      return o1.isAssignableFrom(o2) ? 1 : -1;
    }
  };

  private boolean isExpanded;


  protected AbstractDomElementNode(DomElement element) {
    this(element, null);
  }

  public String toString() {
    return getNodeName();
  }

  protected AbstractDomElementNode(DomElement element, SimpleNode parent) {
    super(element.getManager().getProject(), parent);
  }

  abstract public DomElement getDomElement();

  abstract public String getNodeName();

  abstract public String getTagName();


  public Icon getNodeIcon() {
    return getDomElement().getPresentation().getIcon();
  }

  protected String getPropertyName() {
    return getDomElement().getPresentation().getTypeName();
  }

  protected boolean shouldBeShown(final Type type) {
    final Map<Class, Boolean> hiders = getDomElement().getRoot().getFile().getUserData(TREE_NODES_HIDERS_KEY);
    if (type == null || hiders == null || hiders.size() == 0) return true;

    final Class aClass = ReflectionUtil.getRawType(type);

    List<Class> allParents = new ArrayList<Class>();
    for (Map.Entry<Class, Boolean> entry : hiders.entrySet()) {
      if (entry.getKey().isAssignableFrom(aClass)) {
        allParents.add(entry.getKey());
      }
    }
    if (allParents.size() == 0) return false;

    Collections.sort(allParents, INHERITORS_COMPARATOR);

    return hiders.get(allParents.get(0)).booleanValue();

  }

  protected SimpleTextAttributes getSimpleAttributes(final int style) {
    return new SimpleTextAttributes(style, SimpleTextAttributes.REGULAR_ATTRIBUTES.getFgColor());
  }

  protected SimpleTextAttributes getWavedAttributes(final int style) {
    return new SimpleTextAttributes(style | SimpleTextAttributes.STYLE_WAVED, SimpleTextAttributes.REGULAR_ATTRIBUTES.getFgColor(), SimpleTextAttributes.ERROR_ATTRIBUTES.getFgColor());
  }
  public boolean isExpanded() {
    return isExpanded;
  }

  public void setExpanded(final boolean expanded) {
    isExpanded = expanded;
  }
}
