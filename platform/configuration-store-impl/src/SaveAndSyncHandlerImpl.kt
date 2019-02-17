// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.concurrency.JobScheduler
import com.intellij.ide.FrameStateListener
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.util.SingleAlarm
import com.intellij.util.pooledThreadSingleAlarm
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.CalledInAwt
import java.beans.PropertyChangeListener
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val LISTEN_DELAY = 15

internal class SaveAndSyncHandlerImpl : BaseSaveAndSyncHandler(), Disposable, BaseComponent {
  private val refreshDelayAlarm = SingleAlarm(Runnable { this.doScheduledRefresh() }, delay = 300, parentDisposable = this)
  private val blockSaveOnFrameDeactivationCount = AtomicInteger()
  private val blockSyncOnFrameActivationCount = AtomicInteger()
  @Volatile
  private var refreshSessionId = -1L

  private val saveQueue = ArrayDeque<SaveTask>()

  private val saveAlarm = pooledThreadSingleAlarm(delay = 300, parentDisposable = this) {
    val app = ApplicationManager.getApplication()
    if (app != null && !app.isDisposed && !app.isDisposeInProgress && blockSaveOnFrameDeactivationCount.get() == 0) {
      runBlocking {
        processTasks()
      }
    }
  }

  private suspend fun processTasks() {
    while (true) {
      val task = synchronized(saveQueue) {
        saveQueue.pollFirst() ?: return
      }
      LOG.runAndLogException {
        task.save()
      }
    }
  }

  override fun initComponent() {
    // add listeners after some delay - doesn't make sense to listen earlier
    JobScheduler.getScheduler().schedule(Runnable {
      ApplicationManager.getApplication().invokeLater { addListeners() }
    }, LISTEN_DELAY.toLong(), TimeUnit.SECONDS)
  }

  @CalledInAwt
  private fun addListeners() {
    val settings = GeneralSettings.getInstance()
    val idleListener = Runnable {
      if (settings.isAutoSaveIfInactive && canSyncOrSave()) {
        submitTransaction {
          (FileDocumentManagerImpl.getInstance() as FileDocumentManagerImpl).saveAllDocuments(false)
        }
      }
    }

    var disposable: Disposable? = null

    fun addIdleListener() {
      IdeEventQueue.getInstance().addIdleListener(idleListener, settings.inactiveTimeout * 1000)
      disposable = Disposable { IdeEventQueue.getInstance().removeIdleListener(idleListener) }
      Disposer.register(this, disposable!!)
    }

    settings.addPropertyChangeListener(GeneralSettings.PROP_INACTIVE_TIMEOUT, this, PropertyChangeListener {
      disposable?.let { Disposer.dispose(it) }
      addIdleListener()
    })

    addIdleListener()
    if (LISTEN_DELAY >= (settings.inactiveTimeout * 1000)) {
      idleListener.run()
    }

    val busConnection = ApplicationManager.getApplication().messageBus.connect(this)
    busConnection.subscribe(FrameStateListener.TOPIC, object : FrameStateListener {
      override fun onFrameDeactivated() {
        LOG.debug("save(): enter")

        if (!settings.isSaveOnFrameDeactivation) {
          return
        }

        if (canSyncOrSave() && addToSaveQueue(saveAppTask)) {
          // do not cancel if there is already request - opposite to scheduleSaveDocumentsAndProjectsAndApp,
          // on frame deactivation better to save as soon as possible
          saveAlarm.request(delay = 100)
        }

        LOG.debug("save(): exit")
      }

      override fun onFrameActivated() {
        if (!ApplicationManager.getApplication().isDisposed && settings.isSyncOnFrameActivation) {
          scheduleRefresh()
        }
      }
    })
  }

  override fun scheduleSaveDocumentsAndProjectsAndApp(onlyProject: Project?, forceSavingAllSettings: Boolean, forceExecuteImmediately: Boolean) {
    val task = when {
      onlyProject == null && !forceSavingAllSettings -> saveAppTask
      else -> SaveTask(onlyProject, forceSavingAllSettings)
    }

    if (addToSaveQueue(task) || forceExecuteImmediately) {
      saveAlarm.cancelAndRequest(forceRun = forceExecuteImmediately)
    }
  }

  private fun addToSaveQueue(task: SaveTask): Boolean {
    synchronized(saveQueue) {
      if (task === saveAppTask) {
        saveQueue.removeAll { it.onlyProject != null && !it.forceSavingAllSettings }
      }

      if (saveQueue.contains(task)) {
        return false
      }
      else {
        return saveQueue.add(task)
      }
    }
  }

  override fun cancelScheduledSave() {
    saveAlarm.cancel()
  }

  private fun waitForScheduledSave() {
    if (saveAlarm.isEmpty) {
      return
    }

    while (true) {
      ApplicationManager.getApplication().invokeAndWait(Runnable {
        edtPoolDispatcherManager.processTasks()
      }, ModalityState.any())

      if (saveAlarm.isEmpty) {
        return
      }

      Thread.sleep(5)
    }
  }

  @CalledInAwt
  override fun saveSettingsUnderModalProgress(componentManager: ComponentManager, isSaveAppAlso: Boolean): Boolean {
    if (!ApplicationManager.getApplication().isDispatchThread) {
      throw IllegalStateException(
        "saveSettingsUnderModalProgress is intended to be called only in EDT because otherwise wrapping into modal progress task is not required" +
        " and `saveSettings` should be called directly")
    }

    var isSavedSuccessfully = true
    runInSaveOnFrameDeactivationDisabledMode {
      ProgressManager.getInstance().run(object : Task.Modal(componentManager as? Project, "Saving " + (if (componentManager is Application) "Application" else "Project"), /* canBeCancelled = */ false) {
        override fun run(indicator: ProgressIndicator) {
          if (project != null) {
            synchronized(saveQueue) {
              saveQueue.removeAll { it.onlyProject === project }
            }
          }

          waitForScheduledSave()

          runBlocking {
            isSavedSuccessfully = saveSettings(componentManager, forceSavingAllSettings = true)
            if (isSaveAppAlso && componentManager !is Application) {
              saveSettings(ApplicationManager.getApplication(), forceSavingAllSettings = true)
            }
          }
        }
      })
    }
    return isSavedSuccessfully
  }

  override fun dispose() {
    if (refreshSessionId != -1L) {
      RefreshQueue.getInstance().cancelSession(refreshSessionId)
    }
  }

  private fun canSyncOrSave(): Boolean {
    return !LaterInvocator.isInModalContext() && !ProgressManager.getInstance().hasModalProgressIndicator()
  }

  override fun scheduleRefresh() {
    refreshDelayAlarm.cancelAndRequest()
  }

  private fun doScheduledRefresh() {
    submitTransaction {
      if (canSyncOrSave()) {
        refreshOpenFiles()
      }
      maybeRefresh(ModalityState.NON_MODAL)
    }
  }

  override fun maybeRefresh(modalityState: ModalityState) {
    if (blockSyncOnFrameActivationCount.get() == 0 && GeneralSettings.getInstance().isSyncOnFrameActivation) {
      val queue = RefreshQueue.getInstance()
      queue.cancelSession(refreshSessionId)

      val session = queue.createSession(true, true, null, modalityState)
      session.addAllFiles(*ManagingFS.getInstance().localRoots)
      refreshSessionId = session.id
      session.launch()
      LOG.debug("vfs refreshed")
    }
    else {
      LOG.debug { "vfs refresh rejected, blocked: ${blockSyncOnFrameActivationCount.get() != 0}, isSyncOnFrameActivation: ${GeneralSettings.getInstance().isSyncOnFrameActivation}" }
    }
  }

  override fun refreshOpenFiles() {
    val files = ArrayList<VirtualFile>()
    for (project in ProjectManager.getInstance().openProjects) {
      FileEditorManager.getInstance(project).selectedFiles.filterTo(files) { it is NewVirtualFile }
    }

    if (!files.isEmpty()) {
      // refresh open files synchronously so it doesn't wait for potentially longish refresh request in the queue to finish
      RefreshQueue.getInstance().refresh(false, false, null, files)
    }
  }

  override fun blockSaveOnFrameDeactivation() {
    LOG.debug("save blocked")
    blockSaveOnFrameDeactivationCount.incrementAndGet()
  }

  override fun unblockSaveOnFrameDeactivation() {
    blockSaveOnFrameDeactivationCount.decrementAndGet()
    LOG.debug("save unblocked")
  }

  override fun blockSyncOnFrameActivation() {
    LOG.debug("sync blocked")
    blockSyncOnFrameActivationCount.incrementAndGet()
  }

  override fun unblockSyncOnFrameActivation() {
    blockSyncOnFrameActivationCount.decrementAndGet()
    LOG.debug("sync unblocked")
  }

  private inline fun submitTransaction(crossinline handler: () -> Unit) {
    TransactionGuard.submitTransaction(this, Runnable { handler() })
  }
}

internal abstract class BaseSaveAndSyncHandler : SaveAndSyncHandler() {
  internal val edtPoolDispatcherManager = EdtPoolDispatcherManager()
}

private val saveAppTask = SaveTask(onlyProject = null, forceSavingAllSettings = false)

private data class SaveTask(val onlyProject: Project?, val forceSavingAllSettings: Boolean) {
  suspend fun save() {
    if (onlyProject?.isDisposed == true) {
      return
    }

    coroutineScope {
      launch(storeEdtCoroutineContext) {
        // forceSavingAllSettings is set to true currently only if save triggered explicitly (or on close app/project), so, pass equal isDocumentsSavingExplicit
        // in any case flag isDocumentsSavingExplicit is not really important
        (FileDocumentManagerImpl.getInstance() as FileDocumentManagerImpl).saveAllDocuments(forceSavingAllSettings)
      }
      launch {
        saveProjectsAndApp(forceSavingAllSettings = forceSavingAllSettings, onlyProject = onlyProject)
      }
    }
  }
}