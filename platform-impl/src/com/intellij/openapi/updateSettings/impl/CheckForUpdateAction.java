package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;

import java.util.List;

public class CheckForUpdateAction extends AnAction {

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(!SystemInfo.isMacSystemMenu);
  }

  public void actionPerformed(AnActionEvent e) {
    actionPerformed(true);
  }

  public static void actionPerformed(final boolean enableLink) {
    try {
      final UpdateChecker.NewVersion newVersion = UpdateChecker.checkForUpdates();
      final List<PluginDownloader> updatedPlugins = UpdateChecker.updatePlugins(true);
      if (newVersion != null) {
        UpdateSettings.getInstance().LAST_TIME_CHECKED = System.currentTimeMillis();
        UpdateChecker.showUpdateInfoDialog(enableLink, newVersion, updatedPlugins);
      }
      else {
        UpdateChecker.showNoUpdatesDialog(enableLink, updatedPlugins);
      }
    }
    catch (ConnectionException e) {
      Messages.showErrorDialog(IdeBundle.message("error.checkforupdates.connection.failed"),
                               IdeBundle.message("title.connection.error"));
    }
  }
}
