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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.checkin.*;
import com.intellij.openapi.vcs.checkin.changeListBasedCheckin.CommitChangeOperation;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vcs.versions.AbstractRevisions;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.*;
import java.util.List;

public class SvnCheckinEnvironment implements CheckinEnvironment {
  private final SvnVcs mySvnVcs;
  //TODO lesya
  private KeepLocksComponent myKeepLocksComponent;

  public boolean showCheckinDialogInAnyCase() {
    return false;
  }

  public SvnCheckinEnvironment(SvnVcs svnVcs) {
    mySvnVcs = svnVcs;
  }

  public RevisionsFactory getRevisionsFactory() {
    return new SvnRevisionsFactory(mySvnVcs);
  }

  public RollbackProvider createRollbackProviderOn(AbstractRevisions[] selectedRevisions, final boolean containsExcluded) {
    return new SvnRollbackProvider(selectedRevisions, mySvnVcs);
  }

  public DifferenceType[] getAdditionalDifferenceTypes() {
    return new DifferenceType[0];
  }

  public ColumnInfo[] getAdditionalColumns(int index) {
    return new ColumnInfo[0];
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinProject(Refreshable panel) {
    return new KeepLocksComponent(panel, true);
  }

  public RefreshableOnComponent createAdditionalOptionsPanelForCheckinFile(Refreshable panel) {
    return new KeepLocksComponent(panel, false);
  }

  public RefreshableOnComponent createAdditionalOptionsPanel(Refreshable panel, boolean checkinProject) {
    return null;
  }

  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return null;
  }

  public void onRefreshFinished() {
  }

  public void onRefreshStarted() {
  }

  public AnAction[] getAdditionalActions(int index) {
    return new AnAction[] {new MarkResolvedAction()};
  }

  private class MarkResolvedAction extends AnAction {
    public MarkResolvedAction() {
      super(SvnBundle.message("action.name.mark.resolved"), SvnBundle.message("mark.resolved.action.description"), IconLoader.getIcon("/actions/submit2.png"));
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
      DiffTreeNode[] presentableElements = (DiffTreeNode[])e.getDataContext().getData(
            DiffTreeNode.TREE_NODES);

      Presentation presentation = e.getPresentation();

      if ((presentableElements == null) || (presentableElements.length == 0)){
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

  public String getHelpId() {
    return null;
  }

  public List<VcsException> commit(CheckinProjectDialogImplementer dialog, Project project) {
    return commitInt(collectFilePaths(dialog.getCheckinProjectPanel().getCheckinOperations(this)),
                     dialog.getPreparedComment(this), true, false);
  }

  public List<VcsException> commit(FilePath[] roots, Project project, String preparedComment) {
    return commitInt(collectPaths(roots), preparedComment, false, true);
  }


  private List<VcsException> commitInt(List<String> paths, final String comment, final boolean force, final boolean recursive) {
    final List<VcsException> exception = new ArrayList<VcsException>();
    final Map<File, Collection<File>> committables = getCommitables(paths);

    final SVNCommitClient committer;
    try {
      committer = mySvnVcs.createCommitClient();
    }
    catch (SVNException e) {
      exception.add(new VcsException(e));
      return exception;
    }

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
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
    return exception;
  }

  private void doCommit(Map<File, Collection<File>> committables,
                        ProgressIndicator progress,
                        SVNCommitClient committer,
                        String comment,
                        boolean force,
                        boolean recursive,
                        List<VcsException> exception) {
    for (final File root : committables.keySet()) {
      Collection<File> files = committables.get(root);
      if (files.isEmpty()) {
        continue;
      }
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.committing.changes.below", root.getAbsolutePath()));
      }

      File[] filesArray = files.toArray(new File[files.size()]);
      boolean keepLocks = myKeepLocksComponent != null && myKeepLocksComponent.isKeepLocks();
      try {
        SVNCommitInfo result = committer.doCommit(filesArray, keepLocks, comment, force, recursive);
        if (result != SVNCommitInfo.NULL && result.getNewRevision() >= 0) {

          /*
          final String lastMergedRevision = SvnConfiguration.getInstance(mySvnVcs.getProject()).LAST_MERGED_REVISION;
          if (force && lastMergedRevision != null) {
            try {
              mySvnVcs.createWCClient().doSetRevisionProperty(root, SVNRevision.parse(lastMergedRevision),
                                                              "idea.last.integrated.revision", "true", true,
                                                              ISVNPropertyHandler.NULL);

              final SVNWCClient wcClient = mySvnVcs.createWCClient();
              final SVNPropertyData svnPropertyData =
                wcClient.doGetProperty(root, "idea.last.integrated.revision", SVNRevision.HEAD, SVNRevision.HEAD, true);
            }
            catch (SVNException e) {
              //ignore
            }
          }
          */

          SvnConfiguration.getInstance(mySvnVcs.getProject()).LAST_MERGED_REVISION = null;

          WindowManager.getInstance().getStatusBar(mySvnVcs.getProject()).setInfo(
            SvnBundle.message("status.text.committed.revision", result.getNewRevision()));
        }
      }
      catch (SVNException e) {
        exception.add(new VcsException(e));
      }
    }
  }

  private Map<File, Collection<File>> getCommitables(List<String> paths) {
    Map<File, Collection<File>> result = new HashMap<File, Collection<File>>();
    for (String path : paths) {
      File file = new File(path).getAbsoluteFile();
      File parent = file;
      if (file.isFile()) {
        parent = file.getParentFile();
      }
      File wcRoot = SVNWCUtil.getWorkingCopyRoot(parent, true);
      if (!result.containsKey(wcRoot)) {
        result.put(wcRoot, new ArrayList<File>());
      }
      result.get(wcRoot).add(file);
    }
    return result;
  }

  private List<String> collectPaths(FilePath[] roots) {
    ArrayList<String> result = new ArrayList<String>();
    for (FilePath file : roots) {
      result.add(file.getPath());
    }
    return result;
  }

  private List<String> collectFilePaths(List<VcsOperation> checkinOperations) {
    ArrayList<String> result = new ArrayList<String>();
    for (final VcsOperation checkinOperation : checkinOperations) {
      CommitChangeOperation<SVNStatus> operation = (CommitChangeOperation<SVNStatus>)checkinOperation;
      result.add(operation.getFile().getAbsolutePath());
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
      myPanel.setBorder(new TitledBorder(SvnBundle.message("border.show.changes.dialog.subversion.group")));

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

      } else {
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
