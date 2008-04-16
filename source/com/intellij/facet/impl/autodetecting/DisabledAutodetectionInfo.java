/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * @author nik
 */
public class DisabledAutodetectionInfo {
  private List<DisabledAutodetectionByTypeElement> myElements = new ArrayList<DisabledAutodetectionByTypeElement>();

  @Tag("autodetection-disabled")
  @AbstractCollection(surroundWithTag = false)
  public List<DisabledAutodetectionByTypeElement> getElements() {
    return myElements;
  }

  public void setElements(final List<DisabledAutodetectionByTypeElement> elements) {
    myElements = elements;
  }

  public boolean isDisabled(final @NotNull String facetType, final @NotNull String moduleName, String url) {
    DisabledAutodetectionByTypeElement element = findElement(facetType);
    return element != null && element.isDisabled(moduleName, url);
  }

  public void replaceElement(@NotNull String facetTypeId, @Nullable DisabledAutodetectionByTypeElement element) {
    Iterator<DisabledAutodetectionByTypeElement> iterator = myElements.iterator();
    while (iterator.hasNext()) {
      DisabledAutodetectionByTypeElement typeElement = iterator.next();
      if (typeElement.getFacetTypeId().equals(facetTypeId)) {
        iterator.remove();
        break;
      }
    }
    if (element != null) {
      myElements.add(element);
    }
  }

  @Nullable
  public DisabledAutodetectionByTypeElement findElement(@NotNull String facetTypeId) {
    for (DisabledAutodetectionByTypeElement element : myElements) {
      if (facetTypeId.equals(element.getFacetTypeId())) {
        return element;
      }
    }
    return null;
  }

  public void addDisabled(final @NotNull String facetTypeId) {
    DisabledAutodetectionByTypeElement element = findElement(facetTypeId);
    if (element != null) {
      element.disableInProject();
    }
    else {
      myElements.add(new DisabledAutodetectionByTypeElement(facetTypeId));
    }
  }

  public void addDisabled(final @NotNull String facetTypeId, final @NotNull String moduleName) {
    DisabledAutodetectionByTypeElement element = findElement(facetTypeId);
    if (element != null) {
      element.addDisabled(moduleName);
    }
    else {
      myElements.add(new DisabledAutodetectionByTypeElement(facetTypeId, moduleName));
    }
  }

  public void addDisabled(final @NotNull String facetTypeId, final @NotNull String moduleName, String url) {
    DisabledAutodetectionByTypeElement element = findElement(facetTypeId);
    if (element != null) {
      element.addDisabled(moduleName, url);
    }
    else {
      myElements.add(new DisabledAutodetectionByTypeElement(facetTypeId, moduleName, url));
    }
  }

  public void addDisabled(final @NotNull String facetTypeId, final @NotNull String moduleName, final @NotNull String... urls) {
    for (String url : urls) {
      addDisabled(facetTypeId, moduleName, url);
    }
  }
}
