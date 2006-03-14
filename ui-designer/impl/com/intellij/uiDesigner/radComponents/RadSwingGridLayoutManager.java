/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.core.GridLayoutManager;

import javax.swing.*;
import java.awt.LayoutManager;
import java.awt.GridLayout;
import java.awt.Insets;

/**
 * @author yole
 */
public class RadSwingGridLayoutManager extends RadGridLayoutManager {
  private int myLastRow = 0;
  private int myLastColumn = 0;

  @Override
  public void createSnapshotLayout(final JComponent parent, final RadContainer container, final LayoutManager layout) {
    GridLayout gridLayout = (GridLayout) layout;

    int ncomponents = parent.getComponentCount();
    int nrows = gridLayout.getRows();
    int ncols = gridLayout.getColumns();

    if (nrows > 0) {
        ncols = (ncomponents + nrows - 1) / nrows;
    } else {
        nrows = (ncomponents + ncols - 1) / ncols;
    }

    container.setLayout(new GridLayoutManager(nrows, ncols,
                                              new Insets(0, 0, 0, 0),
                                              gridLayout.getHgap(), gridLayout.getVgap(),
                                              true, true));
  }


  @Override
  public void addSnapshotComponent(final JComponent parent,
                                   final JComponent child,
                                   final RadContainer container,
                                   final RadComponent component) {
    GridLayoutManager grid = (GridLayoutManager) container.getLayout();
    component.getConstraints().setRow(myLastRow);
    component.getConstraints().setColumn(myLastColumn);
    if (myLastColumn == grid.getColumnCount()-1) {
      myLastRow++;
      myLastColumn = 0;
    }
    else {
      myLastColumn++;
    }
  }
}
