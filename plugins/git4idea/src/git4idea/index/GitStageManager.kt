// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangesViewManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vcs.impl.VcsStartupActivity
import com.intellij.util.messages.Topic
import com.intellij.vcs.commit.CommitWorkflowManager
import git4idea.GitVcs
import git4idea.config.GitVcsApplicationSettings

internal class GitStageManager(private val project: Project) : Disposable {
  fun installListeners() {
    stageLocalChangesRegistryOption().addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
        ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
      }
    }, this)
    stageLineStatusTrackerRegistryOption().addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
      }
    }, this)
  }

  private fun onAvailabilityChanged() {
    if (isStagingAreaAvailable(project)) {
      GitStageTracker.getInstance(project).scheduleUpdateAll()
    }
    ApplicationManager.getApplication().messageBus.syncPublisher(LineStatusTrackerSettingListener.TOPIC).settingsUpdated()
    ChangesViewManager.getInstanceEx(project).updateCommitWorkflow()
    project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
  }

  override fun dispose() {
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GitStageManager = project.service()
  }

  internal class GitStageStartupActivity : VcsStartupActivity {
    override fun getOrder(): Int = VcsInitObject.OTHER_INITIALIZATION.order

    override fun runActivity(project: Project) {
      if (isStagingAreaAvailable(project)) {
        GitStageTracker.getInstance(project).scheduleUpdateAll()
      }
      getInstance(project).installListeners()
    }
  }

  class StagingSettingsListener(val project: Project) : GitStagingAreaSettingsListener {
    override fun settingsChanged() {
      getInstance(project).onAvailabilityChanged()
    }
  }

  class CommitSettingsListener(val project: Project) : CommitWorkflowManager.SettingsListener {
    override fun settingsChanged() {
      getInstance(project).onAvailabilityChanged()
    }
  }
}

interface GitStagingAreaSettingsListener {
  fun settingsChanged()

  companion object {
    @JvmField
    val TOPIC: Topic<GitStagingAreaSettingsListener> = Topic.create("Git Staging Area Settings Changes",
                                                                    GitStagingAreaSettingsListener::class.java)
  }
}

fun stageLineStatusTrackerRegistryOption() = Registry.get("git.enable.stage.line.status.tracker")
fun stageLocalChangesRegistryOption() = Registry.get("git.enable.stage.disable.local.changes")

fun isStagingAreaEnabled() = GitVcsApplicationSettings.getInstance().isStagingAreaEnabled
fun enableStagingArea(enabled: Boolean) {
  val applicationSettings = GitVcsApplicationSettings.getInstance()
  if (enabled == applicationSettings.isStagingAreaEnabled) return

  applicationSettings.isStagingAreaEnabled = enabled
  ApplicationManager.getApplication().messageBus.syncPublisher(GitStagingAreaSettingsListener.TOPIC).settingsChanged()
}

fun canEnableStagingArea() = CommitWorkflowManager.isNonModalInSettings()

fun isStagingAreaAvailable() = isStagingAreaEnabled() && canEnableStagingArea()
fun isStagingAreaAvailable(project: Project): Boolean {
  return isStagingAreaAvailable() &&
         ProjectLevelVcsManager.getInstance(project).singleVCS?.keyInstanceMethod == GitVcs.getKey()
}