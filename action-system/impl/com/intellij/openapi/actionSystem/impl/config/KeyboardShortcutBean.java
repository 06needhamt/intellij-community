package com.intellij.openapi.actionSystem.impl.config;

import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

@Tag(name = ActionManagerImpl.SHORTCUT_ELEMENT_NAME)
public class KeyboardShortcutBean {
  @Attribute(name = ActionManagerImpl.FIRST_KEYSTROKE_ATTR_NAME)
  public String firstKeystroke;

  @Attribute(name = ActionManagerImpl.KEYMAP_ATTR_NAME)
  public String keymap;
}
