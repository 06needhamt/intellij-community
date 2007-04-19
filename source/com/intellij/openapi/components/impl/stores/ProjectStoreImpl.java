package com.intellij.openapi.components.impl.stores;

import com.intellij.CommonBundle;
import com.intellij.application.options.PathMacrosImpl;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.fs.FileSystem;
import com.intellij.util.io.fs.IFile;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

class ProjectStoreImpl extends BaseFileConfigurableStoreImpl implements IProjectStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ProjectStoreImpl");
  @NonNls private static final String OLD_PROJECT_SUFFIX = "_old.";
  @NonNls private static final String WORKSPACE_EXTENSION = ".iws";
  @NonNls private static final String OPTION_WORKSPACE = "workspace";
  @NonNls public static final String DIRECTORY_STORE_FOLDER = ".idea";

  private ProjectEx myProject;

  @NonNls private static final String PROJECT_FILE_MACRO = "PROJECT_FILE";
  @NonNls private static final String WS_FILE_MACRO = "WORKSPACE_FILE";
  @NonNls private static final String PROJECT_CONFIG_DIR = "PROJECT_CONFIG_DIR";

  private static final String PROJECT_FILE_STORAGE = "$" + PROJECT_FILE_MACRO + "$";
  private static final String WS_FILE_STORAGE = "$" + WS_FILE_MACRO + "$";
  static final String DEFAULT_STATE_STORAGE = PROJECT_FILE_STORAGE;
  private Set<String> myTrackingSet = new TreeSet<String>();
  private StorageScheme myScheme = StorageScheme.DEFAULT;

  @SuppressWarnings({"UnusedDeclaration"})
  public ProjectStoreImpl(final ProjectEx project) {
    super(project);
    myProject = project;
  }

  @Override
  protected void beforeSave() throws StateStorage.StateStorageException, SaveCancelledException {
    super.beforeSave();

    //is needed for compatibility with demetra
    writeWsVerrsion();
  }

  private void writeWsVerrsion() throws StateStorage.StateStorageException {
    final XmlElementStorage wsStorage = (XmlElementStorage)getStateStorageManager().getFileStateStorage(WS_FILE_STORAGE);

    if (wsStorage == null) return;
    final Element rootElement = wsStorage.getRootElement();
    if (rootElement == null) return;

    writeRootElement(rootElement);
  }

  private static String[] readUsedMacros(Element root) {
    Element child = root.getChild(ProjectStateStorageManager.USED_MACROS_ELEMENT_NAME);
    if (child == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    final List children = child.getChildren(ProjectStateStorageManager.ELEMENT_MACRO);
    final List<String> macroNames = new ArrayList<String>(children.size());
    for (final Object aChildren : children) {
      final Element macro = (Element)aChildren;
      String macroName = macro.getAttributeValue(BaseFileConfigurableStoreImpl.ATTRIBUTE_NAME);
      if (macroName != null) {
        macroNames.add(macroName);
      }
    }
    return macroNames.toArray(new String[macroNames.size()]);
  }

  public boolean checkVersion() {
    int version = getOriginalVersion();
    if (version >= 0 && version < ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
      final VirtualFile projectFile = getProjectFile();
      LOG.assertTrue(projectFile != null);
      String name = projectFile.getNameWithoutExtension();

      String message = ProjectBundle.message("project.convert.old.prompt", projectFile.getName(),
                                             ApplicationNamesInfo.getInstance().getProductName(),
                                             name + OLD_PROJECT_SUFFIX + projectFile.getExtension());
      if (Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) != 0) return false;

      final ArrayList<String> conversionProblems = getConversionProblemsStorage();
      if (conversionProblems != null && conversionProblems.size() > 0) {
        StringBuffer buffer = new StringBuffer();
        buffer.append(ProjectBundle.message("project.convert.problems.detected"));
        for (String s : conversionProblems) {
          buffer.append('\n');
          buffer.append(s);
        }
        buffer.append(ProjectBundle.message("project.convert.problems.help"));
        final int result = Messages.showDialog(myProject, buffer.toString(), ProjectBundle.message("project.convert.problems.title"),
                                               new String[]{ProjectBundle.message("project.convert.problems.help.button"),
                                                 CommonBundle.getCloseButtonText()}, 0,
                                                                                     Messages.getWarningIcon()
        );
        if (result == 0) {
          HelpManager.getInstance().invokeHelp("project.migrationProblems");
        }
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            VirtualFile projectDir = projectFile.getParent();
            assert projectDir != null;

            backup(projectDir, projectFile);

            VirtualFile workspaceFile = getWorkspaceFile();
            if (workspaceFile != null) {
              backup(projectDir, workspaceFile);
            }
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }

        private void backup(final VirtualFile projectDir, final VirtualFile vile) throws IOException {
          final String oldName = vile.getNameWithoutExtension() + OLD_PROJECT_SUFFIX +
                                          vile.getExtension();
          VirtualFile oldFile = projectDir.findOrCreateChildData(this, oldName);
          VfsUtil.saveText(oldFile, VfsUtil.loadText(vile));
        }

      });
    }

    if (version > ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
      String message =
        ProjectBundle.message("project.load.new.version.warning", myProject.getName(), ApplicationNamesInfo.getInstance().getProductName());

      if (Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) != 0) return false;
    }

    return true;
  }

  private ReadonlyStatusHandler.OperationStatus ensureConfigFilesWritable() {
    return ApplicationManager.getApplication().runWriteAction(new Computable<ReadonlyStatusHandler.OperationStatus>() {
      public ReadonlyStatusHandler.OperationStatus compute() {
        final List<VirtualFile> filesToSave = getAllStorageFilesToSave(true);

        List<VirtualFile> readonlyFiles = new ArrayList<VirtualFile>();
        for (VirtualFile file : filesToSave) {
          if (!file.isWritable()) readonlyFiles.add(file);
        }

        if (readonlyFiles.isEmpty()) return new ReadonlyStatusHandler.OperationStatus(VirtualFile.EMPTY_ARRAY, VirtualFile.EMPTY_ARRAY); 

        return ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(readonlyFiles.toArray(new VirtualFile[readonlyFiles.size()]));
      }
    });
  }


  @Override
  protected void saveStorageManager() throws IOException {
    final ReadonlyStatusHandler.OperationStatus operationStatus = ensureConfigFilesWritable();
    if (operationStatus.hasReadonlyFiles()) {
      MessagesEx.error(myProject, ProjectBundle.message("project.save.error", operationStatus.getReadonlyFilesMessage())).showLater();
      throw new SaveCancelledException();
    }

    super.saveStorageManager();
  }

  @Override
  protected boolean optimizeTestLoading() {
    return myProject.isOptimiseTestLoadSpeed();
  }

  public void setProjectFilePath(final String filePath) {
    if (filePath != null) {
      final IFile iFile = FileSystem.FILE_SYSTEM.createFile(filePath);
      final IFile dir_store = iFile.isDirectory() ? iFile.getChild(DIRECTORY_STORE_FOLDER) : iFile.getParentFile().getChild(DIRECTORY_STORE_FOLDER);

      if (dir_store.exists()) {
        myScheme = StorageScheme.DIRECTORY_BASED;
        getStateStorageManager().addMacro(PROJECT_FILE_MACRO, dir_store.getChild("misc.xml").getPath());
        getStateStorageManager().addMacro(WS_FILE_MACRO, dir_store.getChild("workspace.xml").getPath());
        getStateStorageManager().addMacro(PROJECT_CONFIG_DIR, dir_store.getPath());
      }
      else {
        myScheme = StorageScheme.DEFAULT;
        getStateStorageManager().addMacro(PROJECT_FILE_MACRO, filePath);

        if (filePath.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
          String workspacePath = filePath.substring(0, filePath.length() - ProjectFileType.DOT_DEFAULT_EXTENSION.length()) + WORKSPACE_EXTENSION;
          getStateStorageManager().addMacro(WS_FILE_MACRO, workspacePath);
        }
      }
    }
  }

  @Nullable
  public VirtualFile getProjectBaseDir() {
    final VirtualFile projectFile = getProjectFile();
    if (projectFile != null) return  myScheme == StorageScheme.DEFAULT ? projectFile.getParent() : projectFile.getParent().getParent();

    //we are not yet initialized completely
    final StateStorage s = getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    if (!(s instanceof FileBasedStorage)) return null;
    final FileBasedStorage storage = (FileBasedStorage)s;

    final IFile file = storage.getFile();
    if (file == null) return null;

    return LocalFileSystem.getInstance().findFileByIoFile(myScheme == StorageScheme.DEFAULT ? file.getParentFile() : file.getParentFile().getParentFile());
  }

  public void setStorageFormat(final StorageFormat storageFormat) {
  }

  public String getLocation() {
    if (myScheme == StorageScheme.DEFAULT) return getProjectFilePath();
    else return getProjectBaseDir().getPath();
  }

  @NotNull
  public String getProjectName() {
    if (myScheme == StorageScheme.DIRECTORY_BASED) {
      final VirtualFile baseDir = getProjectBaseDir();
      assert baseDir != null;
      return baseDir.getName();
    }

    String temp = getProjectFileName();
    if (temp.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      temp = temp.substring(0, temp.length() - ProjectFileType.DOT_DEFAULT_EXTENSION.length());
    }
    final int i = temp.lastIndexOf(File.separatorChar);
    if (i >= 0) {
      temp = temp.substring(i + 1, temp.length() - i + 1);
    }
    return temp;
  }

  @Nullable
  public String getPresentableUrl() {
    if (myScheme == StorageScheme.DIRECTORY_BASED) {
      final VirtualFile baseDir = getProjectBaseDir();
      return baseDir != null ? baseDir.getPresentableUrl() : null;
    }

    final VirtualFile projectFile = getProjectFile();
    return projectFile != null ? projectFile.getPresentableUrl() : null;
  }


  public void loadProject() throws IOException, JDOMException, InvalidDataException {
    final boolean macrosOk = checkMacros(getDefinedMacros());
    if (!macrosOk) {
      throw new IOException(ProjectBundle.message("project.load.undefined.path.variables.error"));
    }

    load();
    myProject.init();
  }

  public void load() throws IOException {
    super.load();
    try {
      final StateStorage stateStorage = getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
      if  (stateStorage instanceof XmlElementStorage) {
        XmlElementStorage xmlElementStorage = (XmlElementStorage)stateStorage;
        Document doc = xmlElementStorage.getDocument();
        if (doc != null) {
          final Element element = doc.getRootElement();
          final List attributes = element.getAttributes();
          for (Object attribute : attributes) {
            Attribute attr = (Attribute)attribute;
            final String optionName = attr.getName();
            final @NonNls String optionValue = attr.getValue();

            if (optionName.equals(RELATIVE_PATHS_OPTION) && optionValue.equals("true")) {
              setSavePathsRelative(true);
            }
          }
        }
      }
    }
    catch (StateStorage.StateStorageException e) {
      LOG.info(e);
      throw new IOException(e.getMessage());
    }
  }

  private boolean checkMacros(Set<String> definedMacros) throws IOException, JDOMException {
    String projectFilePath = getProjectFilePath();

    final IFile iFile = FileSystem.FILE_SYSTEM.createFile(projectFilePath);

    if (!iFile.exists()) return true;

    Document document = JDOMUtil.loadDocument(iFile);
    Element root = document.getRootElement();
    final Set<String> usedMacros = new HashSet<String>(Arrays.asList(readUsedMacros(root)));

    usedMacros.removeAll(definedMacros);

    // try to lookup values in System properties
    @NonNls final String pathMacroSystemPrefix = "path.macro.";
    for (Iterator it = usedMacros.iterator(); it.hasNext();) {
      final String macro = (String)it.next();
      final String value = System.getProperty(pathMacroSystemPrefix + macro, null);
      if (value != null) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            PathMacrosImpl.getInstanceEx().setMacro(macro, value);
          }
        });
        it.remove();
      }
    }

    if (usedMacros.isEmpty()) {
      return true; // all macros in configuration files are defined
    }

    // there are undefined macros, need to define them before loading components
    return ProjectManagerImpl.showMacrosConfigurationDialog(myProject, usedMacros);
  }

  private static Set<String> getDefinedMacros() {
    final PathMacros pathMacros = PathMacros.getInstance();

    Set<String> definedMacros = new HashSet<String>(pathMacros.getUserMacroNames());
    definedMacros.addAll(pathMacros.getSystemMacroNames());
    definedMacros = Collections.unmodifiableSet(definedMacros);
    return definedMacros;
  }


  @Nullable
  public VirtualFile getProjectFile() {
    if (myProject.isDefault()) return null;
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    assert storage != null;
    return storage.getVirtualFile();
  }

  @Nullable
  public VirtualFile getWorkspaceFile() {
    if (myProject.isDefault()) return null;
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(WS_FILE_STORAGE);
    assert storage != null;
    return storage.getVirtualFile();
  }

  public void loadProjectFromTemplate(final ProjectImpl defaultProject) {
    final StateStorage stateStorage = getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);

    assert stateStorage instanceof XmlElementStorage;
    XmlElementStorage xmlElementStorage = (XmlElementStorage)stateStorage;

    defaultProject.save();
    final IProjectStore projectStore = defaultProject.getStateStore();
    assert projectStore instanceof DefaultProjectStoreImpl;
    DefaultProjectStoreImpl defaultProjectStore = (DefaultProjectStoreImpl)projectStore;
    final Element element = defaultProjectStore.getStateCopy();
    if (element != null) {
      xmlElementStorage.setDefaultState(element);
    }
  }

  @NotNull
  public String getProjectFileName() {
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    assert storage != null;
    return storage.getFileName();
  }

  @NotNull
  public String getProjectFilePath() {
    if (myProject.isDefault()) return "";

    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(PROJECT_FILE_STORAGE);
    assert storage != null;
    return storage.getFilePath();
  }

  public Set<String> getMacroTrackingSet() {
    return myTrackingSet;
  }


  protected XmlElementStorage getMainStorage() {
    final XmlElementStorage storage = (XmlElementStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
    assert storage != null;
    return storage;
  }

  private static boolean isWorkspace(final Map options) {
    return options != null && Boolean.parseBoolean((String)options.get(OPTION_WORKSPACE));
  }

  public void initStore() {
  }

  public List<VirtualFile> getAllStorageFiles(final boolean includingSubStructures) {
    final List<VirtualFile> result = super.getAllStorageFiles(includingSubStructures);

    if (includingSubStructures) {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      final Module[] modules = moduleManager.getModules();
      for (Module module : modules) {
        result.addAll(((ModuleImpl)module).getStateStore().getAllStorageFiles(includingSubStructures));
      }
    }

    return result;
  }


  public List<VirtualFile> getAllStorageFilesToSave(final boolean includingSubStructures) {
    final List<VirtualFile> result = super.getAllStorageFilesToSave(includingSubStructures);

    if (includingSubStructures) {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      final Module[] modules = moduleManager.getModules();
      for (Module module : modules) {
        result.addAll(((ModuleImpl)module).getStateStore().getAllStorageFilesToSave(includingSubStructures));
      }
    }

    return result;
  }

  @Override
  protected StateStorage getOldStorage(final Object component, final String componentName, final StateStorageOperation operation) throws
                                                                                                                                  StateStorage.StateStorageException {
    final ComponentConfig config = getComponentManager().getConfig(component.getClass());
    assert config != null: "Couldn't find old storage for " + component.getClass().getName();

    String macro = PROJECT_FILE_MACRO;

    final boolean workspace = isWorkspace(config.options);

    if (workspace) {
      macro = WS_FILE_MACRO;
    }

    StateStorage storage = getStateStorageManager().getFileStateStorage("$" + macro + "$");

    if (operation == StateStorageOperation.READ &&
        storage != null &&
        workspace &&
        !storage.hasState(component, componentName, Element.class)) {
      storage = getStateStorageManager().getFileStateStorage("$" + PROJECT_FILE_MACRO + "$");
    }

    return storage;
  }

  protected StateStorageManager createStateStorageManager() {
    return new ProjectStateStorageManager(PathMacroManager.getInstance(getComponentManager()).createTrackingSubstitutor(myTrackingSet), myProject);
  }

  @Override
  public void commit() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    final Module[] modules = moduleManager.getModules();
    for (Module module : modules) {
      ((ModuleImpl)module).getStateStore().commit();
    }

    super.commit();
  }

  private StateStorageChooser myStateStorageChooser = new StateStorageChooser() {
    public Storage[] selectStorages(final Storage[] storages, final Object component, final StateStorageOperation operation) {
      if (operation == StateStorageOperation.READ) {
        Storage currentStorage = null;
        Storage defaultStorage = null;

        for (Storage storage : storages) {
          if (storage.scheme() == myScheme) currentStorage = storage;
        }

        for (Storage storage : storages) {
          if (storage.scheme() == StorageScheme.DEFAULT) defaultStorage = storage;
        }

        if (currentStorage != null && defaultStorage != null) return new Storage[]{currentStorage, defaultStorage};
        else if (defaultStorage != null) return new Storage[]{defaultStorage};
        else if (currentStorage != null) return new Storage[]{currentStorage};
        else return new Storage[]{};
      }
      else if (operation == StateStorageOperation.WRITE) {
        for (Storage storage : storages) {
          if (storage.scheme() == myScheme) return new Storage[]{storage};
        }

        for (Storage storage : storages) {
          if (storage.scheme() == StorageScheme.DEFAULT) return new Storage[]{storage};
        }

        return new Storage[]{};
      }

      return new Storage[]{};
    }
  };

  @Nullable
  protected StateStorageChooser getDefaultStateStorageChooser() {
    return myStateStorageChooser;
  }
}

