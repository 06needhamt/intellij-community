/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.events.CollectionElementRemovedEvent;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class CollectionElementInvocationHandler extends DomInvocationHandler{

  public CollectionElementInvocationHandler(final Type type,
                                            @NotNull final XmlTag tag,
                                            final DomInvocationHandler parent) {
    super(type, tag, parent, tag.getName(), parent.getManager(), null);
  }

  protected final XmlTag setXmlTag(final XmlTag tag) throws IncorrectOperationException {
    throw new UnsupportedOperationException("CollectionElementInvocationHandler.setXmlTag() shouldn't be called");
  }

  public final void undefineInternal() {
    final DomElement parent = getParent();
    final XmlTag tag = getXmlTag();
    detach(true);
    deleteTag(tag);
    getManager().fireEvent(new CollectionElementRemovedEvent(getProxy(), parent, getXmlElementName()));
  }

}
