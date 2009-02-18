package com.intellij.openapi.vcs.changes.ui;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RollbackWorker {
  private final Project myProject;
  private final List<VcsException> myExceptions;

  private ProgressIndicator myIndicator;

  public RollbackWorker(final Project project, final boolean synchronous) {
    myProject = project;
    myExceptions = new ArrayList<VcsException>(0);
  }

  public void doRollback(final Collection<Change> changes, final boolean deleteLocallyAddedFiles, final Runnable afterVcsRefreshInAwt,
                         final String localHistoryActionName) {
    final ChangeListManager changeListManager = ChangeListManagerImpl.getInstance(myProject);
    final Runnable notifier = changeListManager.prepareForChangeDeletion(changes);
    final Runnable afterRefresh = new Runnable() {
      public void run() {
        changeListManager.invokeAfterUpdate(new Runnable() {
          public void run() {
            notifier.run();
            if (afterVcsRefreshInAwt != null) {
              afterVcsRefreshInAwt.run();
            }
          }
        }, InvokeAfterUpdateMode.SILENT, "Refresh change lists after update", ModalityState.current());
      }
    };

    Runnable rollbackAction = new MyRollbackRunnable(changes, deleteLocallyAddedFiles, afterRefresh, localHistoryActionName);

    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(rollbackAction, VcsBundle.message("changes.action.rollback.text"), true, myProject);
    }
    else {
      rollbackAction.run();
    }
    ((ChangeListManagerImpl) changeListManager).showLocalChangesInvalidated();
  }

  private class MyRollbackRunnable implements Runnable {
    private final Collection<Change> myChanges;
    private final boolean myDeleteLocallyAddedFiles;
    private final Runnable myAfterRefresh;
    private final String myLocalHistoryActionName;

    private MyRollbackRunnable(final Collection<Change> changes,
                               final boolean deleteLocallyAddedFiles,
                               final Runnable afterRefresh,
                               final String localHistoryActionName) {
      myChanges = changes;
      myDeleteLocallyAddedFiles = deleteLocallyAddedFiles;
      myAfterRefresh = afterRefresh;
      myLocalHistoryActionName = localHistoryActionName;
    }

    public void run() {
      myIndicator = ProgressManager.getInstance().getProgressIndicator();

      final List<Change> changesToRefresh = new ArrayList<Change>();
      try {
        ChangesUtil.processChangesByVcs(myProject, myChanges, new ChangesUtil.PerVcsProcessor<Change>() {
          public void process(AbstractVcs vcs, List<Change> changes) {
            final RollbackEnvironment environment = vcs.getRollbackEnvironment();
            if (environment != null) {
              changesToRefresh.addAll(changes);

              if (myIndicator != null) {
                myIndicator.setText(vcs.getDisplayName() + ": doing rollback...");
                myIndicator.setIndeterminate(false);
                myIndicator.checkCanceled();
              }
              environment.rollbackChanges(changes, myExceptions, new RollbackProgressModifier(changes.size(), myIndicator));
              if (myIndicator != null) {
                myIndicator.setText2("");
                myIndicator.checkCanceled();
              }

              if (myExceptions.isEmpty() && myDeleteLocallyAddedFiles) {
                deleteAddedFilesLocally(changes);
              }
            }
          }
        });
      }
      catch (ProcessCanceledException e) {
        // still do refresh
      }

      if (myIndicator != null) {
        myIndicator.startNonCancelableSection();
        myIndicator.setIndeterminate(true);
        myIndicator.setText2("");
        myIndicator.setText(VcsBundle.message("progress.text.synchronizing.files"));
      }

      doRefresh(myProject, changesToRefresh);

      AbstractVcsHelper.getInstanceChecked(myProject).showErrors(myExceptions, VcsBundle.message("changes.action.rollback.text"));
    }

    private void doRefresh(final Project project, final List<Change> changesToRefresh) {
      final String actionName = VcsBundle.message("changes.action.rollback.text");
      final LocalHistoryAction action = LocalHistory.startAction(project, actionName);

      final Runnable forAwtThread = new Runnable() {
        public void run() {
          action.finish();
          LocalHistory.putSystemLabel(project, (myLocalHistoryActionName == null) ? actionName : myLocalHistoryActionName);
          final VcsDirtyScopeManager manager = VcsDirtyScopeManager.getInstance(project);
          for (Change change : changesToRefresh) {
            if ((! change.isIsReplaced()) && Comparing.equal(change.getBeforeRevision(), change.getAfterRevision())) {
              manager.fileDirty(change.getBeforeRevision().getFile());
            } else {
              if (change.getBeforeRevision() != null) {
                final FilePath parent = change.getBeforeRevision().getFile().getParentPath();
                if (parent != null) {
                  manager.dirDirtyRecursively(parent);
                }
              }
              if (change.getAfterRevision() != null) {
                final FilePath parent = change.getAfterRevision().getFile().getParentPath();
                if (parent != null) {
                  manager.dirDirtyRecursively(parent);
                }
              }
            }
          }

          myAfterRefresh.run();
        }
      };

      RefreshVFsSynchronously.updateChangesForRollback(changesToRefresh);

      ApplicationManager.getApplication().invokeLater(forAwtThread);
    }

    private void deleteAddedFilesLocally(final List<Change> changes) {
      if (myIndicator != null) {
        myIndicator.setText("Deleting added files locally...");
        myIndicator.setFraction(0);
      }
      final int changesSize = changes.size();
      for (int i = 0; i < changesSize; i++) {
        final Change c = changes.get(i);
        if (c.getType() == Change.Type.NEW) {
          ContentRevision rev = c.getAfterRevision();
          assert rev != null;
          final File ioFile = rev.getFile().getIOFile();
          if (myIndicator != null) {
            myIndicator.setText2(ioFile.getAbsolutePath());
            myIndicator.setFraction(((double) i) / changesSize);
          }
          FileUtil.delete(ioFile);
        }
      }
      if (myIndicator != null) {
        myIndicator.setText2("");
      }
    }
  }

}
