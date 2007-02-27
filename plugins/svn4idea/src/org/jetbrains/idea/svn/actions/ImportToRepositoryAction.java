package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.idea.svn.dialogs.ImportDialog;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 08.07.2005
 * Time: 21:44:21
 * To change this template use File | Settings | File Templates.
 */
public class ImportToRepositoryAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    ImportDialog dialog = new ImportDialog(project);
    dialog.show();
  }
}
