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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * @author Eugene Belyaev
 */
public abstract class PopupHandler extends MouseAdapter {
  public abstract void invokePopup(Component comp, int x, int y);

  public void mouseClicked(MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e.getComponent(), e.getX(), e.getY());
      e.consume();
    }
  }

  public void mousePressed(MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e.getComponent(), e.getX(), e.getY());
      e.consume();
    }
  }

  public void mouseReleased(MouseEvent e) {
    if (e.isPopupTrigger()) {
      invokePopup(e.getComponent(), e.getX(), e.getY());
      e.consume();
    }
  }

  public static void installPopupHandler(JComponent component, String groupId, String place) {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(groupId);
    installPopupHandler(component, group, place, ActionManager.getInstance());
  }

  public static MouseListener installPopupHandler(JComponent component, ActionGroup group, String place, ActionManager actionManager) {
    if (ApplicationManager.getApplication() == null) return new MouseAdapter(){};
    final ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(place, group);
    PopupHandler popupHandler = new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        popupMenu.getComponent().show(comp, x, y);
      }
    };
    component.addMouseListener(popupHandler);
    return popupHandler;
  }

  public static MouseListener installUnknownPopupHandler(JComponent component, ActionGroup group, ActionManager actionManager) {
    return installPopupHandler(component, group,  ActionPlaces.UNKNOWN, actionManager);
  }
}