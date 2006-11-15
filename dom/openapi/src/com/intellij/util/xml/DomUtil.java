/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ReflectionCache;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

/**
 * @author peter
 */
public class DomUtil {
  public static final TypeVariable<Class<GenericValue>> GENERIC_VALUE_TYPE_VARIABLE = ReflectionCache.getTypeParameters(GenericValue.class)[0];

  private DomUtil() {
  }

  public static Class extractParameterClassFromGenericType(Type type) {
    return getGenericValueParameter(type);
  }

  public static boolean isGenericValueType(Type type) {
    return getGenericValueParameter(type) != null;
  }

  @Nullable
  public static <T extends DomElement> T findByName(@NotNull Collection<T> list, @NonNls @NotNull String name) {
    for (T element: list) {
      String elementName = element.getGenericInfo().getElementName(element);
      if (elementName != null && elementName.equals(name)) {
        return element;
      }
    }
    return null;
  }

  @NotNull
  public static String[] getElementNames(@NotNull Collection<? extends DomElement> list) {
    ArrayList<String> result = new ArrayList<String>(list.size());
    if (list.size() > 0) {
      for (DomElement element: list) {
        String name = element.getGenericInfo().getElementName(element);
        if (name != null) {
          result.add(name);
        }
      }
    }
    return result.toArray(new String[result.size()]);
  }

  @NotNull
  public static List<XmlTag> getElementTags(@NotNull Collection<? extends DomElement> list) {
    ArrayList<XmlTag> result = new ArrayList<XmlTag>(list.size());
    for (DomElement element: list) {
      XmlTag tag = element.getXmlTag();
      if (tag != null) {
        result.add(tag);
      }
    }
    return result;
  }

  @NotNull
  public static XmlTag[] getElementTags(@NotNull DomElement[] list) {
    XmlTag[] result = new XmlTag[list.length];
    int i = 0;
    for (DomElement element: list) {
      XmlTag tag = element.getXmlTag();
      if (tag != null) {
        result[i++] = tag;
      }
    }
    return result;
  }

  @Nullable
  public static List<JavaMethod> getFixedPath(DomElement element) {
    assert element.isValid();
    final LinkedList<JavaMethod> methods = new LinkedList<JavaMethod>();
    while (true) {
      final DomElement parent = element.getParent();
      if (parent instanceof DomFileElement) {
        break;
      }
      final JavaMethod method = getGetterMethod(element, parent);
      if (method == null) {
        return null;
      }
      methods.addFirst(method);
      element = element.getParent();
    }
    return methods;
  }

  @Nullable
  private static JavaMethod getGetterMethod(final DomElement element, final DomElement parent) {
    final String xmlElementName = element.getXmlElementName();
    final String namespace = element.getXmlElementNamespaceKey();
    final DomGenericInfo genericInfo = parent.getGenericInfo();

    if (element instanceof GenericAttributeValue) {
      final DomAttributeChildDescription description = genericInfo.getAttributeChildDescription(xmlElementName, namespace);
      assert description != null;
      return description.getGetterMethod();
    }

    final DomFixedChildDescription description = genericInfo.getFixedChildDescription(xmlElementName, namespace);
    return description != null ? description.getGetterMethod(description.getValues(parent).indexOf(element)) : null;
  }

  public static Class getGenericValueParameter(Type type) {
    return DomReflectionUtil.substituteGenericType(GENERIC_VALUE_TYPE_VARIABLE, type);
  }

  @Nullable
  public static XmlElement getValueElement(GenericDomValue domValue) {
    if (domValue instanceof GenericAttributeValue) {
      return ((GenericAttributeValue)domValue).getXmlAttributeValue();
    } else {
      return domValue.getXmlTag();
    }
  }

  public static List<? extends DomElement> getIdentitySiblings(DomElement element) {
    final Method nameValueMethod = ElementPresentationManager.findNameValueMethod(element.getClass());
    if (nameValueMethod != null) {
      final NameValue nameValue = DomReflectionUtil.findAnnotationDFS(nameValueMethod, NameValue.class);
      if (nameValue == null || nameValue.unique()) {
        final String stringValue = ElementPresentationManager.getElementName(element);
        if (stringValue != null) {
          final DomElement parent = element.getManager().getIdentityScope(element);
          final DomGenericInfo domGenericInfo = parent.getGenericInfo();
          final String tagName = element.getXmlElementName();
          final DomCollectionChildDescription childDescription = domGenericInfo.getCollectionChildDescription(tagName, element.getXmlElementNamespaceKey());
          if (childDescription != null) {
            final ArrayList<DomElement> list = new ArrayList<DomElement>(childDescription.getValues(parent));
            list.remove(element);
            return list;
          }
        }
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public static DomElement findDuplicateNamedValue(DomElement element, String newName) {
    return ElementPresentationManager.findByName(getIdentitySiblings(element), newName);
  }

  public static boolean isAncestor(@NotNull DomElement ancestor, @NotNull DomElement descendant, boolean strict) {
    if (!strict && ancestor.equals(descendant)) return true;
    final DomElement parent = descendant.getParent();
    return parent != null && isAncestor(ancestor, parent, false);
  }

}
