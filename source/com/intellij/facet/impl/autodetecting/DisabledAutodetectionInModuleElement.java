/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author nik
 */
@Tag("module")
public class DisabledAutodetectionInModuleElement {
  private String myModuleName;
  private Set<String> myFiles = new LinkedHashSet<String>();

  public DisabledAutodetectionInModuleElement() {
  }

  public DisabledAutodetectionInModuleElement(final String moduleName) {
    myModuleName = moduleName;
  }

  public DisabledAutodetectionInModuleElement(final String moduleName, final String url) {
    myModuleName = moduleName;
    myFiles.add(url);
  }

  @Attribute("name")
  public String getModuleName() {
    return myModuleName;
  }

  @Tag("files")
  @AbstractCollection(surroundWithTag = false, elementTag = "file", elementValueAttribute = "url")
  public Set<String> getFiles() {
    return myFiles;
  }

  public void setModuleName(final String moduleName) {
    myModuleName = moduleName;
  }

  public void setFiles(final Set<String> files) {
    myFiles = files;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DisabledAutodetectionInModuleElement that = (DisabledAutodetectionInModuleElement)o;
    return myFiles.equals(that.myFiles) && myModuleName.equals(that.myModuleName);

  }

  public int hashCode() {
    return 31 * myModuleName.hashCode() + myFiles.hashCode();
  }
}
