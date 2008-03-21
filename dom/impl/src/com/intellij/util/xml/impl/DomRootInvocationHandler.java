/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DomRootInvocationHandler extends DomInvocationHandler<AbstractDomChildDescriptionImpl> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomRootInvocationHandler");
  private final DomFileElementImpl<?> myParent;

  public DomRootInvocationHandler(final Class aClass,
                                  final XmlTag tag,
                                  @NotNull final DomFileElementImpl fileElement,
                                  @NotNull final EvaluatedXmlName tagName
  ) {
    super(aClass, tag, null, tagName, null, fileElement.getManager());
    myParent = fileElement;
  }

  public void undefineInternal() {
    try {
      final XmlTag tag = getXmlTag();
      if (tag != null) {
        deleteTag(tag);
        detach();
        fireUndefinedEvent();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public boolean equals(final Object obj) {
    if (!(obj instanceof DomRootInvocationHandler)) return false;

    final DomRootInvocationHandler handler = (DomRootInvocationHandler)obj;
    return myParent.equals(handler.myParent);
  }

  public int hashCode() {
    return myParent.hashCode();
  }

  public boolean isValid() {
    if (!myParent.isValid()) return false;
    final XmlTag tag = getXmlTag();
    return tag == null || tag.isValid();
  }

  @NotNull
  public String getXmlElementNamespace() {
    return getXmlName().getNamespace(getFile());
  }

  @NotNull
  public <T extends DomElement> DomFileElementImpl<T> _getRoot() {
    return (DomFileElementImpl<T>)myParent;
  }

  public DomElement getParent() {
    checkIsValid();
    return myParent;
  }

  public DomElement createPathStableCopy() {
    final DomFileElement stableCopy = myParent.createStableCopy();
    return getManager().createStableValue(new NullableFactory<DomElement>() {
      public DomElement create() {
        return stableCopy.isValid() ? stableCopy.getRootElement() : null;
      }
    });
  }

  protected XmlTag setEmptyXmlTag() {
    final XmlTag[] result = new XmlTag[]{null};
    getManager().runChange(new Runnable() {
      public void run() {
        try {
          final String namespace = getXmlName().getNamespace(getFile());
          @NonNls final String nsDecl = StringUtil.isEmpty(namespace) ? "" : " xmlns=\"" + namespace + "\"";
          final XmlFile xmlFile = getFile();
          final XmlTag tag = XmlElementFactory.getInstance(xmlFile.getProject()).createTagFromText("<" + getXmlElementName() + nsDecl + "/>");
          result[0] = ((XmlDocument)xmlFile.getDocument().replace(((XmlFile)tag.getContainingFile()).getDocument())).getRootTag();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    return result[0];
  }

}
