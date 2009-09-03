/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.palette.Palette;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * @author yole
 */
public class RadTable extends RadAtomicComponent {
  public static class Factory extends RadComponentFactory {
    public RadComponent newInstance(Module module, Class aClass, String id) {
      return new RadTable(module, aClass, id);
    }

    public RadComponent newInstance(final Class componentClass, final String id, final Palette palette) {
      return new RadTable(componentClass, id, palette);
    }
  }

  public RadTable(final Module module, final Class componentClass, final String id) {
    super(module, componentClass, id);
    initDefaultModel();
  }

  public RadTable(final Class componentClass, final String id, final Palette palette) {
    super(componentClass, id, palette);
    initDefaultModel();
  }

  private void initDefaultModel() {
    @NonNls Object[][] data = new Object[][] { new Object[] { "round", "red"},
      new Object[] { "square", "green" } };
    @NonNls Object[] columnNames = new Object[] { "Shape", "Color" };
    try {
      ((JTable) getDelegee()).setModel(new DefaultTableModel(data, columnNames));
    }
    catch(Exception ex) {
      // a custom table subclass may not like our model, so ignore the exception if thrown here
    }
    catch(AssertionError ex) {
      // a custom table subclass may not like our model, so ignore the exception if thrown here
    }
  }
}
