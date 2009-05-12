/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.wm;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public abstract class IdeFocusManager {

  /**
   * Requests focus on a component
   * @param c
   * @param forced
   * @return action callback that either notifies when the focus was obtained or focus request was droppped
   */
  @NotNull
  public abstract ActionCallback requestFocus(@NotNull Component c, boolean forced);

  /**
   * Runs a request focus command, actual focus request is defined by the user in the command itself
   * @param command
   * @param forced
   * @return action callback that either notifies when the focus was obtained or focus request was droppped
   */
  @NotNull
  public abstract ActionCallback requestFocus(@NotNull FocusCommand command, boolean forced);

  /**
   * Finds most suitable component to request focus to. For instance you may pass a JPanel instance,
   * this method will traverse into it's children to find focusable component
   * @param comp
   * @return suitable component to focus
   */
  @Nullable
  public abstract JComponent getFocusTargetFor(@NotNull final JComponent comp);

  public static IdeFocusManager getInstance(@NotNull Project project) {
    return project.getComponent(IdeFocusManager.class);
  }

  public abstract void doWhenFocusSettlesDown(@NotNull Runnable runnable);

  @Nullable
  public abstract Component getFocusedDescendantFor(final Component comp);

  public abstract boolean isFocusTransferInProgress();

  public abstract boolean dispatch(KeyEvent e);
  public abstract boolean isRedispatching();
}
