
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;

public class JumpToLastWindowAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }
    ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(project);
    String id = manager.getLastActiveToolWindowId();
    if(id==null||!manager.getToolWindow(id).isAvailable()){
      return;
    }
    manager.getToolWindow(id).activate(null);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = PlatformDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    ToolWindowManagerEx manager=(ToolWindowManagerEx)ToolWindowManager.getInstance(project);
    String id = manager.getLastActiveToolWindowId();
    presentation.setEnabled(id != null && manager.getToolWindow(id).isAvailable());
  }
}
