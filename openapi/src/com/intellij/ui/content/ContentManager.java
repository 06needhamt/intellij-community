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
package com.intellij.ui.content;

import com.intellij.openapi.actionSystem.AnAction;

import javax.swing.*;
import java.util.List;

import org.jetbrains.annotations.Nullable;

public interface ContentManager {
  boolean canCloseContents();

  JComponent getComponent();

  void addContent(Content content);
  void addContent(Content content, Object constraints);
  boolean removeContent(Content content);

  void setSelectedContent(Content content);
  void addSelectedContent(Content content);

  @Nullable
  Content getSelectedContent();
  Content[] getSelectedContents();


  void removeAllContents();

  int getContentCount();

  Content[] getContents();

  //TODO[anton,vova] is this method needed?
  Content findContent(String displayName);

  @Nullable
  Content getContent(int index);

  Content getContent(JComponent component);

  int getIndexOfContent(Content content);

  String getCloseActionName();

  boolean canCloseAllContents();

  void selectPreviousContent();

  void selectNextContent();

  void addContentManagerListener(ContentManagerListener l);

  void removeContentManagerListener(ContentManagerListener l);

  /**
   * Returns the localized name of the "Close All but This" action.
   *
   * @return the action name.
   * @since 5.1
   */
  String getCloseAllButThisActionName();

  List<AnAction> getAdditionalPopupActions(final Content content);

  void removeSelectedContent(Content content);

  boolean isSelected(Content content);

  void requestFocus(@Nullable Content content);
}
