package com.intellij.openapi.actionSystem.impl.config;

import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Bean;
import com.intellij.util.xmlb.annotations.Property;

@Bean(tagName = ActionManagerImpl.GROUP_ELEMENT_NAME)
public class ActionGroupBean extends ActionBean {
  @Property(surroundWithTag = false)
  @AbstractCollection(
    surroundWithTag = false,
    elementTypes = {ActionBean.class, ActionGroupBean.class, ActionReferenceBean.class, SeparatorBean.class})
  public Object[] actions;

  @Attribute(name = "test")
  public String test;

  @Attribute(name = "configurable")
  public boolean configurable;

}
