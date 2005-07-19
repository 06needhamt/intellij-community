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
package com.intellij.openapi.fileTypes;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Icons;

import javax.swing.*;

public abstract class UserFileType <T extends UserFileType> implements FileType, Cloneable {
  private String myName;
  private String myDescription;
  private Icon myIcon = Icons.CUSTOM_FILE_ICON;

  public abstract SettingsEditor<T> getEditor();

  public UserFileType clone() {
    try {
      return (UserFileType)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null; //Can't be
    }
  }

  public String getName() {
    return myName;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setName(String name) {
    myName = name;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public String getDefaultExtension() {
    return null;
  }

  public Icon getIcon() {
    return myIcon;
  }

  public boolean isReadOnly() {
    return false;
  }

  public String getCharset(VirtualFile file) {
    return null;
  }

  public void copyFrom(UserFileType newType) {
    myName = newType.getName();
    myDescription = newType.getDescription();
  }

  public void setIcon(Icon icon) {
    myIcon = icon;
  }

  public StructureViewBuilder getStructureViewBuilder(VirtualFile file, Project project) {
    return null;
  }

  public Language getLanguage() {
    return null;
  }
}