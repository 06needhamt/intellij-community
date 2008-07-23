package com.intellij.lang.ant.config.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.DataAccessors;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.TargetAction;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ActionRunner;
import com.intellij.util.EventDispatcher;
import com.intellij.util.StringSetSpinAllocator;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.ValueProperty;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

@State(
    name = "AntConfiguration",
    storages = {
      @Storage(id = "default", file = "$PROJECT_FILE$"), 
      @Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/ant.xml", scheme = StorageScheme.DIRECTORY_BASED)
    }
)
public class AntConfigurationImpl extends AntConfigurationBase implements PersistentStateComponent<Element>, ModificationTracker {

  public static final ValueProperty<AntReference> DEFAULT_ANT = new ValueProperty<AntReference>("defaultAnt", AntReference.BUNDLED_ANT);
  public static final ValueProperty<AntConfiguration> INSTANCE = new ValueProperty<AntConfiguration>("$instance", null);
  public static final AbstractProperty<String> DEFAULT_JDK_NAME = new AbstractProperty<String>() {
    public String getName() {
      return "$defaultJDKName";
    }

    @Nullable
    public String getDefault(final AbstractPropertyContainer container) {
      return get(container);
    }

    @Nullable
    public String get(final AbstractPropertyContainer container) {
      if (!container.hasProperty(this)) return null;
      AntConfiguration antConfiguration = AntConfigurationImpl.INSTANCE.get(container);
      return ProjectRootManager.getInstance(antConfiguration.getProject()).getProjectJdkName();
    }

    public String copy(final String jdkName) {
      return jdkName;
    }
  };

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntConfigurationImpl");
  @NonNls private static final String BUILD_FILE = "buildFile";
  @NonNls private static final String CONTEXT_MAPPING = "contextMapping";
  @NonNls private static final String CONTEXT = "context";
  @NonNls private static final String URL = "url";
  @NonNls private static final String EXECUTE_ON_ELEMENT = "executeOn";
  @NonNls private static final String EVENT_ELEMENT = "event";
  @NonNls private static final String TARGET_ELEMENT = "target";

  private final PsiManager myPsiManager;
  private final Map<ExecutionEvent, Pair<AntBuildFile, String>> myEventToTargetMap =
    new HashMap<ExecutionEvent, Pair<AntBuildFile, String>>();
  private final List<AntBuildFile> myBuildFiles = new ArrayList<AntBuildFile>();
  private final Map<AntBuildFile, AntBuildModelBase> myModelToBuildFileMap = new HashMap<AntBuildFile, AntBuildModelBase>();
  private final Map<VirtualFile, VirtualFile> myAntFileToContextFileMap = new java.util.HashMap<VirtualFile, VirtualFile>();
  private final EventDispatcher<AntConfigurationListener> myEventDispatcher = EventDispatcher.create(AntConfigurationListener.class);
  private final AntWorkspaceConfiguration myAntWorkspaceConfiguration;
  private final StartupManager myStartupManager;
  private volatile long myModificationCount = 0;

  public AntConfigurationImpl(final Project project, final AntWorkspaceConfiguration antWorkspaceConfiguration, final DaemonCodeAnalyzer daemon) {
    super(project);
    getProperties().registerProperty(DEFAULT_ANT, AntReference.EXTERNALIZER);
    getProperties().rememberKey(INSTANCE);
    getProperties().rememberKey(DEFAULT_JDK_NAME);
    INSTANCE.set(getProperties(), this);
    myAntWorkspaceConfiguration = antWorkspaceConfiguration;
    myPsiManager = PsiManager.getInstance(project);
    myStartupManager = StartupManager.getInstance(project);
    addAntConfigurationListener(new AntConfigurationListener() {
      public void configurationLoaded() {
        restartDaemon();
      }
      public void buildFileChanged(final AntBuildFile buildFile) {
        restartDaemon();
      }
      public void buildFileAdded(final AntBuildFile buildFile) {
        restartDaemon();
      }
      public void buildFileRemoved(final AntBuildFile buildFile) {
        restartDaemon();
      }
      private void restartDaemon() {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          daemon.restart();
        }
        else {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              daemon.restart();
            }
          });
        }
      }
    });
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      public void beforeFileDeletion(final VirtualFileEvent event) {
        final VirtualFile vFile = event.getFile();
        final AntFile antFile = toAntFile(vFile);
        if (antFile != null) {
          // cleanup
          for (AntBuildFile file : getBuildFiles()) {
            if (antFile.equals(file.getAntFile())) {
              removeBuildFile(file);
              break;
            }
          }
          for (Iterator<VirtualFile> it = myAntFileToContextFileMap.keySet().iterator(); it.hasNext();) {
            final VirtualFile file = it.next();
            if (vFile.equals(file) || vFile.equals(myAntFileToContextFileMap.get(file))) {
              it.remove();
            }
          }
        }
      }
    }, project);
  }


  public Element getState() {
    try {
      final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  private volatile Boolean myIsInitialized = null;
  
  public boolean isInitialized() {
    final Boolean initialized = myIsInitialized;
    return initialized == null || initialized.booleanValue();
  }

  public AntBuildFile[] getBuildFiles() {
    synchronized (myBuildFiles) {
      return myBuildFiles.toArray(new AntBuildFile[myBuildFiles.size()]);
    }
  }

  public AntBuildFile addBuildFile(final VirtualFile file) throws AntNoFileException {
    final AntBuildFile[] result = new AntBuildFile[]{null};
    final AntNoFileException[] ex = new AntNoFileException[]{null};
    final String title = AntBundle.message("register.ant.build.progress", file.getPresentableUrl());
    ProgressManager.getInstance().run(new Task.Modal(getProject(), title, false) {
      @Nullable
      public NotificationInfo getNotificationInfo() {
        return new NotificationInfo("Ant", "Ant Task Finished", "");
      }

      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        indicator.pushState();
        try {
          indicator.setText(title);
          myModificationCount++;
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              try {
                result[0] = addBuildFileImpl(file);
                updateRegisteredActions();
              }
              catch (AntNoFileException e) {
                ex[0] = e;
              }
            }
          });
          if (result[0] != null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                myEventDispatcher.getMulticaster().buildFileAdded(result[0]);
              }
            });
          }
        }
        finally {
          indicator.popState();
        }
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }
    return result[0];
  }

  public void removeBuildFile(final AntBuildFile file) {
    myModificationCount++;
    removeBuildFileImpl(file);
    updateRegisteredActions();
  }

  public void addAntConfigurationListener(final AntConfigurationListener listener) {
    myEventDispatcher.addListener(listener);
  }

  public void removeAntConfigurationListener(final AntConfigurationListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  public boolean isFilterTargets() {
    return myAntWorkspaceConfiguration.FILTER_TARGETS;
  }

  public void setFilterTargets(final boolean value) {
    myAntWorkspaceConfiguration.FILTER_TARGETS = value;
  }

  public AntBuildTarget[] getMetaTargets(final AntBuildFile buildFile) {
    final List<ExecutionEvent> events = getEventsByClass(ExecuteCompositeTargetEvent.class);
    if (events.size() == 0) {
      return AntBuildTargetBase.EMPTY_ARRAY;
    }
    final List<AntBuildTargetBase> targets = new ArrayList<AntBuildTargetBase>();
    for (ExecutionEvent event : events) {
      final MetaTarget target = (MetaTarget)getTargetForEvent(event);
      if (target != null && buildFile.equals(target.getBuildFile())) {
        targets.add(target);
      }
    }
    return targets.toArray(new AntBuildTargetBase[targets.size()]);
  }

  public List<ExecutionEvent> getEventsForTarget(final AntBuildTarget target) {
    final List<ExecutionEvent> list = new ArrayList<ExecutionEvent>();
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        final AntBuildTarget targetForEvent = getTargetForEvent(event);
        if (target.equals(targetForEvent)) {
          list.add(event);
        }
      }
    }
    return list;
  }

  @Nullable
  public AntBuildTarget getTargetForEvent(final ExecutionEvent event) {
    final Pair<AntBuildFile, String> pair;
    synchronized (myEventToTargetMap) {
      pair = myEventToTargetMap.get(event);
    }
    if (pair == null) {
      return null;
    }
    final AntBuildFileBase buildFile = (AntBuildFileBase)pair.first;
    synchronized (myBuildFiles) {
      if (!myBuildFiles.contains(buildFile)) {
        return null; // file was removed
      }
    }
    final String targetName = (String)pair.second;

    final AntBuildTarget antBuildTarget = buildFile.getModel().findTarget(targetName);
    if (antBuildTarget != null) {
      return antBuildTarget;
    }
    final List<ExecutionEvent> events = getEventsByClass(ExecuteCompositeTargetEvent.class);
    if (events.size() == 0) {
      return null;
    }
    for (ExecutionEvent ev : events) {
      final String presentableName = ev.getPresentableName();
      if (Comparing.strEqual(targetName, presentableName)) {
        return new MetaTarget(buildFile, presentableName, ((ExecuteCompositeTargetEvent)ev).getTargetNames());
      }
    }
    return null;
  }

  public void setTargetForEvent(final AntBuildFile buildFile, final String targetName, final ExecutionEvent event) {
    synchronized (myEventToTargetMap) {
      myEventToTargetMap.put(event, new Pair<AntBuildFile, String>(buildFile, targetName));
    }
  }

  public void clearTargetForEvent(final ExecutionEvent event) {
    synchronized (myEventToTargetMap) {
      myEventToTargetMap.remove(event);
    }
  }

  public void updateBuildFile(final AntBuildFile buildFile) {
    myModificationCount++;
    myEventDispatcher.getMulticaster().buildFileChanged(buildFile);
    updateRegisteredActions();
  }

  public boolean isAutoScrollToSource() {
    return myAntWorkspaceConfiguration.IS_AUTOSCROLL_TO_SOURCE;
  }

  public void setAutoScrollToSource(final boolean value) {
    myAntWorkspaceConfiguration.IS_AUTOSCROLL_TO_SOURCE = value;
  }

  public AntInstallation getProjectDefaultAnt() {
    return DEFAULT_ANT.get(getProperties()).find(GlobalAntConfiguration.getInstance());
  }

  @Nullable
  public AntBuildModel getModelIfRegistered(final AntBuildFile buildFile) {
    synchronized (myBuildFiles) {
      if (!myBuildFiles.contains(buildFile)) return null;
    }
    return getModel(buildFile);
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  private void readExternal(final Element parentNode) throws InvalidDataException {
    myIsInitialized = Boolean.FALSE;
    myAntWorkspaceConfiguration.loadFromProjectSettings(parentNode);
    getProperties().readExternal(parentNode);
    runWhenInitialized(new Runnable() {
      public void run() {
        loadBuildFileProjectProperties(parentNode);
      }
    });
  }

  private void runWhenInitialized(final Runnable runnable) {
    if (getProject().isInitialized()) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          runnable.run();
        }
      });
    }
    else {
      myStartupManager.registerPostStartupActivity(new Runnable() {
        public void run() {
          runnable.run();
        }
      });
    }
  }

  private void writeExternal(final Element parentNode) throws WriteExternalException {
    getProperties().writeExternal(parentNode);
    try {
      ActionRunner.runInsideReadAction(new ActionRunner.InterruptibleRunnable() {
        public void run() throws WriteExternalException {
          for (final AntBuildFile buildFile : getBuildFiles()) {
            final Element element = new Element(BUILD_FILE);
            element.setAttribute(URL, buildFile.getVirtualFile().getUrl());
            ((AntBuildFileBase)buildFile).writeProperties(element);
            saveEvents(element, buildFile);
            parentNode.addContent(element);
          }
          final List<VirtualFile> files = new ArrayList<VirtualFile>(myAntFileToContextFileMap.keySet());
          // sort in order to minimize changes
          Collections.sort(files, new Comparator<VirtualFile>() {
            public int compare(final VirtualFile o1, final VirtualFile o2) {
              return o1.getUrl().compareTo(o2.getUrl());
            }
          });
          for (VirtualFile file : files) {
            final Element element = new Element(CONTEXT_MAPPING);
            final VirtualFile contextFile = myAntFileToContextFileMap.get(file);
            element.setAttribute(URL, file.getUrl());
            element.setAttribute(CONTEXT, contextFile.getUrl());
            parentNode.addContent(element);
          }
        }
      });
    }
    catch (WriteExternalException e) {
      LOG.error(e);
      throw e;
    }
    catch (RuntimeException e) {
      LOG.error(e);
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void saveEvents(final Element element, final AntBuildFile buildFile) {
    List<Element> events = null;
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        final Pair<AntBuildFile, String> pair = myEventToTargetMap.get(event);
        if (!buildFile.equals(pair.first)) {
          continue;
        }
        Element eventElement = new Element(EXECUTE_ON_ELEMENT);
        eventElement.setAttribute(EVENT_ELEMENT, event.getTypeId());
        eventElement.setAttribute(TARGET_ELEMENT, pair.second);
        event.writeExternal(eventElement);
        if (events == null) {
          events = new ArrayList<Element>();
        }
        events.add(eventElement);
      }
    }

    if (events != null) {
      Collections.sort(events, EventElementComparator.INSTANCE);
      for (Element eventElement : events) {
        element.addContent(eventElement);
      }
    }
  }

  public AntBuildModel getModel(final AntBuildFile buildFile) {
    AntBuildModelBase model = myModelToBuildFileMap.get(buildFile);
    if (model == null) {
      model = createModel(buildFile);
      myModelToBuildFileMap.put(buildFile, model);
    }
    return model;
  }

  @Nullable
  public AntBuildFile findBuildFileByActionId(final String id) {
    for (AntBuildFile buildFile : getBuildFiles()) {
      AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
      if (id.equals(model.getDefaultTargetActionId())) {
        return buildFile;
      }
      if (model.hasTargetWithActionId(id)) return buildFile;
    }
    return null;
  }

  public boolean hasTasksToExecuteBeforeRun(final RunConfiguration configuration) {
    return findExecuteBeforeRunEvent(configuration) != null;
  }

  public boolean executeTaskBeforeRun(final DataContext context, final RunConfiguration configuration) {
    final ExecuteBeforeRunEvent foundEvent = findExecuteBeforeRunEvent(configuration);
    return runTargetSynchronously(context, foundEvent);
  }

  public AntBuildTarget getTargetForBeforeRunEvent(RunConfiguration configuration) {
    return getTargetForEvent(new ExecuteBeforeRunEvent(configuration));
  }

  public void setTargetForBeforeRunEvent(AntBuildFile buildFile,
                                         String targetName,
                                         RunConfiguration configuration) {
    setTargetForEvent(buildFile, targetName, new ExecuteBeforeRunEvent(configuration));
  }

  private AntBuildModelBase createModel(final AntBuildFile buildFile) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      // otherwise commitAllDocuments() must have been called before the whole process was started
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }
    return new AntBuildModelImpl(buildFile);
  }

  private AntBuildFileBase addBuildFileImpl(final VirtualFile file) throws AntNoFileException {
    PsiFile psiFile = myPsiManager.findFile(file);
    if (psiFile == null) {
      throw new AntNoFileException(AntBundle.message("cant.add.file.error.message"), file);
    }
    AntSupport.markFileAsAntFile(file, psiFile.getViewProvider(), true);
    psiFile = AntSupport.getAntFile(psiFile);
    if (psiFile == null) {
      throw new AntNoFileException(AntBundle.message("cant.add.file.error.message"), file);
    }
    final AntFile antFile = (AntFile)psiFile;
    final AntBuildFileImpl buildFile = new AntBuildFileImpl(antFile, this);
    antFile.getSourceElement().putCopyableUserData(AntFileImpl.ANT_BUILD_FILE, buildFile);
    synchronized (myBuildFiles) {
      myBuildFiles.add(buildFile);
    }
    return buildFile;
  }

  private void updateRegisteredActions() {

    final List<Pair<String, AnAction>> actionList = new ArrayList<Pair<String, AnAction>>();
    for (final AntBuildFile buildFile : getBuildFiles()) {
      final AntBuildModelBase model = (AntBuildModelBase)buildFile.getModel();
      String defaultTargetActionId = model.getDefaultTargetActionId();
      if (defaultTargetActionId != null) {
        final TargetAction action =
          new TargetAction(buildFile, TargetAction.DEFAULT_TARGET_NAME, new String[]{TargetAction.DEFAULT_TARGET_NAME}, null);
        actionList.add(new Pair<String, AnAction>(defaultTargetActionId, action));
      }

      collectTargetActions(model.getFilteredTargets(), actionList, buildFile);
      collectTargetActions(getMetaTargets(buildFile), actionList, buildFile);
    }

    synchronized (this) {
      // unregister Ant actions
      ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
      final String[] oldIds = actionManager.getActionIds(AntConfiguration.getActionIdPrefix(getProject()));
      for (String oldId : oldIds) {
        actionManager.unregisterAction(oldId);
      }
      final Set<String> registeredIds = StringSetSpinAllocator.alloc();
      try {
        for (Pair<String, AnAction> pair : actionList) {
          if (!registeredIds.contains(pair.first)) {
            registeredIds.add(pair.first);
            actionManager.registerAction(pair.first, pair.second);
          }
        }
      }
      finally {
        StringSetSpinAllocator.dispose(registeredIds);
      }
    }
  }

  private static void collectTargetActions(final AntBuildTarget[] targets,
                                           final List<Pair<String, AnAction>> actionList,
                                           final AntBuildFile buildFile) {
    for (final AntBuildTarget target : targets) {
      final String actionId = ((AntBuildTargetBase)target).getActionId();
      if (actionId != null) {
        final TargetAction action =
          new TargetAction(buildFile, target.getName(), new String[]{target.getName()}, target.getNotEmptyDescription());
        actionList.add(new Pair<String, AnAction>(actionId, action));
      }
    }
  }

  private void removeBuildFileImpl(AntBuildFile buildFile) {
    final XmlFile xmlFile = ((AntFile)buildFile.getAntFile()).getSourceElement();
    xmlFile.putCopyableUserData(AntFileImpl.ANT_BUILD_FILE, null);
    AntSupport.markFileAsAntFile(xmlFile.getVirtualFile(), xmlFile.getViewProvider(), false);
    synchronized (myBuildFiles) {
      myBuildFiles.remove(buildFile);
    }
    myModelToBuildFileMap.remove(buildFile);
    myEventDispatcher.getMulticaster().buildFileRemoved(buildFile);
  }

  public boolean executeTargetBeforeCompile(final DataContext context) {
    return runTargetSynchronously(context, ExecuteBeforeCompilationEvent.getInstance());
  }

  public boolean executeTargetAfterCompile(final DataContext context) {
    return runTargetSynchronously(context, ExecuteAfterCompilationEvent.getInstance());
  }

  private boolean runTargetSynchronously(final DataContext dataContext, ExecutionEvent event) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      throw new IllegalStateException("Called in the event dispatch thread");
    }
    final AntBuildTarget target = getTargetForEvent(event);
    if (target == null) {
      // no task assigned
      return true;
    }
    final Semaphore targetDone = new Semaphore();
    final boolean[] result = new boolean[1];
    try {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {

        public void run() {
          Project project = DataAccessors.PROJECT.from(dataContext);
          if (project == null || project.isDisposed()) {
            result[0] = false;
            return;
          }
          targetDone.down();
          target.run(dataContext, new AntBuildListener() {
            public void buildFinished(int state, int errorCount) {
              result[0] = (state == AntBuildListener.FINISHED_SUCCESSFULLY) && (errorCount == 0);
              targetDone.up();
            }
          });
        }
      }, ModalityState.NON_MODAL);
    }
    catch (Exception e) {
      LOG.error(e);
      return false;
    }
    targetDone.waitFor();
    return result[0];
  }

  private List<ExecutionEvent> getEventsByClass(Class eventClass) {
    final List<ExecutionEvent> list = new ArrayList<ExecutionEvent>();
    synchronized (myEventToTargetMap) {
      for (final ExecutionEvent event : myEventToTargetMap.keySet()) {
        if (eventClass.isInstance(event)) {
          list.add(event);
        }
      }
    }
    return list;
  }

  private void loadBuildFileProjectProperties(final Element parentNode) {
    final List<Pair<Element, VirtualFile>> files = new ArrayList<Pair<Element, VirtualFile>>();
    final VirtualFileManager vfManager = VirtualFileManager.getInstance();
    for (final Object o : parentNode.getChildren(BUILD_FILE)) {
      final Element element = (Element)o;
      final String url = element.getAttributeValue(URL);
      final VirtualFile file = vfManager.findFileByUrl(url);
      if (file != null) {
        files.add(new Pair<Element, VirtualFile>(element, file));
      }
    }
    
    // contexts
    myAntFileToContextFileMap.clear();
    for (final Object o : parentNode.getChildren(CONTEXT_MAPPING)) {
      final Element element = (Element)o;
      final String url = element.getAttributeValue(URL);
      final String contextUrl = element.getAttributeValue(CONTEXT);
      final VirtualFile file = vfManager.findFileByUrl(url);
      final VirtualFile contextFile = vfManager.findFileByUrl(contextUrl);
      if (file != null && contextFile != null) {
        myAntFileToContextFileMap.put(file, contextFile);
      }
    }
    
    final String title = AntBundle.message("loading.ant.config.progress");
    queueLater(new Task.Backgroundable(getProject(), title, false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        indicator.pushState();
        try {
          indicator.setText(title);
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              try {
                // first, remove existing files
                final AntBuildFile[] currentFiles = getBuildFiles();
                for (AntBuildFile file : currentFiles) {
                  removeBuildFile(file);
                }
                // then fill the configuration with the files configured in xml
                for (Pair<Element, VirtualFile> pair : files) {
                  final Element element = pair.getFirst();
                  final VirtualFile file = pair.getSecond();
                  try {
                    final AntBuildFileBase buildFile = addBuildFileImpl(file);
                    buildFile.readProperties(element);

                    for (final Object o1 : element.getChildren(EXECUTE_ON_ELEMENT)) {
                      Element e = (Element)o1;
                      String eventId = e.getAttributeValue(EVENT_ELEMENT);
                      ExecutionEvent event = null;
                      String targetName = e.getAttributeValue(TARGET_ELEMENT);
                      if (ExecuteBeforeCompilationEvent.TYPE_ID.equals(eventId)) {
                        event = ExecuteBeforeCompilationEvent.getInstance();
                      }
                      else if (ExecuteAfterCompilationEvent.TYPE_ID.equals(eventId)) {
                        event = ExecuteAfterCompilationEvent.getInstance();
                      }
                      else if (ExecuteBeforeRunEvent.TYPE_ID.equals(eventId)) {
                        event = new ExecuteBeforeRunEvent();
                      }
                      else if (ExecuteCompositeTargetEvent.TYPE_ID.equals(eventId)) {
                        try {
                          event = new ExecuteCompositeTargetEvent(targetName);
                        }
                        catch (WrongNameFormatException e1) {
                          LOG.info(e1);
                          event = null;
                        }
                      }
                      if (event != null) {
                        event.readExternal(e);
                        setTargetForEvent(buildFile, targetName, event);
                      }
                    }

                  }
                  catch (AntNoFileException ignored) {
                  }
                  catch (InvalidDataException e) {
                    LOG.error(e);
                  }
                }
                AntWorkspaceConfiguration.getInstance(getProject()).loadFileProperties();
              }
              catch (InvalidDataException e) {
                LOG.error(e);
              }
              finally {
                updateRegisteredActions();
                myIsInitialized = Boolean.TRUE;
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    myEventDispatcher.getMulticaster().configurationLoaded();
                  }
                });
              }
            }
          });
        }
        finally {
          indicator.popState();
        }
      }
    });
  }
  
  private static void queueLater(final Task task) {
    final Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      task.queue();
    } else {
      app.invokeLater(new Runnable() {
        public void run() {
          task.queue();
        }
      });
    }
  }

  public void setContextFile(@NotNull AntFile file, @Nullable AntFile context) {
    if (context != null) {
      myAntFileToContextFileMap.put(file.getVirtualFile(), context.getVirtualFile());
    }
    else {
      myAntFileToContextFileMap.remove(file.getVirtualFile());
    }
    file.clearCaches();
  }

  public AntFile getContextFile(@Nullable final AntFile file) {
    return file != null? toAntFile(myAntFileToContextFileMap.get(file.getVirtualFile())) : null;
  }

  @Nullable
  public AntFile getEffectiveContextFile(final AntFile file) {
    return new Object() {
      @Nullable
      AntFile findContext(final AntFile file, Set<PsiElement> processed) {
        if (file != null) {
          processed.add(file);
          final AntFile contextFile = toAntFile(myAntFileToContextFileMap.get(file.getVirtualFile()));
          return (contextFile == null || processed.contains(contextFile))? file : findContext(contextFile, processed);
        }
        return null;
      }
    }.findContext(file, new HashSet<PsiElement>());
  }

  @Nullable
  private AntFile toAntFile(VirtualFile vFile) {
    if (vFile == null) {
      return null;
    }
    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(vFile);
    return psiFile != null? AntSupport.getAntFile(psiFile) : null;
  }
  
  @Nullable
  ExecuteBeforeRunEvent findExecuteBeforeRunEvent(RunConfiguration configuration) {
    for (final ExecutionEvent e : getEventsByClass(ExecuteBeforeRunEvent.class)) {
      final ExecuteBeforeRunEvent event = (ExecuteBeforeRunEvent)e;
      if (event.isFor(configuration)) return event;
    }
    return null;
  }

  private static class EventElementComparator implements Comparator<Element> {
    static final Comparator<? super Element> INSTANCE = new EventElementComparator();
    
    private static final String[] COMPARABLE_ATTRIB_NAMES = new String[] {
      EVENT_ELEMENT, 
      TARGET_ELEMENT, 
      ExecuteBeforeRunEvent.RUN_CONFIUGRATION_UUID_ATTR,
      ExecuteCompositeTargetEvent.PRESENTABLE_NAME
    };
    
    public int compare(final Element o1, final Element o2) {
      for (String attribName : COMPARABLE_ATTRIB_NAMES) {
        final int valuesEqual = Comparing.compare(o1.getAttributeValue(attribName), o2.getAttributeValue(attribName));
        if (valuesEqual != 0) {
          return valuesEqual;
        }
      }
      return 0;
    }
  }
}
