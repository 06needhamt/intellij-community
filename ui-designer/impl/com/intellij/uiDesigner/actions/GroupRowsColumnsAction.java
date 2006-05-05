/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.jgoodies.forms.layout.FormLayout;

/**
 * @author yole
 */
public class GroupRowsColumnsAction extends RowColumnAction {
  public GroupRowsColumnsAction() {
    super(UIDesignerBundle.message("action.group.columns"), null, UIDesignerBundle.message("action.group.rows"), null);
  }

  @Override
  public void update(final AnActionEvent e) {
    super.update(e);
    CaptionSelection selection = (CaptionSelection) e.getDataContext().getData(CaptionSelection.class.getName());
    if (selection != null) {
      e.getPresentation().setEnabled(selection.getContainer() != null &&
        selection.getContainer().getLayout() instanceof FormLayout &&
        selection.getSelection().length > 1 &&
        !isGrouped(selection));
    }
  }

  public static boolean isGrouped(final CaptionSelection selection) {
    FormLayout layout = (FormLayout) selection.getContainer().getLayout();
    int[][] groups = selection.isRow() ? layout.getRowGroups() : layout.getColumnGroups();
    final int[] indices = selection.getSelection();
    for (int[] group : groups) {
      if (intersect(group, indices)) return true;
    }
    return false;
  }

  public static boolean intersect(final int[] group, final int[] indices) {
    for (int groupMember : group) {
      for (int index : indices) {
        if (groupMember == index+1) return true;
      }
    }
    return false;
  }

  protected void actionPerformed(CaptionSelection selection) {
    FormLayout layout = (FormLayout) selection.getContainer().getLayout();
    int[][] oldGroups = selection.isRow() ? layout.getRowGroups() : layout.getColumnGroups();
    int[][] newGroups = new int[oldGroups.length + 1][];
    System.arraycopy(oldGroups, 0, newGroups, 0, oldGroups.length);
    newGroups [oldGroups.length] = new int [selection.getSelection().length];
    int[] indices = selection.getSelection();
    for(int i=0; i<indices.length; i++) {
      newGroups [oldGroups.length] [i] = indices [i]+1;
    }
    if (selection.isRow()) {
      layout.setRowGroups(newGroups);
    }
    else {
      layout.setColumnGroups(newGroups);
    }
  }
}
