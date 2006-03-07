/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public class CollectionChildDescriptionImpl extends DomChildDescriptionImpl implements DomCollectionChildDescription {
  private final Method myGetterMethod, myAdderMethod, myIndexedAdderMethod;
  private final Method myClassAdderMethod, myIndexedClassAdderMethod, myInvertedIndexedClassAdderMethod;
  private final int myStartIndex;

  public CollectionChildDescriptionImpl(final String tagName,
                                        final Type type,
                                        final Method adderMethod,
                                        final Method classAdderMethod,
                                        final Method getterMethod,
                                        final Method indexedAdderMethod,
                                        final Method indexedClassAdderMethod,
                                        final Method invertedIndexedClassAdderMethod,
                                        final int startIndex) {
    super(tagName, type);
    myAdderMethod = adderMethod;
    myClassAdderMethod = classAdderMethod;
    myGetterMethod = getterMethod;
    myIndexedAdderMethod = indexedAdderMethod;
    myIndexedClassAdderMethod = indexedClassAdderMethod;
    myInvertedIndexedClassAdderMethod = invertedIndexedClassAdderMethod;
    myStartIndex = startIndex;
  }

  public Method getClassAdderMethod() {
    return myClassAdderMethod;
  }

  public Method getIndexedClassAdderMethod() {
    return myIndexedClassAdderMethod;
  }

  public Method getInvertedIndexedClassAdderMethod() {
    return myInvertedIndexedClassAdderMethod;
  }

  public Method getAdderMethod() {
    return myAdderMethod;
  }

  public DomElement addValue(DomElement element) {
    return addChild(element, getType(), Integer.MAX_VALUE);
  }

  private DomElement addChild(final DomElement element, final Type type, final int index) {
    try {
      return DomManagerImpl.getDomInvocationHandler(element).addChild(getXmlElementName(), type, index);
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }

  public DomElement addValue(DomElement element, int index) {
    return addChild(element, getType(), index + myStartIndex);
  }

  public DomElement addValue(DomElement parent, Class aClass) {
    return addValue(parent, aClass, Integer.MAX_VALUE);
  }

  public final DomElement addValue(DomElement parent, Class aClass, int index) {
    return addChild(parent, aClass, Integer.MAX_VALUE);
  }

  public Method getGetterMethod() {
    return myGetterMethod;
  }

  public Method getIndexedAdderMethod() {
    return myIndexedAdderMethod;
  }

  public List<? extends DomElement> getValues(final DomElement element) {
    try {
      return (List<DomElement>)myGetterMethod.invoke(element);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public String getCommonPresentableName(DomNameStrategy strategy) {
    return StringUtil.capitalizeWords(StringUtil.pluralize(strategy.splitIntoWords(getXmlElementName())), true);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final CollectionChildDescriptionImpl that = (CollectionChildDescriptionImpl)o;

    if (myStartIndex != that.myStartIndex) return false;
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
    result = 29 * result + myStartIndex;
    return result;
  }

}
