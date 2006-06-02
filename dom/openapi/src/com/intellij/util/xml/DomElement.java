/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.module.Module;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.lang.annotation.Annotation;

/**
 * @author peter
 */
public interface DomElement {

  DomElement[] EMPTY_ARRAY = new DomElement[0];
  
  @Nullable
  <T extends Annotation> T getAnnotation(Class<T> annotationClass);

  @Nullable
  XmlTag getXmlTag();

  <T extends DomElement> DomFileElement<T> getRoot();

  DomElement getParent();

  XmlTag ensureTagExists();

  @Nullable
  XmlElement getXmlElement();

  XmlElement ensureXmlElementExists();

  void undefine();

  boolean isValid();

  @NotNull
  DomGenericInfo getGenericInfo();

  @NotNull
  String getXmlElementName();

  void accept(final DomElementVisitor visitor);

  void acceptChildren(DomElementVisitor visitor);

  DomManager getManager();

  Type getDomElementType();

  DomNameStrategy getNameStrategy();

  @NotNull
  ElementPresentation getPresentation();

  GlobalSearchScope getResolveScope();

  <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict);

  Module getModule();

  void copyFrom(DomElement other);

  <T extends DomElement> T createMockCopy(final boolean physical);

  <T extends DomElement> T createStableCopy();

}
