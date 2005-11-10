/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.Converter;

/**
 * @author peter
 */
public abstract class GetInvocation implements Invocation {
  private Converter myConverter;

  protected GetInvocation(final Converter converter) {
    assert converter != null;
    myConverter = converter;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    assert handler.isValid();
    final XmlTag tag = handler.getXmlTag();
    final String tagValue = tag != null ? getValue(tag) : null;
    return myConverter.fromString(tagValue, new ConvertContextImpl(handler));
  }

  protected abstract String getValue(XmlTag tag);
}
