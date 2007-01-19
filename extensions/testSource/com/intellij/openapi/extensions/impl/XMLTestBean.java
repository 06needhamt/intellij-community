/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;

import java.util.Collection;

/**
 * @author Alexander Kireyev
 */
public class XMLTestBean implements PluginAware {
  private boolean otherProperty;
  private int prop1;
  private Object prop2;
  private Collection collectionProperty;
  private PluginId pluginId;

  public XMLTestBean() {
  }

  public XMLTestBean(Collection aCollectionProperty, boolean aOtherProperty, int aProp1) {
    collectionProperty = aCollectionProperty;
    otherProperty = aOtherProperty;
    prop1 = aProp1;
  }

  public boolean isOtherProperty() {
    return otherProperty;
  }

  public void setOtherProperty(boolean otherProperty) {
    this.otherProperty = otherProperty;
  }

  public int getProp1() {
    return prop1;
  }

  public void setProp1(int prop1) {
    this.prop1 = prop1;
  }

  public Object getProp2() {
    return prop2;
  }

  public void setProp2(Object prop2) {
    this.prop2 = prop2;
  }

  public Collection getCollectionProperty() {
    return collectionProperty;
  }

  public void setCollectionProperty(Collection collectionProperty) {
    this.collectionProperty = collectionProperty;
  }

  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    pluginId = pluginDescriptor.getPluginId();
  }

  public PluginId getPluginId() {
    return pluginId;
  }
}
