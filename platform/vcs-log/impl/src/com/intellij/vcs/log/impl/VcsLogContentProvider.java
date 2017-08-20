/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.util.ContentsUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.ui.VcsLogPanel;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Provides the Content tab to the ChangesView log toolwindow.
 * <p/>
 * Delegates to the VcsLogManager.
 */
public class VcsLogContentProvider implements ChangesViewContentProvider {
  private static final Logger LOG = Logger.getInstance(VcsLogContentProvider.class);
  public static final String TAB_NAME = "Log";

  @NotNull private final Project myProject;
  @NotNull private final VcsProjectLog myProjectLog;
  @NotNull private final JPanel myContainer = new JBPanel(new BorderLayout());

  public VcsLogContentProvider(@NotNull Project project, @NotNull VcsProjectLog projectLog) {
    myProject = project;
    myProjectLog = projectLog;

    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, new VcsProjectLog.ProjectLogListener() {
      @Override
      public void logCreated(@NotNull VcsLogManager logManager) {
        addLogUi(logManager);
      }

      @Override
      public void logDisposed(@NotNull VcsLogManager logManager) {
        dispose(logManager);
      }
    });

    VcsLogManager manager = myProjectLog.getLogManager();
    if (manager != null) {
      addLogUi(manager);
    }
  }

  @CalledInAwt
  private void addLogUi(@NotNull VcsLogManager logManager) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    if (myProjectLog.getMainLogUi() == null) {
      VcsLogUiImpl ui = logManager.createLogUi(VcsLogTabsProperties.MAIN_LOG_ID, TAB_NAME);
      myProjectLog.setMainUi(ui);
      myContainer.add(new VcsLogPanel(logManager, ui), BorderLayout.CENTER);
    }
  }

  private void dispose(@Nullable VcsLogManager logManager) {
    LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
    myContainer.removeAll();
    VcsLogUiImpl ui = myProjectLog.getMainLogUi();
    if (ui != null) Disposer.dispose(ui);
    if (logManager != null) closeLogTabs(logManager);
  }

  @Override
  public JComponent initContent() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> myProjectLog.createLog());
    return myContainer;
  }

  @Override
  public void disposeContent() {
    dispose(myProjectLog.getLogManager());
  }

  private void closeLogTabs(@NotNull VcsLogManager logManager) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.VCS);

    if (toolWindow != null) {
      for (String tabName : logManager.getTabNames()) {
        if (!TAB_NAME.equals(tabName)) { // main tab is closed by the ChangesViewContentManager
          Content content = toolWindow.getContentManager().findContent(tabName);
          LOG.assertTrue(content != null, "Could not find content for tab " + tabName);
          ContentsUtil.closeContentTab(toolWindow.getContentManager(), content);
        }
      }
    }
  }

  public static class VcsLogVisibilityPredicate implements NotNullFunction<Project, Boolean> {
    @NotNull
    @Override
    public Boolean fun(Project project) {
      return !VcsLogManager.findLogProviders(Arrays.asList(ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()), project)
        .isEmpty();
    }
  }
}
