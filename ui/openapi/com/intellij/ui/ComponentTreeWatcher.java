/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

public abstract class ComponentTreeWatcher {
  protected final Class[] myControlsToIgnore;

  protected ComponentTreeWatcher(final Class[] controlsToIgnore) {
    myControlsToIgnore = controlsToIgnore;
  }

  private final ContainerListener myContainerListener = new ContainerListener() {
    public void componentAdded(ContainerEvent e) {
      register(e.getChild());
    }

    public void componentRemoved(ContainerEvent e) {
      unregister(e.getChild());
    }
  };

  private boolean shouldBeIgnored(Object object) {
    if (object instanceof CellRendererPane) return true;
    if (object == null) {
      return true;
    }
    for (int i = 0; i < myControlsToIgnore.length; i++) {
      Class aClass = myControlsToIgnore[i];
      if (aClass.isAssignableFrom(object.getClass())) {
        return true;
      }
    }
    return false;
  }

  public final void register(Component parentComponent) {
    if (shouldBeIgnored(parentComponent)) {
      return;
    }

    if (parentComponent instanceof Container) {
      Container container = ((Container)parentComponent);
      for (int i = 0; i < container.getComponentCount(); i++) {
        register(container.getComponent(i));
      }
      container.addContainerListener(myContainerListener);
    }

    processComponent(parentComponent);
  }

  protected abstract void processComponent(Component parentComponent);

  private void unregister(Component component) {

    if (component instanceof Container) {
      Container container = ((Container)component);
      for (int i = 0; i < container.getComponentCount(); i++) {
        unregister(container.getComponent(i));
      }
      container.removeContainerListener(myContainerListener);
    }

    unprocessComponent(component);
  }

  protected abstract void unprocessComponent(Component component);
}
