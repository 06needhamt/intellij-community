/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.Namespace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;

/**
 * @author peter
*/
public class XmlName {
  private final String myLocalName;
  private final String myNamespaceKey;

  public XmlName(@NotNull final String localName) {
    this(localName, null);
  }

  public XmlName(@NotNull final String localName, @Nullable final String namespaceKey) {
    myLocalName = localName;
    myNamespaceKey = namespaceKey;
  }

  @NotNull
  public final String getLocalName() {
    return myLocalName;
  }

  @Nullable
  public final String getNamespaceKey() {
    return myNamespaceKey;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final XmlName xmlName = (XmlName)o;

    if (!myLocalName.equals(xmlName.myLocalName)) return false;
    if (myNamespaceKey != null ? !myNamespaceKey.equals(xmlName.myNamespaceKey) : xmlName.myNamespaceKey != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myLocalName.hashCode();
    result = 31 * result + (myNamespaceKey != null ? myNamespaceKey.hashCode() : 0);
    return result;
  }

  @NotNull
  public EvaluatedXmlName createEvaluatedXmlName(@Nullable DomInvocationHandler parent) {
    String namespaceKey = myNamespaceKey;
    if (namespaceKey == null && parent != null) {
      namespaceKey = parent.getXmlName().getNamespaceKey();
    }
    return new EvaluatedXmlName(this, namespaceKey);
  }

  @Nullable
  public static XmlName create(@NotNull String name, Type type, @Nullable JavaMethod javaMethod) {
    final Class<?> type1 = getErasure(type);
    if (type1 == null) return null;
    String key = getNamespaceKey(type1);
    if (key == null && javaMethod != null) {
      for (final Method method : javaMethod.getSignature().getAllMethods(javaMethod.getDeclaringClass())) {
        final String key1 = getNamespaceKey(method.getDeclaringClass());
        if (key1 != null) {
          key = key1;
        }
      }
    }
    return new XmlName(name, key);
  }

  @Nullable
  private static Class<?> getErasure(Type type) {
    if (type instanceof Class) {
      return (Class)type;
    }
    if (type instanceof ParameterizedType) {
      return getErasure(((ParameterizedType)type).getRawType());
    }
    if (type instanceof TypeVariable) {
      for (final Type bound : ((TypeVariable)type).getBounds()) {
        final Class<?> aClass = getErasure(bound);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    if (type instanceof WildcardType) {
      final WildcardType wildcardType = (WildcardType)type;
      for (final Type bound : wildcardType.getUpperBounds()) {
        final Class<?> aClass = getErasure(bound);
        if (aClass != null) {
          return aClass;
        }
      }
    }
    return null;
  }


  @Nullable
  public static String getNamespaceKey(@NotNull Class<?> type) {
    final Namespace namespace = DomReflectionUtil.findAnnotationDFS(type, Namespace.class);
    return namespace != null ? namespace.value() : null;
  }

  @Nullable
  public static XmlName create(@NotNull final String name, final JavaMethod method) {
    return create(name, method.getGenericReturnType(), method);
  }
}
