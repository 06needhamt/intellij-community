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
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.actions.SvnMergeProvider;
import org.jetbrains.idea.svn.status.SvnStatusEnvironment;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.io.File;

public abstract class AbstractSvnUpdateIntegrateEnvironment implements UpdateEnvironment {
  protected final SvnVcs myVcs;

  protected AbstractSvnUpdateIntegrateEnvironment(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public void fillGroups(UpdatedFiles updatedFiles) {
  }

  public UpdateSession updateDirectories(final FilePath[] contentRoots,
                                         final UpdatedFiles updatedFiles,
                                         final ProgressIndicator progressIndicator)
    throws ProcessCanceledException {

    final ArrayList<VcsException> exceptions = new ArrayList<VcsException>();
    ISVNEventHandler eventHandler = new UpdateEventHandler(myVcs, progressIndicator, updatedFiles);

    boolean totalUpdate = true;
    AbstractUpdateIntegrateCrawler crawler = createCrawler(eventHandler, totalUpdate, exceptions, updatedFiles);

    Collection<File> updatedRoots = new HashSet<File>();
    for (FilePath contentRoot : contentRoots) {
      if (progressIndicator != null && progressIndicator.isCanceled()) {
        throw new ProcessCanceledException();
      }
      Collection<File> roots = SvnUtil.crawlWCRoots(contentRoot.getIOFile(), crawler, progressIndicator);
      updatedRoots.addAll(roots);
    }
    if (updatedRoots.isEmpty()) {
      Messages.showErrorDialog(myVcs.getProject(), SvnBundle.message("message.text.update.no.directories.found"), SvnBundle.message("messate.text.update.error"));
    }

    final Collection<String> conflictedFiles = updatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).getFiles();
    return new UpdateSessionAdapter(exceptions, false) {
      public void onRefreshFilesCompleted() {
        for(FilePath contentRoot: contentRoots) {
          // update switched/ignored status of directories
          VcsDirtyScopeManager.getInstance(myVcs.getProject()).fileDirty(contentRoot);
        }
        if (conflictedFiles != null && !conflictedFiles.isEmpty() && !isDryRun()) {
          List<VirtualFile> vfFiles = new ArrayList<VirtualFile>();
          for (final String conflictedFile : conflictedFiles) {
            @NonNls final String path = "file://" + conflictedFile.replace(File.separatorChar, '/');
            VirtualFile vf = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
              @Nullable public VirtualFile compute() {
                return VirtualFileManager.getInstance().findFileByUrl(path);
              }

            });
            if (vf != null) {
              // refresh base directory so that conflict files should be detected
              vf.getParent().refresh(true, false);
              VcsDirtyScopeManager.getInstance(myVcs.getProject()).fileDirty(vf);
              if (ProjectLevelVcsManager.getInstance(myVcs.getProject()).getVcsFor(vf).equals(myVcs)) {
                vfFiles.add(vf);
              }
            }
          }
          if (!vfFiles.isEmpty()) {
            AbstractVcsHelper.getInstance(myVcs.getProject()).showMergeDialog(vfFiles,
                                                                              new SvnMergeProvider(myVcs.getProject()),
                                                                              null);
          }
        }
      }

    };
  }

  protected boolean isDryRun() {
    return false;
  }

  protected abstract AbstractUpdateIntegrateCrawler createCrawler(ISVNEventHandler eventHandler,
                                                 boolean totalUpdate,
                                                 ArrayList<VcsException> exceptions, UpdatedFiles updatedFiles);

  public abstract Configurable createConfigurable(Collection<FilePath> collection);

  private static class UpdateEventHandler implements ISVNEventHandler {
    private final ProgressIndicator myProgressIndicator;
    private final UpdatedFiles myUpdatedFiles;
    private int myExternalsCount;
    private SvnVcs myVCS;
    @NonNls public static final String SKIP_ID = "skip";

    public UpdateEventHandler(SvnVcs vcs, ProgressIndicator progressIndicator, UpdatedFiles updatedFiles) {
      myProgressIndicator = progressIndicator;
      myUpdatedFiles = updatedFiles;
      myVCS = vcs;
      myExternalsCount = 1;
    }

    public void handleEvent(SVNEvent event, double progress) {
      if (event == null || event.getFile() == null) {
        return;
      }
      String path = event.getFile().getAbsolutePath();
      String displayPath = event.getFile().getName();
      String text2 = null;
      String text = null;
      if (event.getAction() == SVNEventAction.UPDATE_ADD ||
          event.getAction() == SVNEventAction.ADD) {
        text2 = SvnBundle.message("progress.text2.added", displayPath);
        if (myUpdatedFiles.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID).getFiles().contains(path)) {
          myUpdatedFiles.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID).getFiles().remove(path);
          if (myUpdatedFiles.getGroupById(SvnStatusEnvironment.REPLACED_ID) == null) {
            myUpdatedFiles.registerGroup(SvnStatusEnvironment.createFileGroup(SvnBundle.message("status.group.name.replaced"), SvnStatusEnvironment.REPLACED_ID));
          }
          myUpdatedFiles.getGroupById(SvnStatusEnvironment.REPLACED_ID).add(path);
        } else {
          myUpdatedFiles.getGroupById(FileGroup.CREATED_ID).add(path);
        }
      }
      else if (event.getAction() == SVNEventAction.UPDATE_NONE) {
        // skip it
        return;
      }
      else if (event.getAction() == SVNEventAction.UPDATE_DELETE) {
        text2 = SvnBundle.message("progress.text2.deleted", displayPath);
        myUpdatedFiles.getGroupById(FileGroup.REMOVED_FROM_REPOSITORY_ID).add(path);
      }
      else if (event.getAction() == SVNEventAction.UPDATE_UPDATE) {
        if (event.getContentsStatus() == SVNStatusType.CONFLICTED || event.getPropertiesStatus() == SVNStatusType.CONFLICTED) {
          myUpdatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).add(path);
          text2 = SvnBundle.message("progress.text2.conflicted", displayPath);
        }
        else if (event.getContentsStatus() == SVNStatusType.MERGED || event.getPropertiesStatus() == SVNStatusType.MERGED) {
          text2 = SvnBundle.message("progres.text2.merged", displayPath);
          myUpdatedFiles.getGroupById(FileGroup.MERGED_ID).add(path);
        }
        else if (event.getContentsStatus() == SVNStatusType.CHANGED || event.getPropertiesStatus() == SVNStatusType.CHANGED) {
          text2 = SvnBundle.message("progres.text2.updated", displayPath);
          myUpdatedFiles.getGroupById(FileGroup.UPDATED_ID).add(path);
        }
        else if (event.getContentsStatus() == SVNStatusType.UNCHANGED && event.getPropertiesStatus() == SVNStatusType.UNCHANGED) {
          text2 = SvnBundle.message("progres.text2.updated", displayPath);
        }
        else {
          text2 = "";
          myUpdatedFiles.getGroupById(FileGroup.UNKNOWN_ID).add(path);
        }
      }
      else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
        myExternalsCount++;
        if (myUpdatedFiles.getGroupById(SvnStatusEnvironment.EXTERNAL_ID) == null) {
          myUpdatedFiles.registerGroup(new FileGroup(SvnBundle.message("status.group.name.externals"),
                                                     SvnBundle.message("status.group.name.externals"),
                                                     false, SvnStatusEnvironment.EXTERNAL_ID, true));
        }
        myUpdatedFiles.getGroupById(SvnStatusEnvironment.EXTERNAL_ID).add(path);
        text = SvnBundle.message("progress.text.updating.external.location", event.getFile().getAbsolutePath());
      }
      else if (event.getAction() == SVNEventAction.RESTORE) {
        text2 = SvnBundle.message("progress.text2.restored.file", displayPath);
        myUpdatedFiles.getGroupById(FileGroup.RESTORED_ID).add(path);
      }
      else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED && event.getRevision() >= 0) {
        myExternalsCount--;
        text2 = SvnBundle.message("progres.text2.updated.to.revision", event.getRevision());
        if (myExternalsCount == 0) {
          myExternalsCount = 1;
          WindowManager.getInstance().getStatusBar(myVCS.getProject()).setInfo(
            SvnBundle.message("status.text.updated.to.revision", event.getRevision()));
        }
      }
      else if (event.getAction() == SVNEventAction.SKIP) {
        text2 = SvnBundle.message("progress.text2.skipped.file", displayPath);
        if (myUpdatedFiles.getGroupById(SKIP_ID) == null) {
          myUpdatedFiles.registerGroup(new FileGroup(SvnBundle.message("update.group.name.skipped"),
                                                     SvnBundle.message("update.group.name.skipped"), false, SKIP_ID, true));
        }
        myUpdatedFiles.getGroupById(SKIP_ID).add(path);
      }

      if (myProgressIndicator != null) {
        if (text != null) {
          myProgressIndicator.setText(text);
        }
        if (text2 != null) {
          myProgressIndicator.setText2(text2);
        }
      }
    }

    public void checkCancelled() throws SVNCancelException {
      if (myProgressIndicator != null) {
        myProgressIndicator.checkCanceled();
        if (myProgressIndicator.isCanceled()) {
          SVNErrorManager.cancel(SvnBundle.message("exception.text.update.operation.cancelled"));
        }
      }
    }
  }


}
