/**
 * @copyright
 * ====================================================================
 * Copyright (c) 2003-2004 QintSoft.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://subversion.tigris.org/license-1.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 *
 * This software consists of voluntary contributions made by many
 * individuals.  For exact contribution history, see the revision
 * history and logs, available at http://svnup.tigris.org/.
 * ====================================================================
 * @endcopyright
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SvnFileSystemListener implements LocalFileOperationsHandler, CommandListener {
  private static class AddedFileInfo {
    private final Project myProject;
    private final VirtualFile myDir;
    private final String myName;

    public AddedFileInfo(final Project project, final VirtualFile dir, final String name) {
      myProject = project;
      myDir = dir;
      myName = name;
    }
  }

  private static class DeletedFileInfo {
    private final Project myProject;
    private final File myFile;

    public DeletedFileInfo(final Project project, final File file) {
      myProject = project;
      myFile = file;
    }
  }

  private List<AddedFileInfo> myAddedFiles = new ArrayList<AddedFileInfo>();
  private List<DeletedFileInfo> myDeletedFiles = new ArrayList<DeletedFileInfo>();

  @Nullable
  public File copy(final VirtualFile file, final VirtualFile toDir, final String copyName) throws IOException {
    SvnVcs vcs = getVCS(toDir);
    if (vcs == null) {
      vcs = getVCS(file);
    }
    if (vcs == null) {
      return null;
    }

    File srcFile = new File(file.getPath());
    File destFile = new File(new File(toDir.getPath()), copyName);
    if (!SVNWCUtil.isVersionedDirectory(destFile.getParentFile())) {
      return null;
    }

    if (!SVNWCUtil.isVersionedDirectory(srcFile.getParentFile())) {
      myAddedFiles.add(new AddedFileInfo(vcs.getProject(), toDir, copyName));
      return null;
    }

    SVNCopyClient client = new SVNCopyClient(null, vcs.getSvnOptions());
    try {
      client.doCopy(srcFile, SVNRevision.WORKING, destFile, false, false);
    }
    catch (SVNException e) {
      return null;
    }
    return destFile;
  }

  public boolean move(VirtualFile file, VirtualFile toDir) throws IOException {
    File srcFile = getIOFile(file);
    File dstFile = new File(getIOFile(toDir), file.getName());

    SvnVcs vcs = getVCS(toDir);
    if (vcs == null) {
      vcs = getVCS(file);
    }
    if (vcs == null) {
      return false;
    }
    return move(vcs, srcFile, dstFile);
  }

  public boolean rename(VirtualFile file, String newName) throws IOException {
    File srcFile = getIOFile(file);
    File dstFile = new File(srcFile.getParentFile(), newName);
    SvnVcs vcs = getVCS(file);
    if (vcs != null) {
      return move(vcs, srcFile, dstFile);
    }
    return false;
  }

  private static boolean move(SvnVcs vcs, final File src, final File dst) {
    ISVNOptions options = vcs != null ? vcs.getSvnOptions() : null;
    SVNMoveClient mover = new SVNMoveClient(null, options);
    long srcTime = src.lastModified();
    try {
      if (isUndo(vcs)) {
        mover.undoMove(src, dst);
      }
      else {
        // if src is not under version control, do usual move.
        SVNStatus srcStatus = getFileStatus(src);
        if (srcStatus == null || srcStatus.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
                srcStatus.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL ||
                srcStatus.getContentsStatus() == SVNStatusType.STATUS_MISSING ||
                srcStatus.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED) {
            return false;
        }
        mover.doMove(src, dst);
      }
      dst.setLastModified(srcTime);
    }
    catch (SVNException e) {
      return false;
    }
    return true;
  }


  public boolean createFile(VirtualFile dir, String name) throws IOException {
    return createItem(dir, name, false);
  }

  public boolean createDirectory(VirtualFile dir, String name) throws IOException {
    return createItem(dir, name, true);
  }

  /**
   * delete file or directory (both 'undo' and 'do' modes)
   * unversioned: do nothing, return false
   * obstructed: do nothing, return false
   * external or wc root: do nothing, return false
   * missing: do nothing, return false
   * <p/>
   * versioned: schedule for deletion, return true
   * added: schedule for deletion (make unversioned), return true
   * copied, but not scheduled: schedule for deletion, return true
   * replaced: schedule for deletion, return true
   * <p/>
   * deleted: do nothing, return true (strange)
   */
  public boolean delete(VirtualFile file) throws IOException {
    File ioFile = getIOFile(file);
    if (!SVNWCUtil.isVersionedDirectory(ioFile.getParentFile())) {
      return false;
    }
    else {
        try {
        if (SVNWCUtil.isWorkingCopyRoot(ioFile)) {
          return false;
        }
        } catch (SVNException e) {
            //
        }
    }

    SVNStatus status = getFileStatus(ioFile);

    if (status == null ||
        status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED ||
        status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED ||
        status.getContentsStatus() == SVNStatusType.STATUS_MISSING ||
        status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL) {
      return false;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      return true;
    }
    else {
      SvnVcs vcs = getVCS(file);
      if (vcs != null) {
        myDeletedFiles.add(new DeletedFileInfo(vcs.getProject(), ioFile));
      }
      return false;
    }
  }

  /**
   * add file or directory:
   * <p/>
   * parent directory is:
   * unversioned: do nothing, return false
   * versioned:
   * entry is:
   * null: create entry, schedule for addition
   * missing: do nothing, return false
   * deleted, 'do' mode: try to create entry and it schedule for addition if kind is the same, otherwise do nothing, return false.
   * deleted: 'undo' mode: try to revert non-recursively, if kind is the same, otherwise do nothing, return false.
   * anything else: return false.
   */
  private boolean createItem(VirtualFile dir, String name, boolean directory) throws IOException {
    SvnVcs vcs = getVCS(dir);
    if (vcs == null) {
      return false;
    }
    File ioDir = getIOFile(dir);
    if (!SVNWCUtil.isVersionedDirectory(ioDir)) {
      return false;
    }
    ISVNOptions options = vcs.getSvnOptions();
    SVNWCClient wcClient = new SVNWCClient(null, options);
    File targetFile = new File(ioDir, name);
    SVNStatus status = getFileStatus(targetFile);

    if (status == null || status.getContentsStatus() == SVNStatusType.STATUS_NONE) {
      myAddedFiles.add(new AddedFileInfo(vcs.getProject(), dir, name));
      return false;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MISSING) {
      return false;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      SVNNodeKind kind = status.getKind();
      // kind differs.
      if ((directory && kind != SVNNodeKind.DIR) || (!directory && kind != SVNNodeKind.FILE)) {
        return false;
      }
      try {
        if (isUndo(vcs)) {
          wcClient.doRevert(targetFile, false);
          return true;
        }
        myAddedFiles.add(new AddedFileInfo(vcs.getProject(), dir, name));
        return false;
      }
      catch (SVNException e) {
        SVNFileUtil.deleteAll(targetFile, true);
        return false;
      }
    }
    return false;
  }

  public void commandStarted(CommandEvent event) {
  }

  public void beforeCommandFinished(CommandEvent event) {
  }

  public void commandFinished(CommandEvent event) {
    if (myAddedFiles.size() > 0) {
      processAddedFiles();
      myAddedFiles.clear();
    }
    if (myDeletedFiles.size() > 0) {
      processDeletedFiles();
      myDeletedFiles.clear();
    }
  }

  private void processAddedFiles() {
    MultiMap<Project, VirtualFile> addedVFiles = new MultiMap<Project, VirtualFile>();
    for(AddedFileInfo addedFileInfo: myAddedFiles) {
      VirtualFile addedFile = addedFileInfo.myDir.findChild(addedFileInfo.myName);
      if (addedFile != null) {
        final SVNStatus fileStatus = getFileStatus(new File(getIOFile(addedFileInfo.myDir), addedFileInfo.myName));
        if (fileStatus == null || fileStatus.getContentsStatus() != SVNStatusType.STATUS_IGNORED) {
          boolean isIgnored = ChangeListManager.getInstance(addedFileInfo.myProject).isIgnoredFile(addedFile);
          if (!isIgnored) {
            addedVFiles.putValue(addedFileInfo.myProject, addedFile);
          }
        }
      }
    }
    for(Project project: addedVFiles.keySet()) {
      SvnVcs vcs = SvnVcs.getInstance(project);
      final VcsShowConfirmationOption.Value value = vcs.getAddConfirmation().getValue();
      if (value != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
        final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);
        List<VirtualFile> vFiles = addedVFiles.get(project);
        Collection<VirtualFile> filesToProcess;
        if (value == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
          filesToProcess = vFiles;
        }
        else {
          filesToProcess = vcsHelper.selectFilesToProcess(vFiles, SvnBundle.message("confirmation.title.add.multiple.files"),
                                                          null,
                                                          SvnBundle.message("confirmation.title.add.file"),
                                                          SvnBundle.message("confirmation.text.add.file"),
                                                          vcs.getAddConfirmation());
        }
        if (filesToProcess != null) {
          List<VcsException> exceptions = new ArrayList<VcsException>();
          SVNWCClient wcClient = new SVNWCClient(null, vcs.getSvnOptions());
          for(VirtualFile file: filesToProcess) {
            File ioFile = new File(file.getPath());
            try {
              wcClient.doAdd(ioFile, false, false, false, false);
            }
            catch (SVNException e) {
              exceptions.add(new VcsException(e));
            }
          }
          if (!exceptions.isEmpty()) {
            vcsHelper.showErrors(exceptions, "Errors Adding Files");
          }
        }
      }
    }
  }

  private void processDeletedFiles() {
    MultiMap<Project, FilePath> deletedFiles = new MultiMap<Project, FilePath>();
    for(DeletedFileInfo deletedFileInfo: myDeletedFiles) {
      final FilePath filePath = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(deletedFileInfo.myFile);
      deletedFiles.putValue(deletedFileInfo.myProject, filePath);
    }
    for(Project project: deletedFiles.keySet()) {
      SvnVcs vcs = SvnVcs.getInstance(project);
      final VcsShowConfirmationOption.Value value = vcs.getDeleteConfirmation().getValue();
      if (value != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
        final AbstractVcsHelper vcsHelper = AbstractVcsHelper.getInstance(project);
        List<FilePath> filePaths = deletedFiles.get(project);
        Collection<FilePath> filesToProcess;
        if (value == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY) {
          filesToProcess = filePaths;
        }
        else {
          filesToProcess = vcsHelper.selectFilePathsToProcess(filePaths, SvnBundle.message("confirmation.title.delete.multiple.files"),
                                                              null,
                                                              SvnBundle.message("confirmation.title.delete.file"),
                                                              SvnBundle.message("confirmation.text.delete.file"),
                                                              vcs.getAddConfirmation());
        }
        if (filesToProcess != null) {
          List<VcsException> exceptions = new ArrayList<VcsException>();
          SVNWCClient wcClient = new SVNWCClient(null, vcs.getSvnOptions());
          for(FilePath file: filesToProcess) {
            File ioFile = new File(file.getPath());
            try {
              wcClient.doDelete(ioFile, true, false);
              VcsDirtyScopeManager.getInstance(project).fileDirty(file);
            }
            catch (SVNException e) {
              exceptions.add(new VcsException(e));
            }
          }
          if (!exceptions.isEmpty()) {
            vcsHelper.showErrors(exceptions, "Errors Deleting Files");
          }
        }
      }
    }
  }

  public void undoTransparentActionStarted() {
  }

  public void undoTransparentActionFinished() {
  }

  @Nullable
  private static SvnVcs getVCS(VirtualFile file) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; projects != null && i < projects.length; i++) {
      Project project = projects[i];
      ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
      if (rootManager != null) {
        Module module = rootManager.getFileIndex().getModuleForFile(file);
        if (module != null) {
          AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
          if (vcs instanceof SvnVcs) {
            return (SvnVcs)vcs;
          }
        }
      }
    }
    return null;
  }


  private static File getIOFile(VirtualFile vf) {
    return new File(vf.getPath()).getAbsoluteFile();
  }

  @Nullable
  private static SVNStatus getFileStatus(File file) {
    SVNStatusClient stClient = new SVNStatusClient(null, null);
    SVNStatus status;
    try {
      status = stClient.doStatus(file, false);
    }
    catch (SVNException e) {
      status = null;
    }
    return status;
  }

  private static boolean isUndo(SvnVcs vcs) {
    if (vcs == null || vcs.getProject() == null) {
      return false;
    }
    Project p = vcs.getProject();
    return UndoManager.getInstance(p).isUndoInProgress();
  }
}
