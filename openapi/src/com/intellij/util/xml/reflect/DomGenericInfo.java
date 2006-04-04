/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.reflect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author peter
 */
public interface DomGenericInfo {

  @NotNull
  List<DomChildrenDescription> getChildrenDescriptions();

  @NotNull
  List<DomFixedChildDescription> getFixedChildrenDescriptions();

  @NotNull
  List<DomCollectionChildDescription> getCollectionChildrenDescriptions();

  @NotNull
  List<DomAttributeChildDescription> getAttributeChildrenDescriptions();

  @Nullable
  DomChildrenDescription getChildDescription(@NonNls String tagName);

  @Nullable
  DomFixedChildDescription getFixedChildDescription(@NonNls String tagName);

  @Nullable
  DomCollectionChildDescription getCollectionChildDescription(@NonNls String tagName);

  @Nullable
  DomAttributeChildDescription getAttributeChildDescription(@NonNls String attributeName);

  /**
   * @return true, if there's no children in the element, only tag value accessors
   */
  boolean isTagValueElement();


}
