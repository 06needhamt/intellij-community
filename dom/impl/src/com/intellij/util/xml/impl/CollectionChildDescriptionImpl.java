/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public class CollectionChildDescriptionImpl extends DomChildDescriptionImpl implements DomCollectionChildDescription {
  private final JavaMethod myGetterMethod;
  private final JavaMethod myAdderMethod;
  private final JavaMethod myIndexedAdderMethod;
  private final JavaMethod myClassAdderMethod;
  private final JavaMethod myIndexedClassAdderMethod;
  private final JavaMethod myInvertedIndexedClassAdderMethod;
  @NonNls private static final String ES = "es";

  public CollectionChildDescriptionImpl(final XmlName tagName,
                                        final Type type,
                                        final JavaMethod adderMethod,
                                        final JavaMethod classAdderMethod,
                                        final JavaMethod getterMethod,
                                        final JavaMethod indexedAdderMethod,
                                        final JavaMethod indexedClassAdderMethod,
                                        final JavaMethod invertedIndexedClassAdderMethod) {
    super(tagName, type);
    myAdderMethod = adderMethod;
    myClassAdderMethod = classAdderMethod;
    myGetterMethod = getterMethod;
    myIndexedAdderMethod = indexedAdderMethod;
    myIndexedClassAdderMethod = indexedClassAdderMethod;
    myInvertedIndexedClassAdderMethod = invertedIndexedClassAdderMethod;
  }

  public JavaMethod getClassAdderMethod() {
    return myClassAdderMethod;
  }

  public JavaMethod getIndexedClassAdderMethod() {
    return myIndexedClassAdderMethod;
  }

  public JavaMethod getInvertedIndexedClassAdderMethod() {
    return myInvertedIndexedClassAdderMethod;
  }

  public JavaMethod getAdderMethod() {
    return myAdderMethod;
  }

  public DomElement addValue(DomElement element) {
    return addChild(element, getType(), Integer.MAX_VALUE);
  }

  private DomElement addChild(final DomElement element, final Type type, final int index) {
    try {
      final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(element);
      assert handler != null;
      return handler.addChild(getXmlName().createEvaluatedXmlName(handler), type, index);
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }

  public DomElement addValue(DomElement element, int index) {
    return addChild(element, getType(), index);
  }

  public DomElement addValue(DomElement parent, Type type) {
    return addValue(parent, type, Integer.MAX_VALUE);
  }

  public final DomElement addValue(DomElement parent, Type type, int index) {
    return addChild(parent, type, Integer.MAX_VALUE);
  }

  public JavaMethod getGetterMethod() {
    return myGetterMethod;
  }

  public JavaMethod getIndexedAdderMethod() {
    return myIndexedAdderMethod;
  }

  @NotNull
  public List<? extends DomElement> getValues(final DomElement element) {
    return (List<? extends DomElement>)myGetterMethod.invoke(element, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public String getCommonPresentableName(DomNameStrategy strategy) {
    String words = strategy.splitIntoWords(getXmlElementName());
    return StringUtil.capitalizeWords(words.endsWith(ES) ? words: StringUtil.pluralize(words), true);
  }

  @Nullable
  public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    final T annotation = getGetterMethod().getAnnotation(annotationClass);
    if (annotation != null) return annotation;

    final Type elemType = getType();
    return elemType instanceof AnnotatedElement ? ((AnnotatedElement)elemType).getAnnotation(annotationClass) : null;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final CollectionChildDescriptionImpl that = (CollectionChildDescriptionImpl)o;

    if (myAdderMethod != null ? !myAdderMethod.equals(that.myAdderMethod) : that.myAdderMethod != null) return false;
    if (myClassAdderMethod != null ? !myClassAdderMethod.equals(that.myClassAdderMethod) : that.myClassAdderMethod != null) return false;
    if (myGetterMethod != null ? !myGetterMethod.equals(that.myGetterMethod) : that.myGetterMethod != null) return false;
    if (myIndexedAdderMethod != null ? !myIndexedAdderMethod.equals(that.myIndexedAdderMethod) : that.myIndexedAdderMethod != null) {
      return false;
    }
    if (myIndexedClassAdderMethod != null
        ? !myIndexedClassAdderMethod.equals(that.myIndexedClassAdderMethod)
        : that.myIndexedClassAdderMethod != null) {
      return false;
    }
    if (myInvertedIndexedClassAdderMethod != null
        ? !myInvertedIndexedClassAdderMethod.equals(that.myInvertedIndexedClassAdderMethod)
        : that.myInvertedIndexedClassAdderMethod != null) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + (myGetterMethod != null ? myGetterMethod.hashCode() : 0);
    result = 29 * result + (myAdderMethod != null ? myAdderMethod.hashCode() : 0);
    result = 29 * result + (myIndexedAdderMethod != null ? myIndexedAdderMethod.hashCode() : 0);
    result = 29 * result + (myClassAdderMethod != null ? myClassAdderMethod.hashCode() : 0);
    result = 29 * result + (myIndexedClassAdderMethod != null ? myIndexedClassAdderMethod.hashCode() : 0);
    result = 29 * result + (myInvertedIndexedClassAdderMethod != null ? myInvertedIndexedClassAdderMethod.hashCode() : 0);
    return result;
  }

}
