/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.j2ee.j2eeDom.xmlData.ReadOnlyDeploymentDescriptorModificationException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ClassChooserManager;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class AddChildInvocation implements Invocation{
  private final String myTagName;
  private final Type myType;
  private final Function<Object[],Integer> myIndexGetter;
  private final Function<Object[], Type> myClassGetter;

  public AddChildInvocation(final Function<Object[], Type> classGetter,
                            final Function<Object[], Integer> indexGetter,
                            final String tagName,
                            final Type type) {
    myClassGetter = classGetter;
    myIndexGetter = indexGetter;
    myTagName = tagName;
    myType = type;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    final VirtualFile virtualFile = handler.getFile().getVirtualFile();
    if (virtualFile != null && !virtualFile.isWritable()) {
      throw new ReadOnlyDeploymentDescriptorModificationException(virtualFile);
    }
    final Type type = myClassGetter.fun(args);
    final DomElement domElement = handler.addChild(myTagName, type, myIndexGetter.fun(args));
    final boolean b = handler.getManager().setChanging(true);
    try {
      ClassChooserManager.getClassChooser(DomUtil.getRawType(type)).distinguishTag(domElement.getXmlTag(), DomUtil.getRawType(type));
    }
    finally {
      handler.getManager().setChanging(b);
    }
    return domElement;
  }
}
