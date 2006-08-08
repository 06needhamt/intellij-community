/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.CommonBundle;
import com.intellij.find.FindBundle;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.ModuleGroupUtil;
import com.intellij.javaee.serverInstances.ApplicationServersManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurable;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.TreeToolTipHandler;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 02-Jun-2006
 */
public class ProjectRootConfigurable extends MasterDetailsComponent implements ProjectComponent {
  private static final Icon COMPACT_EMPTY_MIDDLE_PACKAGES_ICON = IconLoader.getIcon("/objectBrowser/compactEmptyPackages.png");
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");
  private static final Icon FIND_ICON = IconLoader.getIcon("/actions/find.png");

  public boolean myPlainMode;

  private MyNode myJdksNode;

  private MyNode myGlobalLibrariesNode;
  private LibrariesModifiableModel myGlobalLibrariesProvider;

  private LibrariesModifiableModel myProjectLibrariesProvider;

  private Map<Module, LibrariesModifiableModel> myModule2LibrariesMap = new HashMap<Module, LibrariesModifiableModel>();

  private MyNode myProjectNode;
  private MyNode myProjectLibrariesNode;
  private Project myProject;

  private ModuleManager myModuleManager;
  private ModulesConfigurator myModulesConfigurator;
  private ModulesConfigurable myModulesConfigurable;
  private ProjectJdksModel myJdksTreeModel = new ProjectJdksModel(this);

  private MyNode myApplicationServerLibrariesNode;
  private LibrariesModifiableModel myApplicationServerLibrariesProvider;

  private boolean myDisposed = true;

  private Map<Library, Set<String>> myLibraryDependencyCache = new HashMap<Library, Set<String>>();
  private Map<ProjectJdk, Set<String>> myJdkDependencyCache = new HashMap<ProjectJdk, Set<String>>();
  private Map<Module, Boolean> myValidityCache = new HashMap<Module, Boolean>();
  private Alarm myUpdateDependenciesAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  public ProjectRootConfigurable(Project project, ModuleManager manager) {
    myProject = project;
    myModuleManager = manager;
    addItemsChangeListener(new ItemsChangeListener() {
      public void itemChanged(@Nullable Object deletedItem) {
        if (deletedItem instanceof Library) {
          final Library library = (Library)deletedItem;
          final MyNode node = findNodeByObject(myRoot, library);
          if (node != null) {
            final TreeNode parent = node.getParent();
            node.removeFromParent();
            ((DefaultTreeModel)myTree.getModel()).reload(parent);
          }
        }
      }

      public void itemsExternallyChanged() {
        //do nothing
      }
    });
    initTree();
  }


  protected void initTree() {
    super.initTree();
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    }, true);
    TreeToolTipHandler.install(myTree);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    myTree.setCellRenderer(new ColoredTreeCellRenderer(){
      public void customizeCellRenderer(JTree tree,
                                        Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof MyNode) {
          final MyNode node = ((MyNode)value);
          final String displayName = node.getDisplayName();
          final Icon icon = node.getConfigurable().getIcon();
          setIcon(icon);
          setToolTipText(null);
          if (node.isDisplayInBold()){
            append(displayName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          } else {
            final Object object = node.getConfigurable().getEditableObject();
            final boolean unused = isUnused(object, node);
            final boolean invalid = isInvalid(object);
            if (unused || invalid){
              Color fg = unused
                         ? UIUtil.getTextInactiveTextColor()
                         : selected && hasFocus ? UIUtil.getTreeSelectionForeground() : UIUtil.getTreeForeground();
              append(displayName, new SimpleTextAttributes(invalid ? SimpleTextAttributes.STYLE_WAVED : SimpleTextAttributes.STYLE_PLAIN,
                                                           fg,
                                                           Color.red));
              setToolTipText(ProjectBundle.message("project.root.tooltip", displayName, invalid ? (unused ? 3 : 1) : 2));
            }
            else {
              append(displayName, selected && hasFocus ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        }
      }
    });
  }

  private boolean isInvalid(final Object object) {
    if (object instanceof Module){
      final Module module = (Module)object;
      if (myValidityCache.containsKey(module)) return myValidityCache.get(module).booleanValue();
      myUpdateDependenciesAlarm.addRequest(new Runnable(){
        public void run() {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              final OrderEntry[] entries = myModulesConfigurator.getRootModel(module).getOrderEntries();
              for (OrderEntry entry : entries) {
                if (!entry.isValid()){
                  myValidityCache.put(module, Boolean.TRUE);
                  SwingUtilities.invokeLater(new Runnable(){
                    public void run() {
                      if (!myDisposed){
                        myTree.repaint();
                      }
                    }
                  });
                  return;
                }
              }
              myValidityCache.put(module, Boolean.FALSE);
            }
          });
        }
      }, 0);
    } else if (object instanceof LibraryEx) {
      final LibraryEx library = (LibraryEx)object;
      return !(library.allPathsValid(OrderRootType.CLASSES) &&
               library.allPathsValid(OrderRootType.JAVADOC) &&
               library.allPathsValid(OrderRootType.SOURCES));
    }
    return false;
  }

  private boolean isUnused(final Object object, MyNode node) {
    if (object == null || object instanceof Module) return false;
    final Set<String> dependencies = getCachedDependencies(object, node, false);
    return dependencies != null && dependencies.size() == 0;
  }

  protected ArrayList<AnAction> getAdditionalActions() {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(ActionManager.getInstance().getAction(IdeActions.GROUP_MOVE_MODULE_TO_GROUP));
    return result;
  }

  protected void reloadTree() {

    myRoot.removeAllChildren();

    createProjectNodes();

    createProjectJdks();

    myGlobalLibrariesNode = createLibrariesNode(LibraryTablesRegistrar.getInstance().getLibraryTable(), myGlobalLibrariesProvider, getGlobalLibrariesProvider());

    myApplicationServerLibrariesNode = createLibrariesNode(ApplicationServersManager.getInstance().getLibraryTable(), myApplicationServerLibrariesProvider, getApplicationServerLibrariesProvider());

    ((DefaultTreeModel)myTree.getModel()).reload();

    myDisposed = false;
  }

  protected void updateSelection(NamedConfigurable configurable) {
    final String selectedTab = ModuleEditor.getSelectedTab();
    super.updateSelection(configurable);
    if (configurable instanceof ModuleConfigurable){
      final ModuleConfigurable moduleConfigurable = (ModuleConfigurable)configurable;
      moduleConfigurable.getModuleEditor().setSelectedTabName(selectedTab);
    }
  }

  private MyNode createLibrariesNode(final LibraryTable table,
                                     LibrariesModifiableModel provider,
                                     final LibraryTableModifiableModelProvider modelProvider) {
    provider = new LibrariesModifiableModel(table.getModifiableModel());
    LibrariesConfigurable librariesConfigurable = new LibrariesConfigurable(table.getTableLevel(), provider);
    MyNode node = new MyNode(librariesConfigurable, true);
    final Library[] libraries = provider.getLibraries();
    for (Library library : libraries) {
      addNode(new MyNode(new LibraryConfigurable(modelProvider, library, myProject, TREE_UPDATER)), node);
    }
    myRoot.add(node);
    return node;
  }

  private void createProjectJdks() {
    myJdksNode = new MyNode(new JdksConfigurable(myJdksTreeModel), true);
    final TreeMap<ProjectJdk, ProjectJdk> sdks = myJdksTreeModel.getProjectJdks();
    for (ProjectJdk sdk : sdks.keySet()) {
      final JdkConfigurable configurable = new JdkConfigurable((ProjectJdkImpl)sdks.get(sdk), myJdksTreeModel, TREE_UPDATER);
      addNode(new MyNode(configurable), myJdksNode);
    }
    myRoot.add(myJdksNode);
  }

  private void createProjectNodes() {
    myProjectNode = new MyNode(myModulesConfigurable, true);
    final Map<ModuleGroup, MyNode> moduleGroup2NodeMap = new HashMap<ModuleGroup, MyNode>();
    final Module[] modules = myModuleManager.getModules();
    for (final Module module : modules) {
      ModuleConfigurable configurable = new ModuleConfigurable(myModulesConfigurator, module, TREE_UPDATER);
      final MyNode moduleNode = new MyNode(configurable);
      createModuleLibraries(module, moduleNode);
      final String[] groupPath = myPlainMode ? null : myModulesConfigurator.getModuleModel().getModuleGroupPath(module);
      if (groupPath == null || groupPath.length == 0){
        addNode(moduleNode, myProjectNode);
      } else {
        final MyNode moduleGroupNode = ModuleGroupUtil
          .buildModuleGroupPath(new ModuleGroup(groupPath), myProjectNode, moduleGroup2NodeMap,
                                new Consumer<ModuleGroupUtil.ParentChildRelation<MyNode>>() {
                                  public void consume(final ModuleGroupUtil.ParentChildRelation<MyNode> parentChildRelation) {
                                    addNode(parentChildRelation.getChild(), parentChildRelation.getParent());
                                  }
                                },
                                new Function<ModuleGroup, MyNode>() {
                                  public MyNode fun(final ModuleGroup moduleGroup) {
                                    final NamedConfigurable moduleGroupConfigurable = new ModuleGroupConfigurable(moduleGroup);
                                    return new MyNode(moduleGroupConfigurable, true);
                                  }
                                });
        addNode(moduleNode, moduleGroupNode);
      }
    }

    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    myProjectLibrariesProvider = new LibrariesModifiableModel(table.getModifiableModel());
    final LibrariesConfigurable librariesConfigurable = new LibrariesConfigurable(table.getTableLevel(), myProjectLibrariesProvider);

    myProjectLibrariesNode = new MyNode(librariesConfigurable, true);
    final Library[] libraries = myProjectLibrariesProvider.getLibraries();
    for (Library library1 : libraries) {
      addNode(new MyNode(new LibraryConfigurable(getProjectLibrariesProvider(), library1, myProject, TREE_UPDATER)), myProjectLibrariesNode);
    }
    myProjectNode.add(myProjectLibrariesNode);

    myRoot.add(myProjectNode);
  }

  public boolean updateProjectTree(final Module[] modules, final ModuleGroup group) {
    if (myRoot.getChildCount() == 0) return false; //isn't visible
    final MyNode [] nodes = new MyNode[modules.length];
    int i = 0;
    for (Module module : modules) {
      MyNode node = findNodeByObject(myProjectNode, module);
      node.removeFromParent();
      nodes[i ++] = node;
    }
    for (final MyNode moduleNode : nodes) {
      final String[] groupPath = myPlainMode
                                 ? null
                                 : group != null ? group.getGroupPath() : null;
      if (groupPath == null || groupPath.length == 0){
        addNode(moduleNode, myProjectNode);
      } else {
        final MyNode moduleGroupNode = ModuleGroupUtil
          .updateModuleGroupPath(new ModuleGroup(groupPath), myProjectNode, new Function<ModuleGroup, MyNode>() {
            public MyNode fun(final ModuleGroup group) {
              return findNodeByObject(myProjectNode, group);
            }
          }, new Consumer<ModuleGroupUtil.ParentChildRelation<MyNode>>() {
            public void consume(final ModuleGroupUtil.ParentChildRelation<MyNode> parentChildRelation) {
              addNode(parentChildRelation.getChild(), parentChildRelation.getParent());
            }
          }, new Function<ModuleGroup, MyNode>() {
            public MyNode fun(final ModuleGroup moduleGroup) {
              final NamedConfigurable moduleGroupConfigurable = new ModuleGroupConfigurable(moduleGroup);
              return new MyNode(moduleGroupConfigurable, true);
            }
          });
        addNode(moduleNode, moduleGroupNode);
      }
    }
    ((DefaultTreeModel)myTree.getModel()).reload(myProjectNode);
    return true;
  }

  protected void addNode(MyNode nodeToAdd, MyNode parent) {
    parent.add(nodeToAdd);
    TreeUtil.sort(parent, new Comparator() {
      public int compare(final Object o1, final Object o2) {
        final MyNode node1 = (MyNode)o1;
        final MyNode node2 = (MyNode)o2;
        final Object editableObject1 = node1.getConfigurable().getEditableObject();
        final Object editableObject2 = node2.getConfigurable().getEditableObject();
        if (editableObject1.getClass() == editableObject2.getClass()) {
          return node1.getDisplayName().compareToIgnoreCase(node2.getDisplayName());
        }

        if (editableObject2 instanceof Module && editableObject1 instanceof ModuleGroup) return -1;
        if (editableObject1 instanceof Module && editableObject2 instanceof ModuleGroup) return 1;

        if (editableObject2 instanceof Module && editableObject1 instanceof String) return 1;
        if (editableObject1 instanceof Module && editableObject2 instanceof String) return -1;

        if (editableObject2 instanceof ModuleGroup && editableObject1 instanceof String) return 1;
        if (editableObject1 instanceof ModuleGroup && editableObject2 instanceof String) return -1;

        return 0;
      }
    });
    ((DefaultTreeModel)myTree.getModel()).reload(parent);
  }

  private void createModuleLibraries(final Module module, final MyNode moduleNode) {
    final LibraryTableModifiableModelProvider libraryTableModelProvider = new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        return myModule2LibrariesMap.get(module);
      }

      public String getTableLevel() {
        return LibraryTableImplUtil.MODULE_LEVEL;
      }
    };

    final OrderEntry[] entries = myModulesConfigurator.getModuleEditor(module).getModifiableRootModel().getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry orderEntry = (LibraryOrderEntry)entry;
        if (orderEntry.isModuleLevel()) {
          final Library library = orderEntry.getLibrary();
          if (library.getName() == null && orderEntry.getPresentableName() == null) continue;
          final LibraryConfigurable libraryConfigurable =
            new LibraryConfigurable(libraryTableModelProvider, library, trancateModuleLibraryName(orderEntry), myProject, TREE_UPDATER);
          addNode(new MyNode(libraryConfigurable), moduleNode);
        }
      }
    }
  }

  public static String trancateModuleLibraryName(LibraryOrderEntry entry) {
    final String presentableName = entry.getPresentableName();
    String independantName = FileUtil.toSystemIndependentName(presentableName);
    if (independantName.lastIndexOf('/') + 1 == independantName.length() && independantName.length() > 1){
      independantName = independantName.substring(0, independantName.length() - 2);
    }
    return independantName.substring(independantName.lastIndexOf("/") + 1);
  }


  public ProjectJdksModel getProjectJdksModel() {
    return myJdksTreeModel;
  }

  public LibraryTableModifiableModelProvider getGlobalLibrariesProvider() {
    return new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        return myGlobalLibrariesProvider;
      }

      public String getTableLevel() {
        return LibraryTablesRegistrar.APPLICATION_LEVEL;
      }
    };
  }

  public LibraryTableModifiableModelProvider getProjectLibrariesProvider() {
    return new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        return myProjectLibrariesProvider;
      }

      public String getTableLevel() {
        return LibraryTablesRegistrar.PROJECT_LEVEL;
      }
    };
  }

  public void reset() {
    myJdksTreeModel.reset();
    myModulesConfigurator = new ModulesConfigurator(myProject, this);
    myModulesConfigurator.resetModuleEditors();
    myModulesConfigurable = myModulesConfigurator.getModulesConfigurable();
    final LibraryTablesRegistrar tablesRegistrar = LibraryTablesRegistrar.getInstance();
    myProjectLibrariesProvider = new LibrariesModifiableModel(tablesRegistrar.getLibraryTable(myProject).getModifiableModel());
    myGlobalLibrariesProvider = new LibrariesModifiableModel(tablesRegistrar.getLibraryTable().getModifiableModel());
    myApplicationServerLibrariesProvider =
      new LibrariesModifiableModel(ApplicationServersManager.getInstance().getLibraryTable().getModifiableModel());
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final ModifiableRootModel modelProxy = myModulesConfigurator.getModuleEditor(module).getModifiableRootModelProxy();
      myModule2LibrariesMap.put(module, new LibrariesModifiableModel(modelProxy.getModuleLibraryTable().getModifiableModel()));
    }
    reloadTree();
    super.reset();
  }


  public void apply() throws ConfigurationException {
    final Set<MyNode> roots = new HashSet<MyNode>();
    roots.add(myProjectNode);
    if (!canApply(roots, ProjectBundle.message("rename.message.prefix.module"), ProjectBundle.message("rename.module.title"))) return;
    boolean modifiedJdks = false;
    for (int i = 0; i < myJdksNode.getChildCount(); i++) {
      final NamedConfigurable configurable = ((MyNode)myJdksNode.getChildAt(i)).getConfigurable();
      if (configurable.isModified()) {
        configurable.apply();
        modifiedJdks = true;
      }
    }

    if (myJdksTreeModel.isModified() || modifiedJdks) myJdksTreeModel.apply();
    myJdksTreeModel.setProjectJdk(ProjectRootManager.getInstance(myProject).getProjectJdk());
    if (isInitialized(myModulesConfigurable) && myModulesConfigurable.isModified()) myModulesConfigurable.apply();
    if (myModulesConfigurator.isModified()) myModulesConfigurator.apply();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myProjectLibrariesProvider.deferredCommit();
        myGlobalLibrariesProvider.deferredCommit();
        myApplicationServerLibrariesProvider.deferredCommit();
        for (Module module : myModule2LibrariesMap.keySet()) {
          if (!module.isDisposed()){ //do not update deleted modules
            myModule2LibrariesMap.get(module).deferredCommit();
          }
        }
      }
    });

    //cleanup
    myUpdateDependenciesAlarm.cancelAllRequests();
    myUpdateDependenciesAlarm.addRequest(new Runnable(){
      public void run() {
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
             dispose();
             reset();
          }
        });
      }
    }, 0);
  }

  public boolean isModified() {
    boolean isModified = myModulesConfigurator.isModified();
    for (LibrariesModifiableModel model : myModule2LibrariesMap.values()) {
      final Library[] libraries = model.getLibraries();
      for (Library library : libraries) {
        if (model.hasLibraryEditor(library) && model.getLibraryEditor(library).hasChanges()) return true;
      }
    }
    for (int i = 0; i < myJdksNode.getChildCount(); i++) {
      final NamedConfigurable configurable = ((MyNode)myJdksNode.getChildAt(i)).getConfigurable();
      if (configurable.isModified()) {
        return true;
      }
    }
    isModified |= isInitialized(myModulesConfigurable) && myModulesConfigurable.isModified();
    isModified |= myJdksTreeModel.isModified();
    isModified |= myGlobalLibrariesProvider.isChanged();
    isModified |= myApplicationServerLibrariesProvider.isChanged();
    isModified |= myProjectLibrariesProvider.isChanged();
    return isModified;
  }

  public void disposeUIResources() {
    myUpdateDependenciesAlarm.cancelAllRequests();
    myUpdateDependenciesAlarm.addRequest(new Runnable(){
      public void run() {
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            dispose();
          }
        });
      }
    }, 0);
  }

  private void dispose() {
    myDisposed = true;
    myJdksTreeModel.disposeUIResources();
    myModulesConfigurator.disposeUIResources();
    myModule2LibrariesMap.clear();
    myProjectLibrariesProvider = null;
    myGlobalLibrariesProvider = null;
    myApplicationServerLibrariesProvider = null;
    myJdkDependencyCache.clear();
    myLibraryDependencyCache.clear();
    myValidityCache.clear();
    ProjectRootConfigurable.super.disposeUIResources();
  }


  public JComponent createComponent() {
    return new MyDataProviderWrapper(super.createComponent());
  }

  protected void processRemovedItems() {
    // do nothing
  }

  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.roots.display.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    final TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath != null) {
      MyNode node = (MyNode)selectionPath.getLastPathComponent();
      final NamedConfigurable configurable = node.getConfigurable();
      if (configurable != null) {
        return configurable.getHelpTopic();
      }
    }
    return "root.settings";
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }


  protected ArrayList<AnAction> createActions() {
    final ArrayList<AnAction> result = new ArrayList<AnAction>();
    result.add(new MyAddAction());
    result.add(new MyRemoveAction(new Condition<Object>() {
      public boolean value(final Object object) {
        if (object instanceof MyNode) {
          final NamedConfigurable namedConfigurable = ((MyNode)object).getConfigurable();
          final Object editableObject = namedConfigurable.getEditableObject();
          if (editableObject instanceof ProjectJdk ||
            editableObject instanceof Module) return true;
          if (editableObject instanceof Library){
            final LibraryTable table = ((Library)editableObject).getTable();
            return table == null || !ApplicationServersManager.APPLICATION_SERVER_MODULE_LIBRARIES.equals(table.getTableLevel());
          }
        }
        return false;
      }
    }));
    final AnAction findUsages = new AnAction(ProjectBundle.message("find.usages.action.text"),
                                             ProjectBundle.message("find.usages.action.text"),
                                             FIND_ICON) {
      public void update(AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        final TreePath selectionPath = myTree.getSelectionPath();
        if (selectionPath != null){
          final MyNode node = (MyNode)selectionPath.getLastPathComponent();
          presentation.setEnabled(!node.isDisplayInBold());
        } else {
          presentation.setEnabled(false);
        }
      }

      public void actionPerformed(AnActionEvent e) {
        showDependencies();
      }
    };
    findUsages.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES).getShortcutSet(), myTree);
    result.add(findUsages);
    result.add(new MyGroupAction());
    final TreeExpander expander = new TreeExpander() {
      public void expandAll() {
        TreeUtil.expandAll(myTree);
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(myTree, 0);
      }

      public boolean canCollapse() {
        return true;
      }
    };
    final CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    result.add(actionsManager.createExpandAllAction(expander));
    result.add(actionsManager.createCollapseAllAction(expander));
    return result;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootMasterDetailsConfigurable";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static ProjectRootConfigurable getInstance(final Project project) {
    return project.getComponent(ProjectRootConfigurable.class);
  }

  public void createNode(final NamedConfigurable<ProjectJdk> configurable, final MyNode parentNode) {
    final MyNode node = new MyNode(configurable);
    addNode(node, parentNode);
    selectNodeInTree(node);
  }

  public MyNode createLibraryNode(Library library, String presentableName) {
    final LibraryTable table = library.getTable();
    if (table != null){
      final String level = table.getTableLevel();
      if (level == LibraryTablesRegistrar.APPLICATION_LEVEL) {
        final LibraryConfigurable configurable = new LibraryConfigurable(getGlobalLibrariesProvider(), library, myProject, TREE_UPDATER);
        final MyNode node = new MyNode(configurable);
        addNode(node, myGlobalLibrariesNode);
        return node;
      }
      else if (level == LibraryTablesRegistrar.PROJECT_LEVEL) {
        final LibraryConfigurable configurable = new LibraryConfigurable(getProjectLibrariesProvider(), library, myProject, TREE_UPDATER);
        final MyNode node = new MyNode(configurable);
        addNode(node, myProjectLibrariesNode);
        return node;
      }
      else {
        final LibraryConfigurable configurable = new LibraryConfigurable(getApplicationServerLibrariesProvider(), library, myProject, TREE_UPDATER);
        final MyNode node = new MyNode(configurable);
        addNode(node, myApplicationServerLibrariesNode);
        return node;
      }
    } else { //module library
      Module module = (Module)getSelectedObject();
      final LibraryConfigurable configurable = new LibraryConfigurable(getModifiableModelProvider(myModulesConfigurator.getModuleEditor(module).getModifiableRootModelProxy()), library, presentableName, myProject, TREE_UPDATER);
      final MyNode node = new MyNode(configurable);
      addNode(node, (MyNode)myTree.getSelectionPath().getLastPathComponent());
      return node;
    }
  }

  private void showDependencies() {
    final Set<String> dependencies = getDependencies();
    if (dependencies == null || dependencies.size() == 0){
      Messages.showInfoMessage(myTree, FindBundle.message("find.usage.view.no.usages.text"), FindBundle.message("find.pointcut.applications.not.found.title"));
      return;
    }
    final int selectedRow = myTree.getSelectionRows()[0];
    final Rectangle rowBounds = myTree.getRowBounds(selectedRow);
    final Point location = rowBounds.getLocation();
    location.x += rowBounds.width;
    JBPopupFactory.getInstance().createWizardStep(new BaseListPopupStep<String>(ProjectBundle.message("dependencies.used.in.popup.title"),
                                                                                dependencies.toArray(new String[dependencies.size()])) {

      public PopupStep onChosen(final String nameToSelect, final boolean finalChoice) {
        selectNodeInTree(nameToSelect);
        return PopupStep.FINAL_CHOICE;
      }

      public Icon getIconFor(String selection){
        return myModulesConfigurator.getModule(selection).getModuleType().getNodeIcon(false);
      }

    }).show(new RelativePoint(myTree, location));
  }

  @Nullable
  private Set<String> getDependencies() {
    final Object selectedObject = getSelectedObject();
    final MyNode selectedNode = (MyNode)myTree.getSelectionPath().getLastPathComponent();
    return getCachedDependencies(selectedObject, selectedNode, true);
  }

  private Set<String> getCachedDependencies(final Object selectedObject, final MyNode selectedNode, boolean force) {
    if (selectedObject instanceof Library){
      final Library library = (Library)selectedObject;
      if (myLibraryDependencyCache.containsKey(library)){
        return myLibraryDependencyCache.get(library);
      }
    } else if (selectedObject instanceof ProjectJdk){
      final ProjectJdk projectJdk = (ProjectJdk)selectedObject;
      if (myJdkDependencyCache.containsKey(projectJdk)){
        return myJdkDependencyCache.get(projectJdk);
      }
    }
    final Computable<Set<String>> dependencies = new Computable<Set<String>>(){
      public Set<String> compute() {
        final Set<String> dependencies = getDependencies(selectedObject, selectedNode);
        if (selectedObject instanceof Library){
          myLibraryDependencyCache.put((Library)selectedObject, dependencies);
        } else if (selectedObject instanceof ProjectJdk){
          final ProjectJdk projectJdk = (ProjectJdk)selectedObject;
          myJdkDependencyCache.put(projectJdk, dependencies);
        }
        return dependencies;
      }
    };
    if (force){
      return dependencies.compute();
    } else {
      myUpdateDependenciesAlarm.addRequest(new Runnable(){
        public void run() {
          final Set<String> dep = dependencies.compute();
          SwingUtilities.invokeLater(new Runnable(){
            public void run() {
              if (dep != null && dep.size() == 0 && !myDisposed){
                myTree.repaint();
              }
            }
          });
        }
      }, 0);
      return null;
    }
  }

  private Set<String> getDependencies(final Object selectedObject, final MyNode node) {
    if (selectedObject instanceof Module) {
      return getDependencies(new Condition<OrderEntry>() {
        public boolean value(final OrderEntry orderEntry) {
          return orderEntry instanceof ModuleOrderEntry && Comparing.equal(((ModuleOrderEntry)orderEntry).getModule(), selectedObject);
        }
      });
    }
    else if (selectedObject instanceof Library) {
      if (((Library)selectedObject).getTable() == null) { //module library navigation
        final Set<String> set = new HashSet<String>();
        set.add(((MyNode)node.getParent()).getDisplayName());
        return set;
      }
      return getDependencies(new Condition<OrderEntry>() {
        @SuppressWarnings({"SimplifiableIfStatement"})
        public boolean value(final OrderEntry orderEntry) {
          if (orderEntry instanceof LibraryOrderEntry){
            final LibraryImpl library = (LibraryImpl)((LibraryOrderEntry)orderEntry).getLibrary();
            if (Comparing.equal(library, selectedObject)) return true;
            return library != null && Comparing.equal(library.getSource(), selectedObject);
          }
          return false;
        }
      });
    }
    else if (selectedObject instanceof ProjectJdk) {
      return getDependencies(new Condition<OrderEntry>() {
        public boolean value(final OrderEntry orderEntry) {
          return orderEntry instanceof JdkOrderEntry && Comparing.equal(((JdkOrderEntry)orderEntry).getJdk(), selectedObject);
        }
      });
    }
    return null;
  }

  private Set<String> getDependencies(Condition<OrderEntry> condition) {
    final Set<String> result = new TreeSet<String>();
    final Module[] modules = myModulesConfigurator.getModules();
    for (Module module : modules) {
      final ModifiableRootModel rootModel = myModulesConfigurator.getModuleEditor(module).getModifiableRootModel();
      final OrderEntry[] entries = rootModel.getOrderEntries();
      for (OrderEntry entry : entries) {
        if (condition.value(entry)) {
          result.add(module.getName());
          break;
        }
      }
    }
    return result;
  }

  @Nullable
  public ProjectJdk getSelectedJdk() {
    final Object object = getSelectedObject();
    if (object instanceof ProjectJdk){
      return myJdksTreeModel.findSdk((ProjectJdk)object);
    }
    return null;
  }

  public void setStartModuleWizard(final boolean show) {
    myModulesConfigurator.getModulesConfigurable().setStartModuleWizardOnShow(show);
  }

  public LibraryTableModifiableModelProvider getApplicationServerLibrariesProvider() {
    return new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        return myApplicationServerLibrariesProvider;
      }

      public String getTableLevel() {
        return ApplicationServersManager.APPLICATION_SERVER_MODULE_LIBRARIES;
      }
    };
  }

  public DefaultMutableTreeNode createLibraryNode(final LibraryOrderEntry libraryOrderEntry, final ModifiableRootModel model) {
    final LibraryConfigurable configurable = new LibraryConfigurable(getModifiableModelProvider(model), libraryOrderEntry.getLibrary(), trancateModuleLibraryName(libraryOrderEntry), myProject, TREE_UPDATER);
    final MyNode node = new MyNode(configurable);
    addNode(node, findNodeByObject(myProjectNode, libraryOrderEntry.getOwnerModule()));
    return node;
  }

  public void deleteLibraryNode(LibraryOrderEntry libraryOrderEntry) {
    final MyNode node = findNodeByObject(myProjectNode, libraryOrderEntry.getLibrary());
    if (node != null) {
      final TreeNode parent = node.getParent();
      node.removeFromParent();
      ((DefaultTreeModel)myTree.getModel()).reload(parent);
      final Module module = libraryOrderEntry.getOwnerModule();
      myModule2LibrariesMap.get(module).removeLibrary(libraryOrderEntry.getLibrary());
    }
  }

  public Project getProject() {
    return myProject;
  }

  @Nullable
  public Library getLibrary(final Library library) {
    final String level = library.getTable().getTableLevel();
    if (level == LibraryTablesRegistrar.PROJECT_LEVEL) {
      return findLibraryModel(library, myProjectLibrariesProvider);
    }
    else if (level == LibraryTablesRegistrar.APPLICATION_LEVEL) {
      return findLibraryModel(library, myGlobalLibrariesProvider);
    }
    return findLibraryModel(library, myApplicationServerLibrariesProvider);
  }

  @Nullable
  private static Library findLibraryModel(final Library library, LibrariesModifiableModel tableModel) {
    if (tableModel == null) return library;
    if (tableModel.wasLibraryRemoved(library)) return null;
    return tableModel.hasLibraryEditor(library) ? (Library)tableModel.getLibraryEditor(library).getModel() : library;
  }

  public void selectModuleTab(@NotNull final String moduleName, final String tabName) {
    final MyNode node = findNodeByObject(myProjectNode, ModuleManager.getInstance(myProject).findModuleByName(moduleName));
    if (node != null) {
      selectNodeInTree(node);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          ModuleConfigurable moduleConfigurable = (ModuleConfigurable)node.getConfigurable();
          moduleConfigurable.getModuleEditor().setSelectedTabName(tabName);
        }
      });
    }
  }

  public boolean addJdkNode(final ProjectJdk jdk) {
    if (!myDisposed) {
      addNode(new MyNode(new JdkConfigurable((ProjectJdkImpl)jdk, myJdksTreeModel, TREE_UPDATER)), myJdksNode);
      return true;
    }
    return false;
  }

  public void clearCaches(final Module module, final List<Library> chosen) {
    for (Library library : chosen) {
      myLibraryDependencyCache.remove(library);
    }
    myValidityCache.remove(module);
    myValidityCache.remove(module);
    myTree.repaint();
  }

  public void clearCaches(final Module module, final LibraryOrderEntry libEntry) {
    final Library library = libEntry.getLibrary();
    myLibraryDependencyCache.remove(library);
    if (library != null){
      myLibraryDependencyCache.remove(((LibraryImpl)library).getSource());
    }
    myValidityCache.remove(module);
    myTree.repaint();
  }

  public void clearCaches(final Module module, final ProjectJdk oldJdk, final ProjectJdk selectedModuleJdk) {
    myJdkDependencyCache.remove(oldJdk);
    myJdkDependencyCache.remove(selectedModuleJdk);
    myValidityCache.remove(module);
    myTree.repaint();
  }

  public Module[] getModules() {
    return myModulesConfigurator.getModuleModel().getModules();
  }

  private class MyDataProviderWrapper extends JPanel implements DataProvider {
    public MyDataProviderWrapper(final JComponent component) {
      super(new BorderLayout());
      add(component, BorderLayout.CENTER);
    }

    @Nullable
    public Object getData(@NonNls String dataId) {
      if (DataConstants.MODULE_CONTEXT_ARRAY.equals(dataId)){
        final Object o = getSelectedObject();
        if (o instanceof Module){
          return new Module[]{(Module)o};
        }
      }
      if (DataConstantsEx.MODIFIABLE_MODULE_MODEL.equals(dataId)){
        return myModulesConfigurator.getModuleModel();
      }
      return null;
    }
  }

  private class MyRemoveAction extends MyDeleteAction {
    public MyRemoveAction(final Condition<Object> availableCondition) {
      super(availableCondition);
    }

    public void actionPerformed(AnActionEvent e) {
      final TreePath[] paths = myTree.getSelectionPaths();
      final Set<TreePath> pathsToRemove = new HashSet<TreePath>();
      for (TreePath path : paths) {
        if (removeFromModel(path)){
          pathsToRemove.add(path);
        }
      }
      removePaths(pathsToRemove.toArray(new TreePath[pathsToRemove.size()]));
    }

    private boolean removeFromModel(final TreePath selectionPath) {
      final MyNode node = (MyNode)selectionPath.getLastPathComponent();
      final NamedConfigurable configurable = node.getConfigurable();
      final Object editableObject = configurable.getEditableObject();
      if (editableObject instanceof ProjectJdk) {
        myJdksTreeModel.removeJdk((ProjectJdk)editableObject);
      }
      else if (editableObject instanceof Module) {
        if (!myModulesConfigurator.deleteModule((Module)editableObject)){
          //wait for confirmation
          return false;
        }
      }
      else if (editableObject instanceof Library) {
        final Library library = (Library)editableObject;
        final LibraryTable table = library.getTable();
        if (table != null) {
          final String level = table.getTableLevel();
          if (level == LibraryTablesRegistrar.APPLICATION_LEVEL) {
            myGlobalLibrariesProvider.removeLibrary(library);
          }
          else if (level == LibraryTablesRegistrar.PROJECT_LEVEL) {
            myProjectLibrariesProvider.removeLibrary(library);
          }
          else {
            myApplicationServerLibrariesProvider.removeLibrary(library);
          }
        }
        else {
          Module module = (Module)((MyNode)node.getParent()).getConfigurable().getEditableObject();
          myModule2LibrariesMap.get(module).removeLibrary(library);
          myModulesConfigurator.getModuleEditor(module).updateOrderEntriesInEditors(); //in order to update classpath panel
        }
      }
      return true;
    }
  }

  private DefaultActionGroup createAddJdkGroup() {
    DefaultActionGroup group = new DefaultActionGroup(ProjectBundle.message("add.new.jdk.text"), true);
    myJdksTreeModel.createAddActions(group, myTree, new Consumer<ProjectJdk>() {
      public void consume(final ProjectJdk projectJdk) {
        addJdkNode(projectJdk);
        selectNodeInTree(findNodeByObject(myJdksNode, projectJdk));
      }
    });
    return group;
  }

  private DefaultActionGroup createAddLibrariesGroup() {
    DefaultActionGroup group = new DefaultActionGroup(ProjectBundle.message("add.new.library.text"), true);
    group.add(new AnAction(ProjectBundle.message("add.new.project.library.text")) {
      public void actionPerformed(AnActionEvent e) {
        final LibraryTableEditor editor = LibraryTableEditor.editLibraryTable(getProjectLibrariesProvider(), myProject);
        editor.createAddLibraryAction(true, myWholePanel).actionPerformed(null);
        Disposer.dispose(editor);
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(!myProject.isDefault());
      }
    });
    group.add(new AnAction(ProjectBundle.message("add.new.global.library.text")) {
      public void actionPerformed(AnActionEvent e) {
        final LibraryTableEditor editor = LibraryTableEditor.editLibraryTable(getGlobalLibrariesProvider(), myProject);
        editor.createAddLibraryAction(true, myWholePanel).actionPerformed(null);
        Disposer.dispose(editor);
      }
    });
    group.add(new AnAction(ProjectBundle.message("add.new.module.library.text")) {
      public void actionPerformed(AnActionEvent e) {
        Module module = (Module)getSelectedObject();
        final LibraryTableModifiableModelProvider modifiableModelProvider = getModifiableModelProvider(myModulesConfigurator.getModuleEditor(module).getModifiableRootModelProxy());
        final LibraryTableEditor editor = LibraryTableEditor.editLibraryTable(modifiableModelProvider, myProject);
        editor.createAddLibraryAction(true, myWholePanel).actionPerformed(null);
        Disposer.dispose(editor);
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(getSelectedObject() instanceof Module);
      }
    });
    return group;
  }

  private LibraryTableModifiableModelProvider getModifiableModelProvider(final ModifiableRootModel model) {
    return new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        final LibraryTable.ModifiableModel modifiableModel = model.getModuleLibraryTable().getModifiableModel();
        myModule2LibrariesMap.put(model.getModule(), new LibrariesModifiableModel(modifiableModel));
        return modifiableModel;
      }

      public String getTableLevel() {
        return LibraryTableImplUtil.MODULE_LEVEL;
      }
    };
  }

  private class MyAddAction extends ActionGroup implements ActionGroupWithPreselection{
    private AnAction [] myChildren;
    public MyAddAction() {
      super(CommonBundle.message("button.add"), true);
      final Presentation presentation = getTemplatePresentation();
      presentation.setIcon(Icons.ADD_ICON);
      registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
    }

    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      if (myChildren == null) {
        myChildren = new AnAction[3];
        myChildren[0] = createAddLibrariesGroup();
        myChildren[1] = createAddJdkGroup();
        myChildren[2] = new AnAction(ProjectBundle.message("add.new.module.text")) {
          public void actionPerformed(AnActionEvent e) {
            final Module module = myModulesConfigurator.addModule(myTree);
            if (module != null) {
              final MyNode node = new MyNode(new ModuleConfigurable(myModulesConfigurator, module, TREE_UPDATER));
              addNode(node, myProjectNode);
              selectNodeInTree(node);
            }
          }

          public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(!myProject.isDefault());
          }
        };
      }
      return myChildren;
    }

    public ActionGroup getActionGroup() {
      return this;
    }

    public int getDefaultIndex() {
      final Object selectedObject = getSelectedObject();
      if (selectedObject instanceof Library || selectedObject instanceof String) {
        return 0;
      }
      else if (selectedObject instanceof ProjectJdk || selectedObject instanceof ProjectJdksModel) {
        return 1;
      }
      else if (selectedObject instanceof Module) {
        return 2;
      }
      return 0;
    }
  }

  private class MyGroupAction extends ToggleAction {

    public MyGroupAction() {
      super("", "", COMPACT_EMPTY_MIDDLE_PACKAGES_ICON);
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      String text = ProjectBundle.message("project.roots.plain.mode.action.text.disabled");
      if (myPlainMode){
        text = ProjectBundle.message("project.roots.plain.mode.action.text.enabled");
      }
      presentation.setText(text);
      presentation.setDescription(text);

      if (myModulesConfigurator != null) {
        presentation.setVisible(myModulesConfigurator.getModuleModel().hasModuleGroups());
      }
    }

    public boolean isSelected(AnActionEvent e) {
      return myPlainMode;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myPlainMode = state;
      final ModifiableModuleModel model = myModulesConfigurator.getModuleModel();
      final Module[] modules = model.getModules();
      for (Module module : modules) {
        final String[] groupPath = model.getModuleGroupPath(module);
        updateProjectTree(new Module[]{module}, groupPath != null ? new ModuleGroup(groupPath) : null);
      }
      if (state) {
        removeModuleGroups();
      }
    }

    private void removeModuleGroups() {
      for(int i = myProjectNode.getChildCount() - 1; i >=0; i--){
        final MyNode node = (MyNode)myProjectNode.getChildAt(i);
        if (node.getConfigurable().getEditableObject() instanceof ModuleGroup){
          node.removeFromParent();
        }
      }
      ((DefaultTreeModel)myTree.getModel()).reload(myProjectNode);
    }
  }
}
