package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ModuleStoreImpl extends BaseFileConfigurableStoreImpl implements IModuleStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ModuleStoreImpl");
  @NonNls private static final String RELATIVE_PATHS_OPTION = "relativePaths";
  @NonNls private static final String MODULE_FILE_MACRO = "MODULE_FILE";

  private ModuleImpl myModule;

  private static final String DEFAULT_STATE_STORAGE = "$" + MODULE_FILE_MACRO + "$";


  @SuppressWarnings({"UnusedDeclaration"})
  public ModuleStoreImpl(final ComponentManagerImpl componentManager, final ModuleImpl module) {
    super(componentManager);
    myModule = module;
  }

  @Override
  protected void writeRootElement(final Element rootElement) {
    super.writeRootElement(rootElement);

    Set<String> options = myModule.myOptions.keySet();
    for (String option : options) {
      rootElement.setAttribute(option, myModule.myOptions.get(option));
    }

    Set<String> obsoleteOptions = new HashSet<String>();
    for (Object attr : rootElement.getAttributes()) {
      obsoleteOptions.add(((Attribute)attr).getName());
    }
    obsoleteOptions.removeAll(options);
    obsoleteOptions.remove(BaseFileConfigurableStoreImpl.VERSION_OPTION);
    obsoleteOptions.remove(BaseFileConfigurableStoreImpl.RELATIVE_PATHS_OPTION);
    for ( String oldOption : obsoleteOptions ) {
      rootElement.removeAttribute(oldOption);
    }
  }

  protected XmlElementStorage getMainStorage() {
    return (XmlElementStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
  }

  @Override
  public void load() throws IOException {
    super.load();

    try {
      final StateStorage stateStorage = getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
      assert stateStorage instanceof FileBasedStorage;
      FileBasedStorage fileBasedStorage = (FileBasedStorage)stateStorage;

      Document doc = fileBasedStorage.getDocument();
      final Element element = doc.getRootElement();

      final List attributes = element.getAttributes();
      for (Object attribute : attributes) {
        Attribute attr = (Attribute)attribute;
        final String optionName = attr.getName();
        final String optionValue = attr.getValue();
        myModule.setOption(optionName, optionValue);

        if (optionName.equals(RELATIVE_PATHS_OPTION) && optionValue.equals("true")) {
          setSavePathsRelative(true);
        }
      }

      final String moduleTypeId = myModule.getOptionValue(ModuleImpl.ELEMENT_TYPE);
      final ModuleType moduleType = moduleTypeId == null ? ModuleType.JAVA : ModuleTypeManager.getInstance().findByID(moduleTypeId);
      myModule.setModuleType(moduleType);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.info(e);
      throw new IOException(e.getMessage());
    }
  }

  public void setModuleFilePath(final String filePath) {
    LOG.assertTrue(filePath != null);
    final String path = filePath.replace(File.separatorChar, '/');
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      }
    });
    getStateStorageManager().clearStateStorage(DEFAULT_STATE_STORAGE);
    getStateStorageManager().addMacro(MODULE_FILE_MACRO, path);
  }

  @Nullable
  public VirtualFile getModuleFile() {
    try {
      return ((FileBasedStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE)).getVirtualFile();
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  public String getModuleFilePath() {
    return ((FileBasedStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE)).getFilePath();
  }

  @NotNull
  public String getModuleFileName() {
    return ((FileBasedStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE)).getFileName();
  }

  @Override
  protected boolean optimizeTestLoading() {
    return ((ProjectImpl)myModule.getProject()).isOptimiseTestLoadSpeed();
  }

  @Nullable
  protected VirtualFile getComponentConfigurationFile(ComponentConfig componentInterface) {
    return getModuleFile();
  }

  @NonNls
  protected String getRootNodeName() {
    return "module";
  }

  // since this option is stored in 2 different places (a field in the base class and myOptions map in this class),
  // we have to update both storages whenever the value of the option changes
  public void setSavePathsRelative(boolean value) {
    super.setSavePathsRelative(value);
    myModule.setOption(RELATIVE_PATHS_OPTION, String.valueOf(value));
  }

  synchronized String getLineSeparator(final VirtualFile file) {
    return FileDocumentManager.getInstance().getLineSeparator(file, myModule.getProject());
  }

  public synchronized void initStore() {
  }


  @Override
  protected StateStorage getOldStorage(final Object component) {
    return getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
  }

  protected StateStorageManager createStateStorageManager() {
    return new ModuleStateStorageManager(PathMacroManager.getInstance(getComponentManager()).createTrackingSubstitutor(), myModule);
  }
}
