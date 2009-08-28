package com.intellij.platform;

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;

/**
 * @author yole
 */
public class NewDirectoryProjectAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    NewDirectoryProjectDialog dlg = new NewDirectoryProjectDialog(project);
    dlg.show();
    if (dlg.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;
    final DirectoryProjectGenerator generator = dlg.getProjectGenerator();
    final File location = new File(dlg.getNewProjectLocation());
    if (!location.exists() && !location.mkdirs()) {
      Messages.showErrorDialog(project, "Cannot create directory '" + location + "'", "Create Project");
      return;
    }

    VirtualFile baseDir = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(location);
      }
    });
    baseDir.refresh(false, true);

    Object settings = null;
    if (generator != null) {
      try {
        settings = generator.showGenerationSettings(baseDir);
      }
      catch (ProcessCanceledException e1) {
        return;
      }
    }
    GeneralSettings.getInstance().setLastProjectLocation(location.getParent());
    Project newProject = PlatformProjectOpenProcessor.getInstance().doOpenProject(baseDir, null, false);
    if (generator != null && newProject != null) {
      //noinspection unchecked
      generator.generateProject(newProject, baseDir, settings);
    }
  }
}
