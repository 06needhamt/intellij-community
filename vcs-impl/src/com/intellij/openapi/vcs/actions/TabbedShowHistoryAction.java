package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;


public class TabbedShowHistoryAction extends AbstractVcsAction {
  protected void update(VcsContext context, Presentation presentation) {
    presentation.setEnabled(isEnabled(context));
    final Project project = context.getProject();
    presentation.setVisible(project != null && ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length > 0);
  }

  protected VcsHistoryProvider getProvider(AbstractVcs activeVcs) {
    return activeVcs.getVcsHistoryProvider();
  }

  protected boolean isEnabled(VcsContext context) {
    FilePath[] selectedFiles = getSelectedFiles(context);
    if (selectedFiles == null) return false;
    if (selectedFiles.length != 1) return false;
    FilePath path = selectedFiles[0];
    Project project = context.getProject();
    if (project == null) return false;
    VirtualFile someVFile = path.getVirtualFile() != null ? path.getVirtualFile() : path.getVirtualFileParent();
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(someVFile);
    if (vcs == null) return false;
    VcsHistoryProvider vcsHistoryProvider = getProvider(vcs);
    if (vcsHistoryProvider == null) return false;
    if (selectedFiles[0].isDirectory() && (! vcsHistoryProvider.supportsHistoryForDirectories())) return false;
    final FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(someVFile);
    return fileStatus != FileStatus.ADDED && fileStatus != FileStatus.UNKNOWN;
  }

  protected static FilePath[] getSelectedFiles(VcsContext context) {
    ArrayList<FilePath> result = new ArrayList<FilePath>();
    VirtualFile[] virtualFileArray = context.getSelectedFiles();
    if (virtualFileArray != null) {
      for (VirtualFile virtualFile : virtualFileArray) {
        result.add(new FilePathImpl(virtualFile));
      }
    }

    File[] fileArray = context.getSelectedIOFiles();
    if (fileArray != null) {
      for (File file : fileArray) {
        VirtualFile parent = LocalFileSystem.getInstance().findFileByIoFile(file.getParentFile());
        if (parent != null) {
          final FilePathImpl child = new FilePathImpl(parent, file.getName(), false);
          if (! result.contains(child)) {
            result.add(child);
          }
        }
      }
    }
    return result.toArray(new FilePath[result.size()]);
  }

  protected void actionPerformed(VcsContext context) {
    FilePath path = getSelectedFiles(context)[0];
    Project project = context.getProject();
    VirtualFile someVFile = path.getVirtualFile() != null ? path.getVirtualFile() : path.getVirtualFileParent();
    AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(someVFile);
    assert activeVcs != null;
    VcsHistoryProvider vcsHistoryProvider = getProvider(activeVcs);
    AbstractVcsHelper.getInstance(project).showFileHistory(vcsHistoryProvider, activeVcs.getAnnotationProvider(), path, null, activeVcs);
  }



  protected boolean forceSyncUpdate(final AnActionEvent e) {
    return true;
  }
}
