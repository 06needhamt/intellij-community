/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static com.intellij.vcs.log.util.PersistentUtil.LOG_CACHE;

public class VcsProjectLog implements Disposable {
  private static final Logger LOG = Logger.getInstance(VcsProjectLog.class);
  public static final Topic<ProjectLogListener> VCS_PROJECT_LOG_CHANGED =
    Topic.create("Project Vcs Log Created or Disposed", ProjectLogListener.class);
  private static final int RECREATE_LOG_TRIES = 5;
  @NotNull private final Project myProject;
  @NotNull private final MessageBus myMessageBus;
  @NotNull private final VcsLogTabsProperties myUiProperties;
  @NotNull private final VcsLogTabsManager myTabsManager;

  @NotNull private final LazyVcsLogManager myLogManager = new LazyVcsLogManager();
  @NotNull private final Disposable myMappingChangesDisposable = Disposer.newDisposable();
  private int myRecreatedLogCount = 0;

  public VcsProjectLog(@NotNull Project project,
                       @NotNull MessageBus messageBus,
                       @NotNull VcsLogProjectTabsProperties uiProperties) {
    myProject = project;
    myMessageBus = messageBus;
    myUiProperties = uiProperties;
    myTabsManager = new VcsLogTabsManager(project, messageBus, uiProperties, this);

    Disposer.register(this, myMappingChangesDisposable);
  }

  private void subscribeToMappingsChanges() {
    myMessageBus.connect(myMappingChangesDisposable).subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this::recreateLog);
  }

  @Nullable
  public VcsLogData getDataManager() {
    VcsLogManager cached = myLogManager.getCached();
    if (cached == null) return null;
    return cached.getDataManager();
  }

  @NotNull
  private Collection<VcsRoot> getVcsRoots() {
    return Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots());
  }

  /**
   * The instance of the {@link VcsLogUiImpl} or null if the log was not initialized yet.
   */
  @Nullable
  public VcsLogUiImpl getMainLogUi() {
    VcsLogContentProvider logContentProvider = VcsLogContentProvider.getInstance(myProject);
    if (logContentProvider == null) return null;
    return logContentProvider.getUi();
  }

  @Nullable
  public VcsLogManager getLogManager() {
    return myLogManager.getCached();
  }

  @NotNull
  public VcsLogTabsManager getTabsManager() {
    return myTabsManager;
  }

  @CalledInAny
  private void recreateLog() {
    UIUtil.invokeLaterIfNeeded(() -> myLogManager.drop(() -> {
      if (myProject.isDisposed()) return;
      createLog(false);
    }));
  }

  @CalledInAwt
  private void recreateOnError(@NotNull Throwable t) {
    if ((++myRecreatedLogCount) % RECREATE_LOG_TRIES == 0) {
      String message = String.format("VCS Log was recreated %d times due to data corruption\n" +
                                     "Delete %s directory and restart %s if this happens often.\n%s",
                                     myRecreatedLogCount, LOG_CACHE, ApplicationNamesInfo.getInstance().getFullProductName(),
                                     t.getMessage());
      LOG.error(message, t);

      VcsLogManager manager = getLogManager();
      if (manager != null && manager.isLogVisible()) {
        VcsBalloonProblemNotifier.showOverChangesView(myProject, message, MessageType.ERROR);
      }
    }
    else {
      LOG.debug("Recreating VCS Log after storage corruption", t);
    }

    recreateLog();
  }

  @CalledInBackground
  void createLog(boolean forceInit) {
    Map<VirtualFile, VcsLogProvider> logProviders = getLogProviders();
    if (!logProviders.isEmpty()) {
      createLog(logProviders, forceInit);
    }
  }

  @CalledInBackground
  private void createLog(@NotNull Map<VirtualFile, VcsLogProvider> logProviders, boolean forceInit) {
    VcsLogManager logManager = myLogManager.getValue(logProviders);

    ApplicationManager.getApplication().invokeLater(() -> {
      if (logManager.isLogVisible() || forceInit) {
        logManager.scheduleInitialization();
      }
      else if (PostponableLogRefresher.keepUpToDate()) {
        VcsLogCachesInvalidator invalidator = CachesInvalidator.EP_NAME.findExtension(VcsLogCachesInvalidator.class);
        if (invalidator.isValid()) {
          HeavyAwareExecutor.executeOutOfHeavyProcessLater(logManager::scheduleInitialization, 5000);
        }
      }
    });
  }

  @NotNull
  private Map<VirtualFile, VcsLogProvider> getLogProviders() {
    return VcsLogManager.findLogProviders(Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots()), myProject);
  }

  public static VcsProjectLog getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsProjectLog.class);
  }

  @Override
  public void dispose() {
    myLogManager.drop();
  }

  private class LazyVcsLogManager {
    @Nullable private VcsLogManager myValue;

    @NotNull
    @CalledInBackground
    public synchronized VcsLogManager getValue(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
      if (myValue == null) {
        VcsLogManager value = compute(logProviders);
        myValue = value;
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!myProject.isDisposed()) myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logCreated(value);
        });
      }
      return myValue;
    }

    @NotNull
    @CalledInBackground
    protected VcsLogManager compute(@NotNull Map<VirtualFile, VcsLogProvider> logProviders) {
      return new VcsLogManager(myProject, myUiProperties, logProviders, false,
                               VcsProjectLog.this::recreateOnError);
    }

    @CalledInAwt
    public synchronized void drop() {
      LOG.assertTrue(ApplicationManager.getApplication().isDispatchThread());
      drop(null);
    }

    public synchronized void drop(@Nullable Runnable callback) {
      if (myValue != null) {
        myMessageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logDisposed(myValue);
        myValue.dispose(callback);
        myValue = null;
      }
      else if (callback != null) {
        ApplicationManager.getApplication().executeOnPooledThread(callback);
      }
    }

    @Nullable
    public synchronized VcsLogManager getCached() {
      return myValue;
    }
  }

  public static class InitLogStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) return;

      VcsProjectLog projectLog = getInstance(project);

      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        projectLog.subscribeToMappingsChanges();
        projectLog.createLog(false);
      });
    }
  }

  public interface ProjectLogListener {
    @CalledInAwt
    void logCreated(@NotNull VcsLogManager manager);

    @CalledInAwt
    default void logDisposed(@NotNull VcsLogManager manager) {
    }
  }
}
