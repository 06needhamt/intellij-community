package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocatorEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Alarm;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
class SaveAndSyncHandler implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.SaveAndSyncHandler");
  private Runnable myIdleListener;
  private PropertyChangeListener myGeneralSettingsListener;

  public SaveAndSyncHandler(final FrameStateManager frameStateManager,
                            final FileDocumentManager fileDocumentManager,
                            final GeneralSettings generalSettings) {

    myIdleListener = new Runnable() {
      public void run() {
        if (generalSettings.isAutoSaveIfInactive() && canSyncOrSave()) {
          fileDocumentManager.saveAllDocuments();
        }
      }
    };


    IdeEventQueue.getInstance().addIdleListener(
      myIdleListener,
      generalSettings.getInactiveTimeout() * 1000
    );

    myGeneralSettingsListener = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent e) {
        if (GeneralSettings.PROP_INACTIVE_TIMEOUT.equals(e.getPropertyName())) {
          IdeEventQueue eventQueue = IdeEventQueue.getInstance();
          eventQueue.removeIdleListener(myIdleListener);
          Integer timeout = (Integer)e.getNewValue();
          eventQueue.addIdleListener(myIdleListener, timeout.intValue() * 1000);
        }
      }
    };
    generalSettings.addPropertyChangeListener(myGeneralSettingsListener);

    frameStateManager.addListener(new FrameStateListener() {
      public void onFrameDeactivated() {
        if (canSyncOrSave()) {
          saveProjectsAndDocuments();
        }
      }

      public void onFrameActivated() {
        if (canSyncOrSave()) {
          refreshFiles();
        }
      }
    });

  }

  public String getComponentName() {
    return "SaveAndSyncHandler";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    GeneralSettings.getInstance().removePropertyChangeListener(myGeneralSettingsListener);
    IdeEventQueue.getInstance().removeIdleListener(myIdleListener);
  }

  private boolean canSyncOrSave() {
    if (LaterInvocatorEx.isInModalContext()) return false;
    if (ProgressManager.getInstance().hasModalProgressIndicator()) return false;
    return true;
  }


  private void saveProjectsAndDocuments() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: save()");
    }
    if (GeneralSettings.getInstance().isSaveOnFrameDeactivation()) {
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
    for (int i = 0; i < openProjects.length; i++) {
      Project project = openProjects[i];
      if (LOG.isDebugEnabled()) {
        LOG.debug("save project: " + project);
      }
      project.save();
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("save application settings");
    }
    ApplicationManagerEx.getApplicationEx().saveSettings();
    if (LOG.isDebugEnabled()) {
      LOG.debug("exit: save()");
    }
  }

  private void refreshFiles() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: synchronize()");
    }
    if (GeneralSettings.getInstance().isSyncOnFrameActivation()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("refresh VFS");
      }
      VirtualFileManager.getInstance().refresh(true);
    }
    else { // referesh only opened files
      Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
      for (int i = 0; i < openProjects.length; i++) {
        Project project = openProjects[i];
        VirtualFile[] file = FileEditorManager.getInstance(project).getSelectedFiles();
        for (int j = 0; j < file.length; j++) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("refresh file: " + file);
          }
          file[j].refresh(true, false);
        }
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("exit: synchronize()");
    }
  }
}