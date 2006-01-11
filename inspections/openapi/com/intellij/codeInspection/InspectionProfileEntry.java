/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 28-Nov-2005
 */
public abstract class InspectionProfileEntry {

  public abstract String getGroupDisplayName();

  public abstract String getDisplayName();

  /**
   * @return short name that is used in two cases: \inspectionDescriptions\&lt;short_name&gt;.html resource may contain short inspection
   *         description to be shown in "Inspect Code..." dialog and also provide some file name convention when using offline
   *         inspection or export to HTML function. Should be unique among all inspections.
   */
  @NonNls public abstract String getShortName();

  /**
   * @return highlighting level for this inspection tool that is used in default settings
   */
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  /**
   * @return null if no UI options required
   */
  @Nullable
  public JComponent createOptionsPanel() {
    return null;
  }

  /**
   * Read in settings from xml config. Default implementation uses DefaultJDOMExternalizer so you may use public fields like <code>int TOOL_OPTION</code> to store your options.
   *
   * @param node to read settings from.
   * @throws InvalidDataException
   */
  public void readSettings(Element node) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, node);
  }

  /**
   * Store current settings in xml config. Default implementation uses DefaultJDOMExternalizer so you may use public fields like <code>int TOOL_OPTION</code> to store your options.
   *
   * @param node to store settings to.
   * @throws WriteExternalException
   */
  public void writeSettings(Element node) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, node);
  }


}
