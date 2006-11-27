/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.RecentChangesProvider;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.versionBrowser.VersionsProvider;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;

/**
 * @author max
 */
public class ChangesBrowserDialog extends DialogWrapper {
  private final Project myProject;
  private final ListTableModel<CommittedChangeList> myChanges;
  private final boolean myShowSearchAgain;
  private VersionsProvider myVersionsProvider;
  private RecentChangesProvider myRecentChangesProvider;
  private CommittedChangesBrowser myCommittedChangesBrowser;

  public ChangesBrowserDialog(Project project, ListTableModel<CommittedChangeList> changes, final boolean showSearchAgain) {
    super(project, true);
    myProject = project;
    myChanges = changes;
    myShowSearchAgain = showSearchAgain;
    setTitle(VcsBundle.message("dialog.title.changes.browser"));
    setCancelButtonText(CommonBundle.getCloseButtonText());
    setModal(false);

    init();
  }

  public void setVersionsProvider(final VersionsProvider versionsProvider) {
    myVersionsProvider = versionsProvider;
  }

  public void setRecentChangesProvider(final RecentChangesProvider recentChangesProvider) {
    myRecentChangesProvider = recentChangesProvider;
  }

  protected String getDimensionServiceKey() {
    return "VCS.ChangesBrowserDialog";
  }

  protected JComponent createCenterPanel() {
    myCommittedChangesBrowser = new CommittedChangesBrowser(myProject, myChanges);
    return myCommittedChangesBrowser;
  }

  @Override
  protected void dispose() {
    super.dispose();
    myCommittedChangesBrowser.dispose();
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    getOKAction().putValue(Action.NAME, VcsBundle.message("button.search.again"));
  }

  @Override
  protected Action[] createActions() {
    if (!myShowSearchAgain) {
      return new Action[] { getCancelAction() };
    }
    return super.createActions();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    if (myVersionsProvider != null) {
      AbstractVcsHelper.getInstance(myProject).showChangesBrowser(myVersionsProvider, getTitle());
    }
    else if (myRecentChangesProvider != null) {
      AbstractVcsHelper.getInstance(myProject).showChangesBrowser(myRecentChangesProvider, getTitle());
    }
  }
}
