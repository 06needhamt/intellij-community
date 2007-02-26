/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.SubTag;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author peter
 */
public class IndexedElementInvocationHandler extends DomInvocationHandler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.IndexedElementInvocationHandler");
  private final int myIndex;

  public IndexedElementInvocationHandler(final Type aClass,
                                         final XmlTag tag,
                                         final DomInvocationHandler parent,
                                         final EvaluatedXmlName tagName,
                                         final int index) {
    super(aClass, tag, parent, tagName, parent.getManager());
    myIndex = index;
  }

  public boolean isValid() {
    return super.isValid() && getParentHandler().isValid();
  }

  final boolean isIndicator() {
    final SubTag annotation = getAnnotation(SubTag.class);
    return annotation != null && annotation.indicator();
  }

  protected XmlTag setEmptyXmlTag() {
    final DomInvocationHandler parent = getParentHandler();
    parent.createFixedChildrenTags(getXmlName(), myIndex);
    final XmlTag[] newTag = new XmlTag[1];
    getManager().runChange(new Runnable() {
      public void run() {
        try {
          final XmlTag parentTag = parent.getXmlTag();
          newTag[0] = (XmlTag)parentTag.add(parent.createChildTag(getXmlName()));
          if (getParentHandler().getFixedChildrenClass(getXmlName()) != null) {
            getManager().getTypeChooserManager().getTypeChooser(getChildDescription().getType()).distinguishTag(newTag[0], getDomElementType());
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
    return newTag[0];
  }

  public void undefineInternal() {
    final DomInvocationHandler parent = getParentHandler();
    final XmlTag parentTag = parent.getXmlTag();
    if (parentTag == null) return;

    final EvaluatedXmlName xmlElementName = getXmlName();
    parent.checkInitialized(xmlElementName);

    final int totalCount = parent.getGenericInfo().getFixedChildrenCount(xmlElementName.getXmlName());

    final List<XmlTag> subTags = DomImplUtil.findSubTags(parentTag, xmlElementName, this);
    if (subTags.size() <= myIndex) {
      return;
    }

    final boolean changing = getManager().setChanging(true);
    try {
      XmlTag tag = getXmlTag();
      assert tag != null;
      detach(false);
      if (totalCount == myIndex + 1 && subTags.size() >= myIndex + 1) {
        for (int i = myIndex; i < subTags.size(); i++) {
          subTags.get(i).delete();
        }
      }
      else if (subTags.size() == myIndex + 1) {
        tag.delete();
      } else {
        attach((XmlTag) tag.replace(parent.createChildTag(getXmlName())));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    } finally {
      getManager().setChanging(changing);
    }
    detachChildren();
    fireUndefinedEvent();
  }

  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    final T annotation = getChildDescription().getAnnotation(myIndex, annotationClass);
    if (annotation != null) return annotation;

    return getRawType().getAnnotation(annotationClass);
  }

  public final <T extends DomElement> T createStableCopy() {
    final DomFixedChildDescription description = getChildDescription();
    final DomElement parentCopy = findCallerProxy(CREATE_STABLE_COPY_METHOD).getParent().createStableCopy();
    return getManager().createStableValue(new Factory<T>() {
      public T create() {
        return parentCopy.isValid() ? (T)description.getValues(parentCopy).get(myIndex) : null;
      }
    });
  }

  @NotNull
  protected final FixedChildDescriptionImpl getChildDescription() {
    final FixedChildDescriptionImpl description = getParentHandler().getGenericInfo().getFixedChildDescription(getXmlName().getXmlName());
    assert description != null;
    return description;
  }
}
