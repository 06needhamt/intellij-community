/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomNameStrategy;
import com.intellij.util.xml.reflect.DomFixedChildDescription;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * @author peter
 */
public class FixedChildDescriptionImpl extends DomChildDescriptionImpl implements DomFixedChildDescription {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.FixedChildDescriptionImpl");
  private final Method[] myGetterMethods;
  private final int myCount;

  public FixedChildDescriptionImpl(final String tagName, final Type type, final int count, final Method[] getterMethods) {
    super(tagName, type);
    assert getterMethods.length == count;
    myCount = count;
    myGetterMethods = getterMethods;
  }

  public Method getGetterMethod(int index) {
    return myGetterMethods[index];
  }

  public void initConcreteClass(final DomElement parent, final Class<? extends DomElement> aClass) {
    ((DomProxy) parent).getDomInvocationHandler().setFixedChildClass(getXmlElementName(), aClass);
  }

  public int getCount() {
    return myCount;
  }

  public List<? extends DomElement> getValues(final DomElement element) {
    final ArrayList<DomElement> result = new ArrayList<DomElement>();
    for (Method method : myGetterMethods) {
      if (method != null) {
        try {
          result.add((DomElement) method.invoke(element));
        }
        catch (IllegalAccessException e) {
          LOG.error(e);
        }
        catch (InvocationTargetException e) {
          LOG.error(e);
        }
      }
    }
    return result;
  }

  public String getCommonPresentableName(DomNameStrategy strategy) {
    return StringUtil.capitalizeWords(strategy.splitIntoWords(getXmlElementName()), true);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final FixedChildDescriptionImpl that = (FixedChildDescriptionImpl)o;

    if (myCount != that.myCount) return false;
    if (!Arrays.equals(myGetterMethods, that.myGetterMethods)) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 29 * result + myCount;
    return result;
  }
}
