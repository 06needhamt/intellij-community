// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions;

import com.intellij.diff.DiffManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBaseContentRevision;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffRequest;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffRequest.PropertyContent;
import org.jetbrains.idea.svn.history.SvnLazyPropertyContentRevision;
import org.jetbrains.idea.svn.history.SvnRepositoryContentRevision;
import org.jetbrains.idea.svn.properties.PropertyData;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.exists;

public class ShowPropertiesDiffAction extends AnAction implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isVisible = checkThatChangesAreUnderSvn(e.getData(VcsDataKeys.CHANGES));

    e.getPresentation().setVisible(isVisible);
    e.getPresentation().setEnabled(isVisible && e.getProject() != null);
  }

  private static boolean checkThatChangesAreUnderSvn(@Nullable Change[] changes) {
    return changes != null && exists(changes, change -> isUnderSvn(change.getBeforeRevision()) || isUnderSvn(change.getAfterRevision()));
  }

  private static boolean isUnderSvn(@Nullable ContentRevision revision) {
    return revision instanceof SvnBaseContentRevision;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGE_LEAD_SELECTION);

    if (checkThatChangesAreUnderSvn(changes)) {
      new CalculateAndShow(e.getRequiredData(PROJECT), changes[0], e.getPresentation().getText()).queue();
    }
  }

  private static class CalculateAndShow extends Task.Backgroundable {
    private final Change myChange;
    private List<PropertyData> myBeforeContent;
    private List<PropertyData> myAfterContent;
    private Revision myBeforeRevisionValue;
    private Revision myAfterRevision;
    private SvnBindException myException;
    private final String myErrorTitle;

    private CalculateAndShow(@NotNull Project project, Change change, String errorTitle) {
      super(project, SvnBundle.message("fetching.properties.contents.progress.title"), true, PerformInBackgroundOption.DEAF);
      myChange = change;
      myErrorTitle = errorTitle;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      SvnVcs vcs = SvnVcs.getInstance(myProject);

      try {
        myBeforeRevisionValue = getBeforeRevisionValue(myChange);
        myAfterRevision = getAfterRevisionValue(myChange);

        myBeforeContent = getPropertyList(vcs, myChange.getBeforeRevision(), myBeforeRevisionValue);
        indicator.checkCanceled();
        // gets exactly WORKING revision property
        myAfterContent = getPropertyList(vcs, myChange.getAfterRevision(), myAfterRevision);
      }
      catch (SvnBindException exc) {
        myException = exc;
      }
    }

    @Override
    public void onSuccess() {
      if (myException != null) {
        Messages.showErrorDialog(myException.getMessage(), myErrorTitle);
        return;
      }
      if (myBeforeContent != null && myAfterContent != null && myBeforeRevisionValue != null && myAfterRevision != null) {
        SvnPropertiesDiffRequest diffRequest;
        if (compareRevisions(myBeforeRevisionValue, myAfterRevision) > 0) {
          diffRequest = new SvnPropertiesDiffRequest(getDiffWindowTitle(myChange),
                                                     new PropertyContent(myAfterContent), new PropertyContent(myBeforeContent),
                                                     revisionToString(myAfterRevision), revisionToString(myBeforeRevisionValue));
        }
        else {
          diffRequest = new SvnPropertiesDiffRequest(getDiffWindowTitle(myChange),
                                                     new PropertyContent(myBeforeContent), new PropertyContent(myAfterContent),
                                                     revisionToString(myBeforeRevisionValue), revisionToString(myAfterRevision));
        }
        DiffManager.getInstance().showDiff(myProject, diffRequest);
      }
    }
  }

  @NotNull
  private static Revision getBeforeRevisionValue(@NotNull Change change) {
    ContentRevision beforeRevision = change.getBeforeRevision();
    if (beforeRevision != null) {
      return ((SvnRevisionNumber)beforeRevision.getRevisionNumber()).getRevision();
    }
    else {
      return Revision.of(((SvnRevisionNumber)notNull(change.getAfterRevision()).getRevisionNumber()).getRevision().getNumber() - 1);
    }
  }

  @NotNull
  private static Revision getAfterRevisionValue(@NotNull Change change) {
    ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision != null) {
      // CurrentContentRevision will be here, for instance, if invoked from changes dialog for "Compare with Branch" action
      return afterRevision instanceof CurrentContentRevision
             ? Revision.WORKING
             : ((SvnRevisionNumber)afterRevision.getRevisionNumber()).getRevision();
    }
    else {
      return Revision.of(((SvnRevisionNumber)notNull(change.getBeforeRevision()).getRevisionNumber()).getRevision().getNumber() + 1);
    }
  }

  @NotNull
  private static String getDiffWindowTitle(@NotNull Change change) {
    if (change.isMoved() || change.isRenamed()) {
      FilePath beforeFilePath = ChangesUtil.getBeforePath(change);
      FilePath afterFilePath = ChangesUtil.getAfterPath(change);

      String beforePath = beforeFilePath == null ? "" : beforeFilePath.getPath();
      String afterPath = afterFilePath == null ? "" : afterFilePath.getPath();
      return SvnBundle.message("action.Subversion.properties.difference.diff.for.move.title", beforePath, afterPath);
    } else {
      return SvnBundle.message("action.Subversion.properties.difference.diff.title", ChangesUtil.getFilePath(change).getPath());
    }
  }

  private static int compareRevisions(@NotNull Revision revision1, @NotNull Revision revision2) {
    if (revision1.equals(revision2)) {
      return 0;
    }
    // working(local) ahead of head
    if (Revision.WORKING.equals(revision1)) {
      return 1;
    }
    if (Revision.WORKING.equals(revision2)) {
      return -1;
    }
    if (Revision.HEAD.equals(revision1)) {
      return 1;
    }
    if (Revision.HEAD.equals(revision2)) {
      return -1;
    }
    return revision1.getNumber() > revision2.getNumber() ? 1 : -1;
  }

  @NotNull
  private static String revisionToString(@Nullable Revision revision) {
    return revision == null ? "not exists" : revision.toString();
  }

  @NotNull
  private static List<PropertyData> getPropertyList(@NotNull SvnVcs vcs,
                                                    @Nullable ContentRevision contentRevision,
                                                    @Nullable Revision revision) throws SvnBindException {
    if (contentRevision == null) {
      return Collections.emptyList();
    }

    Target target;
    if (contentRevision instanceof SvnRepositoryContentRevision) {
      SvnRepositoryContentRevision svnRevision = (SvnRepositoryContentRevision)contentRevision;
      target = Target.on(svnRevision.getUrl(), revision);
    } else {
      File ioFile = contentRevision.getFile().getIOFile();
      target = Target.on(ioFile, revision);
    }

    return SvnLazyPropertyContentRevision.getPropertyList(vcs, target, revision);
  }
}
