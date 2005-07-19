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
package com.intellij.ui.components.panels;

import javax.swing.*;
import java.awt.*;

public class Wrapper extends JPanel {

  public Wrapper() {
    setLayout(new BorderLayout());
  }

  public Wrapper(JComponent wrapped) {
    setLayout(new BorderLayout());
    add(wrapped, BorderLayout.CENTER);
  }

  public Wrapper(LayoutManager layout, JComponent wrapped) {
    super(layout);
    add(wrapped);
  }

  public Wrapper(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
  }

  public Wrapper(LayoutManager layout) {
    super(layout);
  }

  public Wrapper(LayoutManager layout, boolean isDoubleBuffered) {
    super(layout, isDoubleBuffered);
  }

  public void setContent(JComponent wrapped) {
    if (wrapped == getTargetComponent()) {
      return;
    }
    
    removeAll();
    setLayout(new BorderLayout());
    if (wrapped != null) {
      add(wrapped, BorderLayout.CENTER);
    }
  }

  public void requestFocus() {
    if (getTargetComponent() == this) {
      super.requestFocus();
      return;
    }
    getTargetComponent().requestFocus();
  }

  public final boolean requestFocusInWindow() {
    if (getTargetComponent() == this) {
      return super.requestFocusInWindow();
    }
    return getTargetComponent().requestFocusInWindow();
  }

  public final boolean requestFocus(boolean temporary) {
    if (getTargetComponent() == this) {
      return super.requestFocus(temporary);
    }
    return getTargetComponent().requestFocus(temporary);
  }

  private JComponent getTargetComponent() {
    if (getComponentCount() == 1) {
      return (JComponent) getComponent(0);
    } else {
      return this;
    }
  }
}
