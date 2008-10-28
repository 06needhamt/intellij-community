package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.actions.BaseViewAction;
import com.intellij.execution.ui.layout.Grid;
import com.intellij.execution.ui.layout.Tab;
import com.intellij.execution.ui.layout.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class MoveToGridAction extends BaseViewAction {
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (!context.isMoveToGridActionEnabled() || content.length != 1) {
      setEnabled(e, false);
      return;
    }

    if (isDetached(context, content[0])) {
      setEnabled(e, false);
      return;
    }

    Grid grid = context.findGridFor(content[0]);
    if (grid == null) {
      setEnabled(e, false);
      return;
    }
    Tab tab = context.getTabFor(grid);
    setEnabled(e, !tab.isDefault() && grid.getContents().size() == 1);
  }
                     
  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.getCellTransform().moveToGrid(content[0]);
  }
}
