/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class DomImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomImplUtil");

  private DomImplUtil() {
  }

  public static boolean isTagValueGetter(final JavaMethod method) {
    if (!isGetter(method)) {
      return false;
    }
    if (hasTagValueAnnotation(method)) {
      return true;
    }
    if ("getValue".equals(method.getName())) {
      final JavaMethodSignature signature = method.getSignature();
      final Class<?> declaringClass = method.getDeclaringClass();
      if (signature.findAnnotation(SubTag.class, declaringClass) != null) return false;
      if (signature.findAnnotation(SubTagList.class, declaringClass) != null) return false;
      if (signature.findAnnotation(Convert.class, declaringClass) != null ||
          signature.findAnnotation(Resolve.class, declaringClass) != null) {
        return !ReflectionCache.isAssignable(GenericDomValue.class, method.getReturnType());
      }
      if (ReflectionCache.isAssignable(DomElement.class, method.getReturnType())) return false;
      return true;
    }
    return false;
  }

  private static boolean hasTagValueAnnotation(final JavaMethod method) {
    return method.getAnnotation(TagValue.class) != null;
  }

  public static boolean isGetter(final JavaMethod method) {
    @NonNls final String name = method.getName();
    if (method.getGenericParameterTypes().length != 0) {
      return false;
    }
    final Type returnType = method.getGenericReturnType();
    if (name.startsWith("get")) {
      return returnType != void.class;
    }
    return name.startsWith("is") && DomReflectionUtil.canHaveIsPropertyGetterPrefix(returnType);
  }


  public static boolean isTagValueSetter(final JavaMethod method) {
    boolean setter = method.getName().startsWith("set") && method.getGenericParameterTypes().length == 1 && method.getReturnType() == void.class;
    return setter && (hasTagValueAnnotation(method) || "setValue".equals(method.getName()));
  }

  @Nullable
  public static DomNameStrategy getDomNameStrategy(final Class<?> rawType, boolean isAttribute) {
    Class aClass = null;
    if (isAttribute) {
      NameStrategyForAttributes annotation = DomReflectionUtil.findAnnotationDFS(rawType, NameStrategyForAttributes.class);
      if (annotation != null) {
        aClass = annotation.value();
      }
    }
    if (aClass == null) {
      NameStrategy annotation = DomReflectionUtil.findAnnotationDFS(rawType, NameStrategy.class);
      if (annotation != null) {
        aClass = annotation.value();
      }
    }
    if (aClass != null) {
      if (HyphenNameStrategy.class.equals(aClass)) return DomNameStrategy.HYPHEN_STRATEGY;
      if (JavaNameStrategy.class.equals(aClass)) return DomNameStrategy.JAVA_STRATEGY;
      try {
        return (DomNameStrategy)aClass.newInstance();
      }
      catch (InstantiationException e) {
        LOG.error(e);
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  public static List<XmlTag> findSubTags(@NotNull XmlTag tag, final EvaluatedXmlName name, final DomInvocationHandler handler) {
    return ContainerUtil.findAll(tag.getSubTags(), new Condition<XmlTag>() {
      public boolean value(XmlTag childTag) {
        return isNameSuitable(name, childTag, handler);
      }
    });
  }

  public static boolean isNameSuitable(final XmlName name, final XmlTag tag, @NotNull final DomInvocationHandler handler) {
    return isNameSuitable(handler.createEvaluatedXmlName(name), tag, handler);
  }

  private static boolean isNameSuitable(final EvaluatedXmlName evaluatedXmlName, final XmlTag tag, @NotNull final DomInvocationHandler handler) {
    return isNameSuitable(evaluatedXmlName, tag.getLocalName(), tag.getName(), tag.getNamespace(), handler);
  }

  public static boolean isNameSuitable(final EvaluatedXmlName evaluatedXmlName,
                                        final String localName,
                                        final String qName,
                                        final String namespace,
                                        final DomInvocationHandler handler) {
    final String localName1 = evaluatedXmlName.getXmlName().getLocalName();
    return (localName1.equals(localName) || localName1.equals(qName)) && evaluatedXmlName.isNamespaceAllowed(handler, namespace);
  }

  public static boolean containsTagName(final Set<CollectionChildDescriptionImpl> descriptions, final XmlTag subTag, final DomInvocationHandler handler) {
    return ContainerUtil.find(descriptions, new Condition<CollectionChildDescriptionImpl>() {
      public boolean value(CollectionChildDescriptionImpl description) {
        return isNameSuitable(description.getXmlName(), subTag, handler);
      }
    }) != null;
  }

  @Nullable
  public static String getRootTagName(final PsiFile file) throws IOException {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile instanceof LightVirtualFile && file instanceof XmlFile && FileDocumentManager.getInstance().getCachedDocument(virtualFile) == null) {
      final XmlDocument document = ((XmlFile)file).getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null) {
          return tag.getLocalName();
        }
      }
      return null;
    }

    final NanoXmlUtil.RootTagNameBuilder builder = new NanoXmlUtil.RootTagNameBuilder();
    NanoXmlUtil.parseFile(file, builder);
    return builder.getResult();
  }

  @Nullable
  public static XmlName createXmlName(@NotNull String name, Type type, @Nullable JavaMethod javaMethod) {
    final Class<?> aClass = getErasure(type);
    if (aClass == null) return null;
    String key = getNamespaceKey(aClass);
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
  private static String getNamespaceKey(@NotNull Class<?> type) {
    final Namespace namespace = DomReflectionUtil.findAnnotationDFS(type, Namespace.class);
    return namespace != null ? namespace.value() : null;
  }

  @Nullable
  public static XmlName createXmlName(@NotNull final String name, final JavaMethod method) {
    return createXmlName(name, method.getGenericReturnType(), method);
  }

  public static List<XmlTag> getCustomSubTags(final XmlTag tag, final DomInvocationHandler handler) {
    final DomGenericInfoEx info = handler.getGenericInfo();
    final Set<XmlName> usedNames = new THashSet<XmlName>();
    for (final DomCollectionChildDescription description : info.getCollectionChildrenDescriptions()) {
      usedNames.add(description.getXmlName());
    }
    for (final DomFixedChildDescription description : info.getFixedChildrenDescriptions()) {
      usedNames.add(description.getXmlName());
    }
    return ContainerUtil.findAll(tag.getSubTags(), new Condition<XmlTag>() {
      public boolean value(final XmlTag tag) {
        for (final XmlName name : usedNames) {
          if (isNameSuitable(name, tag, handler)) {
            return false;
          }
        }
        return true;
      }
    });
  }
}
