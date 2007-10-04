/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 16-Aug-2006
 * Time: 16:56:21
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.ui.configuration.projectRoot.JdkConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Conditions;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.Consumer;
import com.intellij.util.Icons;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;

@State(
  name = "ProjectJDKsConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class ProjectJdksConfigurable extends MasterDetailsComponent implements Configurable.Assistant {

  private ProjectJdksModel myProjectJdksModel;
  private Project myProject;
  @NonNls 
  private static final String SPLITTER_PROPORTION = "project.jdk.splitter";

  public ProjectJdksConfigurable(Project project) {
    super();
    myProject = project;
    myProjectJdksModel = ProjectJdksModel.getInstance(project);

    initTree();
  }

  protected void initTree() {
    super.initTree();
    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    }, true);

    myTree.setRootVisible(false);
  }

  public void reset() {
    super.reset();

    myProjectJdksModel.reset(myProject);

    myRoot.removeAllChildren();
    final Map<ProjectJdk,ProjectJdk> sdks = myProjectJdksModel.getProjectJdks();
    for (ProjectJdk sdk : sdks.keySet()) {
      final JdkConfigurable configurable = new JdkConfigurable((ProjectJdkImpl)sdks.get(sdk), myProjectJdksModel, TREE_UPDATER, myHistory);
      addNode(new MyNode(configurable), myRoot);
    }
    selectJdk(myProjectJdksModel.getProjectJdk()); //restore selection
    final String value = PropertiesComponent.getInstance().getValue(SPLITTER_PROPORTION);
    if (value != null) {
      try {
        final Splitter splitter = extractSplitter();
        if (splitter != null) {
          (splitter).setProportion(Float.parseFloat(value));
        }
      }
      catch (NumberFormatException e) {
        //do not set proportion
      }
    }
  }

  @Nullable
  private Splitter extractSplitter() {
    final Component[] components = myWholePanel.getComponents();
    if (components.length == 1 && components[0] instanceof Splitter) {
      return (Splitter)components[0];
    }
    return null;
  }

  public void apply() throws ConfigurationException {
    super.apply();
    boolean modifiedJdks = false;
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final NamedConfigurable configurable = ((MyNode)myRoot.getChildAt(i)).getConfigurable();
      if (configurable.isModified()) {
        configurable.apply();
        modifiedJdks = true;
      }
    }

    if (myProjectJdksModel.isModified() || modifiedJdks) myProjectJdksModel.apply(this);
    myProjectJdksModel.setProjectJdk(getSelectedJdk());
 }


  public boolean isModified() {
    return super.isModified() || myProjectJdksModel.isModified();
  }


  public void disposeUIResources() {
    final Splitter splitter = extractSplitter();
    if (splitter != null) {
      PropertiesComponent.getInstance().setValue(SPLITTER_PROPORTION, String.valueOf(splitter.getProportion()));
    }
    myProjectJdksModel.disposeUIResources();
    super.disposeUIResources();
  }

  @Nullable
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    final ArrayList<AnAction> actions = new ArrayList<AnAction>();
    DefaultActionGroup group = new DefaultActionGroup(ProjectBundle.message("add.new.jdk.text"), true);
    group.getTemplatePresentation().setIcon(Icons.ADD_ICON);
    myProjectJdksModel.createAddActions(group, myTree, new Consumer<ProjectJdk>() {
      public void consume(final ProjectJdk projectJdk) {
        addNode(new MyNode(new JdkConfigurable(((ProjectJdkImpl)projectJdk), myProjectJdksModel, TREE_UPDATER, myHistory), false), myRoot);
        selectNodeInTree(findNodeByObject(myRoot, projectJdk));
      }
    });
    actions.add(new MyActionGroupWrapper(group));
    actions.add(new MyDeleteAction(Conditions.alwaysTrue()));
    return actions;
  }

  protected void processRemovedItems() {
    final Set<ProjectJdk> jdks = new HashSet<ProjectJdk>();
    for(int i = 0; i < myRoot.getChildCount(); i++){
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myRoot.getChildAt(i);
      final NamedConfigurable namedConfigurable = (NamedConfigurable)node.getUserObject();
      jdks.add(((JdkConfigurable)namedConfigurable).getEditableObject());
    }
    final Map<ProjectJdk, ProjectJdk> sdks = new HashMap<ProjectJdk, ProjectJdk>(myProjectJdksModel.getProjectJdks());
    for (ProjectJdk sdk : sdks.values()) {
      if (!jdks.contains(sdk)) {
        myProjectJdksModel.removeJdk(sdk);
      }
    }
  }

  protected boolean wasObjectStored(Object editableObject) {
    //noinspection RedundantCast
    return myProjectJdksModel.getProjectJdks().containsKey((ProjectJdk)editableObject);
  }

  @Nullable
  public ProjectJdk getSelectedJdk() {
    return (ProjectJdk)getSelectedObject();
  }

  public void selectJdk(final ProjectJdk projectJdk) {
    selectNodeInTree(projectJdk);
  }

  @Nullable
  public String getDisplayName() {
    return null;
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select a JDK to view or edit its details here";
  }
}