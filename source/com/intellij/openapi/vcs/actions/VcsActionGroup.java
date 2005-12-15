package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

/**
 * @author mike
 */
public class VcsActionGroup extends DefaultActionGroup {
  public void update(AnActionEvent event) {
    super.update(event);

    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null){
      presentation.setVisible(false);
      presentation.setEnabled(false);
    } else if (!project.isOpen()) {
      presentation.setVisible(false);
      presentation.setEnabled(false);
    } else {
      presentation.setVisible(true);
      presentation.setEnabled(true);
    }
  }
}
