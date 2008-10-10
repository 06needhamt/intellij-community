/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 21:53:06
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.CommitChangeListDialog;

import java.util.Arrays;

public class CommitAction extends AnAction {
  public void update(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    boolean enabled = false;
    if (project != null) {
      final ChangeList changeList = ChangesUtil.getChangeListIfOnlyOne(project, changes);
      if (changes != null && changeList != null) {
        for(Change c: changes) {
          final AbstractVcs vcs = ChangesUtil.getVcsForChange(c, project);
          if (vcs != null && vcs.getCheckinEnvironment() != null) {
            enabled = true;
          }
        }
      }
    }
    e.getPresentation().setEnabled(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Change[] changes = e.getData(VcsDataKeys.CHANGES);
    final ChangeList list = ChangesUtil.getChangeListIfOnlyOne(project, changes);
    if ((list == null) || (! (list instanceof LocalChangeList))) return;

    ChangeListManager.getInstance(project).invokeAfterUpdate(new Runnable() {
      public void run() {
        CommitChangeListDialog.commitChanges(project, Arrays.asList(changes), (LocalChangeList) list,
                                             ChangeListManager.getInstance(project).getRegisteredExecutors(), true, null);
      }
    }, InvokeAfterUpdateMode.SYNCHRONOUS_NOT_CANCELLABLE, VcsBundle.message("waiting.changelists.update.for.show.commit.dialog.message"));
  }
}