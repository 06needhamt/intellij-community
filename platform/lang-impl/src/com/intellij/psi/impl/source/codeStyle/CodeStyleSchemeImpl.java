/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.SchemeDataHolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeStyleSchemeImpl implements CodeStyleScheme, ExternalizableScheme {
  private String myName;

  private static final Logger LOG = Logger.getInstance(CodeStyleSchemeImpl.class);

  private SchemeDataHolder myDataHolder;
  private String myParentSchemeName;
  private final boolean myIsDefault;
  private volatile CodeStyleSettings myCodeStyleSettings;

  CodeStyleSchemeImpl(@NotNull String name, String parentSchemeName, @NotNull SchemeDataHolder dataHolder) {
    setName(name);
    myDataHolder = dataHolder;
    myIsDefault = false;
    myParentSchemeName = parentSchemeName;
  }

  public CodeStyleSchemeImpl(@NotNull String name, boolean isDefault, CodeStyleScheme parentScheme) {
    setName(name);
    myIsDefault = isDefault;
    init(parentScheme, null);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String value) {
    myName = value;
  }

  private void init(@Nullable CodeStyleScheme parentScheme, Element root) {
    if (parentScheme == null) {
      myCodeStyleSettings = new CodeStyleSettings();
    }
    else {
      CodeStyleSettings parentSettings = parentScheme.getCodeStyleSettings();
      myCodeStyleSettings = parentSettings.clone();
      while (parentSettings.getParentSettings() != null) {
        parentSettings = parentSettings.getParentSettings();
      }
      myCodeStyleSettings.setParentSettings(parentSettings);
    }
    if (root != null) {
      try {
        myCodeStyleSettings.readExternal(root);
      }
      catch (InvalidDataException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public CodeStyleSettings getCodeStyleSettings() {
    if (myDataHolder != null) {
      init(myParentSchemeName == null ? null : CodeStyleSchemesImpl.getSchemeManager().findSchemeByName(myParentSchemeName), myDataHolder.read());
      myParentSchemeName = null;
      myDataHolder = null;
    }
    return myCodeStyleSettings;
  }

  boolean isInitialized() {
    return myDataHolder == null;
  }

  public void setCodeStyleSettings(@NotNull CodeStyleSettings codeStyleSettings){
    myCodeStyleSettings = codeStyleSettings;
    myParentSchemeName = null;
    myDataHolder = null;
  }

  @Override
  public boolean isDefault() {
    return myIsDefault;
  }

  @NotNull
  public Element writeScheme() throws WriteExternalException {
    if (myDataHolder == null) {
      Element newElement = new Element("code_scheme");
      newElement.setAttribute("name", getName());
      myCodeStyleSettings.writeExternal(newElement);
      return newElement;
    }
    else {
      return myDataHolder.read();
    }
  }
}
