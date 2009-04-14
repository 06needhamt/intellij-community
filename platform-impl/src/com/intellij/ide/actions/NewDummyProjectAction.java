package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.ex.ProjectManagerEx;

/**
 * @author yole
 */
public class NewDummyProjectAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = projectManager.newProject("dummy", PathManager.getConfigPath() + "/dummy.ipr", true, false);
    if (project == null) return;
    projectManager.openProject(project);
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible("Platform".equals(System.getProperty("idea.platform.prefix")));
  }
}