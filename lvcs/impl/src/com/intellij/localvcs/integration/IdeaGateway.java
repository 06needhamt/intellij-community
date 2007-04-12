package com.intellij.localvcs.integration;

import com.intellij.localvcs.core.Clock;
import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class IdeaGateway {
  protected Project myProject;
  protected FileFilter myFileFilter;

  public IdeaGateway(Project p) {
    myProject = p;
    myFileFilter = createFileFilter();
  }

  protected FileFilter createFileFilter() {
    FileIndex fi = ProjectRootManager.getInstance(myProject).getFileIndex();
    FileTypeManager tm = FileTypeManager.getInstance();
    return new FileFilter(fi, tm);
  }

  public Project getProject() {
    return myProject;
  }

  // todo get rid of file filter
  public FileFilter getFileFilter() {
    return myFileFilter;
  }

  public boolean askForProceed(String s) {
    int result = Messages.showYesNoDialog(myProject, s, "Confirmation", Messages.getWarningIcon());
    return result == 0 ? true : false;
  }

  public <T> T performCommandInsideWriteAction(final String name, final Callable<T> c) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<T>() {
      public T compute() {
        return performCommand(name, c);
      }
    });
  }

  private <T> T performCommand(String name, final Callable<T> c) {
    final List<T> result = new ArrayList<T>();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          result.add(c.call());
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }, name, null);
    return result.get(0);
  }

  public boolean ensureFilesAreWritable(VirtualFile... ff) {
    ReadonlyStatusHandler h = ReadonlyStatusHandler.getInstance(myProject);
    return !h.ensureFilesWritable(ff).hasReadonlyFiles();
  }

  public VirtualFile findVirtualFile(String path) {
    return getFileSystem().findFileByPath(path);
  }

  public VirtualFile findOrCreateDirectory(String path) {
    File f = new File(path);
    if (!f.exists()) f.mkdirs();

    getFileSystem().refresh(false);
    return getFileSystem().findFileByPath(path);
  }

  public byte[] getPhysicalContent(VirtualFile f) throws IOException {
    return getFileSystem().physicalContentsToByteArray(f);
  }

  public void registerUnsavedDocuments(ILocalVcs vcs) {
    for (Document d : getUnsavedDocuments()) {
      VirtualFile f = getDocumentFile(d);
      if (!getFileFilter().isAllowedAndUnderContentRoot(f)) continue;
      vcs.changeFileContent(f.getPath(), d.getText().getBytes(), Clock.getCurrentTimestamp());
    }
  }

  protected Document[] getUnsavedDocuments() {
    return getDocManager().getUnsavedDocuments();
  }

  protected VirtualFile getDocumentFile(Document d) {
    return getDocManager().getFile(d);
  }

  private LocalFileSystem getFileSystem() {
    return LocalFileSystem.getInstance();
  }

  private FileDocumentManager getDocManager() {
    return FileDocumentManager.getInstance();
  }
}
