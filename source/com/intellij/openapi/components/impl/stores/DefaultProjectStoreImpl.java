package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

//todo: extends from base store class
public class DefaultProjectStoreImpl extends ProjectStoreImpl {
  private @Nullable Element myElement;
  private ProjectManagerImpl myProjectManager;
  @NonNls private static final String PROJECT = "project";

  public DefaultProjectStoreImpl(final ComponentManagerImpl componentManager, final ProjectImpl project, final ProjectManagerImpl projectManager) {
    super(componentManager, project, projectManager);
    myProjectManager = projectManager;

    myElement = projectManager.getDefaultProjectRootElement();
  }

  Element getStateCopy() {
    return (Element)myProjectManager.getDefaultProjectRootElement().clone();
  }


  @Override
  protected StateStorageManager createStateStorageManager() {
    Document _d = null;

    if (myElement != null) {
      myElement.detach();
      _d = new Document(myElement);
    }

    final PathMacroManager pathMacroManager = PathMacroManager.getInstance(getComponentManager());

    final Document document = _d;

    final XmlElementStorage storage = new XmlElementStorage(pathMacroManager) {
      @Nullable
      protected Document loadDocument() throws StateStorage.StateStorageException {
        return document;
      }

      public void doSave() throws StateStorage.StateStorageException {
        if (myElement != null) {
          myProjectManager.setDefaultProjectRootElement((Element)myElement.clone());
        }
      }

      public boolean  needsSave() throws StateStorage.StateStorageException {
        return true;
      }

      public List<VirtualFile> getAllStorageFiles() {
        return Collections.emptyList();
      }
    };

    return new StateStorageManager(pathMacroManager, PROJECT) {
      @Override
      public synchronized StateStorage getStateStorage(@NotNull final Storage storageSpec) {
        return storage;
      }

      @Override
      @Nullable
      public StateStorage getFileStateStorage(final String fileName) {
        return storage;
      }

      @Override
      public synchronized void save() throws StateStorage.StateStorageException, IOException {
        super.save();
        storage.save();
      }
    };
  }
}
