/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:13:55
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;

import java.util.List;

public class ScheduleForAdditionAction extends AnAction {
  public ScheduleForAdditionAction() {
    super(VcsBundle.message("changes.action.add.text"), VcsBundle.message("changes.action.add.description"),
          IconLoader.getIcon("/actions/include.png"));
  }

  public void update(AnActionEvent e) {
    List<VirtualFile> files = e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);
    boolean enabled = files != null && !files.isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    final List<VirtualFile> files = e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY);
    if (files == null) return;

    final ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(project);
    changeListManager.addUnversionedFiles(changeListManager.getDefaultChangeList(), files);
  }
}