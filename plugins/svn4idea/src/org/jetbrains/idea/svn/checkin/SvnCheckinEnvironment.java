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
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.DiffTreeNode;
import com.intellij.openapi.vcs.checkin.RevisionsFactory;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.*;
import java.util.List;

public class SvnCheckinEnvironment implements CheckinEnvironment {
  private final SvnVcs mySvnVcs;

  public boolean showCheckinDialogInAnyCase() {
    return false;
  }

  public SvnCheckinEnvironment(SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  public RevisionsFactory getRevisionsFactory() {
    return new SvnRevisionsFactory(mySvnVcs);
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinProject(Refreshable panel) {
    return new KeepLocksComponent(panel, true);
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinFile(Refreshable panel) {
    return new KeepLocksComponent(panel, false);
  }

  @Nullable
  public RefreshableOnComponent createAdditionalOptionsPanel(Refreshable panel, boolean checkinProject) {
    return null;
  }

  @Nullable
  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  public void onRefreshFinished() {
  }

  public void onRefreshStarted() {
  }

  private class MarkResolvedAction extends AnAction {
    public MarkResolvedAction() {
      super(SvnBundle.message("action.name.mark.resolved"), SvnBundle.message("mark.resolved.action.description"),
            IconLoader.getIcon("/actions/submit2.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      final VcsContext context = PeerFactory.getInstance().getVcsContextFactory().createContextOn(e);

      final Refreshable refreshableDialog = context.getRefreshableDialog();
      if (refreshableDialog != null) {
        refreshableDialog.saveState();
      }

      final FilePath[] pathsArray = context.getSelectedFilePaths();

      try {
        SVNWCClient wcClient = mySvnVcs.createWCClient();
        for (FilePath aPathsArray : pathsArray) {
          wcClient.doResolve(aPathsArray.getIOFile(), false);
        }
      }
      catch (SVNException svnEx) {
        Messages.showErrorDialog(SvnBundle.message("cannot.mark.file.as.resolved.error.message", svnEx.getLocalizedMessage()),
                                 SvnBundle.message("mark.resolved.dialog.title"));
      }
      finally {
        FileStatusManager.getInstance(mySvnVcs.getProject()).fileStatusesChanged();

        if (refreshableDialog != null) {
          refreshableDialog.refresh();
        }

      }

    }

    public void update(AnActionEvent e) {
      DiffTreeNode[] presentableElements = (DiffTreeNode[])e.getDataContext().getData(DiffTreeNode.TREE_NODES);

      Presentation presentation = e.getPresentation();

      if ((presentableElements == null) || (presentableElements.length == 0)) {
        presentation.setEnabled(false);
        presentation.setVisible(false);
        return;
      }

      presentation.setEnabled(true);
      presentation.setVisible(true);

      for (DiffTreeNode presentableElement : presentableElements) {
        if (presentableElement.getDifference() != SvnRevisions.CONFLICTED_DIFF_TYPE) {
          presentation.setEnabled(false);
          presentation.setVisible(false);
          return;
        }
      }

    }
  }

  public String prepareCheckinMessage(String text) {
    return text;
  }

  @Nullable
  public String getHelpId() {
    return null;
  }


  public List<VcsException> commitInt(List<String> paths, final String comment, final boolean force, final boolean recursive) {
    final List<VcsException> exception = new ArrayList<VcsException>();
    final Collection<File> committables = getCommitables(paths);

    final SVNCommitClient committer;
    try {
      committer = mySvnVcs.createCommitClient();
    }
    catch (SVNException e) {
      exception.add(new VcsException(e));
      return exception;
    }

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    final Collection<VirtualFile> deletedFiles = new ArrayList<VirtualFile>();
    if (progress != null) {
      committer.setEventHandler(new ISVNEventHandler() {
        public void handleEvent(SVNEvent event, double p) {
          String path = event.getFile() != null ? event.getFile().getName() : event.getPath();
          if (path == null) {
            return;
          }
          if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
            progress.setText2(SvnBundle.message("progress.text2.adding", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
            final String filePath = "file://" + event.getFile().getAbsolutePath().replace(File.separatorChar, '/');
            VirtualFile vf = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
              @Nullable public VirtualFile compute() {
                return VirtualFileManager.getInstance().findFileByUrl(filePath);
              }
            });
            if (vf != null) {
              deletedFiles.add(vf);
            }
            progress.setText2(SvnBundle.message("progress.text2.deleting", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_MODIFIED) {
            progress.setText2(SvnBundle.message("progress.text2.sending", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_REPLACED) {
            progress.setText2(SvnBundle.message("progress.text2.replacing", path));
          }
          else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
            progress.setText2(SvnBundle.message("progress.text2.transmitting.delta", path));
          }
        }

        public void checkCancelled() {
        }
      });
    }

    if (progress != null) {
      doCommit(committables, progress, committer, comment, force, recursive, exception);
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator p = ProgressManager.getInstance().getProgressIndicator();
          doCommit(committables, p, committer, comment, force, recursive, exception);
        }
      }, SvnBundle.message("progress.title.commit"), false, mySvnVcs.getProject());
    }
    else {
      doCommit(committables, progress, committer, comment, force, recursive, exception);
    }

    for(VirtualFile f : deletedFiles) {
      f.putUserData(VirtualFile.REQUESTOR_MARKER, this);
    }
    return exception;
  }

  private void doCommit(Collection<File> committables,
                        ProgressIndicator progress,
                        SVNCommitClient committer,
                        String comment,
                        boolean force,
                        boolean recursive,
                        List<VcsException> exception) {
    if (committables.isEmpty()) {
      return;
    }
    File[] pathsToCommit = (File[])committables.toArray(new File[committables.size()]);
    boolean keepLocks = SvnConfiguration.getInstance(mySvnVcs.getProject()).isKeepLocks();
    SVNCommitPacket[] commitPackets = null;
    SVNCommitInfo[] results;
    try {
      commitPackets = committer.doCollectCommitItems(pathsToCommit, keepLocks, force, recursive, true);
      results = committer.doCommit(commitPackets, keepLocks, comment);
      commitPackets = null;
    }
    catch (SVNException e) {
      // exception on collecting commitables.
      exception.add(new VcsException(e));
      return;
    }
    finally {
      if (commitPackets != null) {
        for (int i = 0; i < commitPackets.length; i++) {
          SVNCommitPacket commitPacket = commitPackets[i];
          try {
            commitPacket.dispose();
          }
          catch (SVNException e) {
            //
          }
        }
      }
    }
    StringBuffer committedRevisions = new StringBuffer();
    for (int i = 0; i < results.length; i++) {
      SVNCommitInfo result = results[i];
      if (result.getErrorMessage() != null) {
        exception.add(new VcsException(result.getErrorMessage().getFullMessage()));
      }
      else if (result != SVNCommitInfo.NULL && result.getNewRevision() > 0) {
        if (committedRevisions.length() > 0) {
          committedRevisions.append(", ");
        }
        committedRevisions.append(result.getNewRevision());
      }
    }
    if (committedRevisions.length() > 0) {
      WindowManager.getInstance().getStatusBar(mySvnVcs.getProject()).setInfo(
        SvnBundle.message("status.text.committed.revision", committedRevisions));
    }
  }

  private Collection<File> getCommitables(List<String> paths) {
    Collection<File> result = new HashSet<File>();
    SVNStatusClient statusClient = null;
    try {
      statusClient = mySvnVcs.createStatusClient();
    }
    catch (SVNException e) {
      //
    }
    for (Iterator<String> p = paths.iterator(); p.hasNext();) {
      String path = p.next();
      File file = new File(path).getAbsoluteFile();
      result.add(file);
      if (file.getParentFile() != null) {
        addParents(statusClient, file.getParentFile(), result);
      }
    }
    return result;
  }

  private static void addParents(SVNStatusClient statusClient, File file, Collection<File> files) {
    SVNStatus status;
    try {
      status = statusClient.doStatus(file, false);
    }
    catch (SVNException e) {
      return;
    }
    if (status != null &&
        (status.getContentsStatus() == SVNStatusType.STATUS_ADDED ||
         status.getContentsStatus() == SVNStatusType.STATUS_REPLACED)) {
      // file should be added
      files.add(file);
      file = file.getParentFile();
      if (file != null) {
        addParents(statusClient, file, files);
      }
    }
  }

  public static List<String> collectPaths(FilePath[] roots) {
    ArrayList<String> result = new ArrayList<String>();
    for (FilePath file : roots) {
      // if file is scheduled for addition[r] and its parent is also scheduled for additio[r] ->
      // then add parents till versioned file is met. same for 'copied' files.
      result.add(file.getPath());
    }
    return result;
  }

  public String getCheckinOperationName() {
    return SvnBundle.message("checkin.operation.name");
  }

  private class KeepLocksComponent implements RefreshableOnComponent {
    private JCheckBox myKeepLocksBox;
    private boolean myIsKeepLocks;
    private JPanel myPanel;

    private final JCheckBox myShowUnresolvedFileCheckBox;

    public KeepLocksComponent(final Refreshable panel, boolean showShowUnresolvedCheckBox) {

      myPanel = new JPanel(new BorderLayout());
      myKeepLocksBox = new JCheckBox(SvnBundle.message("checkbox.chckin.keep.files.locked"));
      myKeepLocksBox.setSelected(myIsKeepLocks);

      myPanel.add(myKeepLocksBox, BorderLayout.CENTER);

      if (showShowUnresolvedCheckBox) {
        myShowUnresolvedFileCheckBox = new JCheckBox(SvnBundle.message("commit.dialog.setings.show.unresolved.checkbox"));

        myPanel.add(myShowUnresolvedFileCheckBox, BorderLayout.NORTH);

        myShowUnresolvedFileCheckBox.addItemListener(new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            final SvnConfiguration conf = SvnConfiguration.getInstance(mySvnVcs.getProject());
            if (myShowUnresolvedFileCheckBox.isSelected() != conf.PROCESS_UNRESOLVED) {

              panel.saveState();
              conf.PROCESS_UNRESOLVED = myShowUnresolvedFileCheckBox.isSelected();
              panel.refresh();
            }
          }
        });

      }
      else {
        myShowUnresolvedFileCheckBox = null;
      }

    }

    public JComponent getComponent() {
      return myPanel;
    }

    public boolean isKeepLocks() {
      return myKeepLocksBox != null && myKeepLocksBox.isSelected();
    }

    public void refresh() {
      if (myShowUnresolvedFileCheckBox != null) {
        myShowUnresolvedFileCheckBox.setSelected(SvnConfiguration.getInstance(mySvnVcs.getProject()).PROCESS_UNRESOLVED);
      }
    }

    public void saveState() {
      mySvnVcs.getSvnConfiguration().setKeepLocks(isKeepLocks());
    }

    public void restoreState() {
      myIsKeepLocks = mySvnVcs.getSvnConfiguration().isKeepLocks();
    }
  }

}
