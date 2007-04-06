package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class BaseFileConfigurableStoreImpl extends ComponentStoreImpl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.BaseFileConfigurableStoreImpl");

  private int myOriginalVersion = -1;
  private boolean mySavePathsRelative;
  final HashMap<String,String> myConfigurationNameToFileName = new HashMap<String,String>();
  @NonNls protected static final String RELATIVE_PATHS_OPTION = "relativePaths";
  @NonNls protected static final String VERSION_OPTION = "version";
  @NonNls public static final String ATTRIBUTE_NAME = "name";
  @NonNls static final String ELEMENT_COMPONENT = "component";
  @NonNls private static final String ATTRIBUTE_CLASS = "class";
  private ComponentManagerImpl myComponentManager;
  private static ArrayList<String> ourConversionProblemsStorage = new ArrayList<String>();
  private DefaultsStateStorage myDefaultsStateStorage;
  private StateStorageManager myStateStorageManager;


  protected BaseFileConfigurableStoreImpl(final ComponentManagerImpl componentManager) {
    myComponentManager = componentManager;
    myDefaultsStateStorage = new DefaultsStateStorage(PathMacroManager.getInstance(myComponentManager));
  }

  public synchronized ComponentManagerImpl getComponentManager() {
    return myComponentManager;
  }

  @Override
  protected void beforeSave() throws StateStorage.StateStorageException, SaveCancelledException {
    final XmlElementStorage mainStorage = getMainStorage();

    final Element rootElement = mainStorage.getRootElement();
    if (rootElement == null) return;

    writeRootElement(rootElement);

    super.beforeSave();
  }

  protected void writeRootElement(final Element rootElement) {
    rootElement.setAttributes(Collections.EMPTY_LIST);

    rootElement.setAttribute(RELATIVE_PATHS_OPTION, Boolean.toString(isSavePathsRelative()));
    rootElement.setAttribute(VERSION_OPTION, Integer.toString(ProjectManagerImpl.CURRENT_FORMAT_VERSION));
  }

  protected abstract XmlElementStorage getMainStorage();

  public synchronized void loadFromXml(Element root, String filePath) throws InvalidDataException {
    throw new UnsupportedOperationException("");
    /*
    PathMacroManager.getInstance(myComponentManager).expandPaths(root);

    int originalVersion = 0;
    try {
      originalVersion = Integer.parseInt(root.getAttributeValue(VERSION_OPTION));
    }
    catch (NumberFormatException e) {
      LOG.info(e);
    }
    if (originalVersion < 1) {
      Convertor01.execute(root);
    }
    if (originalVersion < 2) {
      Convertor12.execute(root);
    }
    if (originalVersion < 3) {
      Convertor23.execute(root);
    }
    if (originalVersion < 4) {
      Convertor34.execute(root, filePath, getConversionProblemsStorage());
    }

    if (getOriginalVersion() == -1) myOriginalVersion = originalVersion;
    myOriginalVersion = Math.min(getOriginalVersion(), originalVersion);

    String relative = root.getAttributeValue(RELATIVE_PATHS_OPTION);
    if (relative != null) {
      setSavePathsRelative(Boolean.parseBoolean(relative));
    }

    List children = root.getChildren(ELEMENT_COMPONENT);
    for (final Object aChildren : children) {
      Element element = (Element)aChildren;

      String name = element.getAttributeValue(ATTRIBUTE_NAME);
      if (name == null || name.length() == 0) {
        String className = element.getAttributeValue(ATTRIBUTE_CLASS);
        if (className == null) {
          throw new InvalidDataException();
        }
        name = className.substring(className.lastIndexOf('.') + 1);
      }

      addConfiguration(name, element);
      myConfigurationNameToFileName.put(name, filePath);
    }
    */

  }

  @Nullable
  static ArrayList<String> getConversionProblemsStorage() {
    return ourConversionProblemsStorage;
  }

  synchronized int getOriginalVersion() {
    return myOriginalVersion;
  }


  @Override
  protected void doSave() throws IOException {
    super.doSave();

    saveStorageManager();
  }

  protected void saveStorageManager() throws IOException {
    try {
      getStateStorageManager().save();
    }
    catch (StateStorage.StateStorageException e) {
      LOG.info(e);
      throw new IOException(e.getMessage());
    }
  }

  public void load() throws IOException {
  }

  public boolean isSavePathsRelative() {
    return mySavePathsRelative;
  }

  public void setSavePathsRelative(boolean b) {
    mySavePathsRelative = b;
  }

  public List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures) {
    return getStateStorageManager().getAllStorageFiles();
  }


  public List<VirtualFile> getAllStorageFilesToSave(final boolean includingSubStructures) {
    try {
      return getStateStorageManager().getAllStorageFilesToSave();
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
      return Collections.emptyList();
    }
  }

  @Override
  protected StateStorage getDefaultsStorage() {
    return myDefaultsStateStorage;
  }

  @Override
  protected StateStorage getStateStorage(final Storage storageSpec) throws StateStorage.StateStorageException {
    return getStateStorageManager().getStateStorage(storageSpec);
  }

  public StateStorageManager getStateStorageManager() {
    if (myStateStorageManager == null) {
      myStateStorageManager = createStateStorageManager();
    }
    return myStateStorageManager;
  }

  protected abstract StateStorageManager createStateStorageManager();
}
