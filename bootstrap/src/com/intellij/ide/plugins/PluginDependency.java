package com.intellij.ide.plugins;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Text;

@Tag("depends")
public class PluginDependency {
  @Attribute("optional")
  public boolean optional;

  @Text 
  public String pluginId;
}
