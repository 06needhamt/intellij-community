package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 2, 2004
 * Time: 1:27:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class ExistingTemplatesComponent {
  private final Tree patternTree;
  private final DefaultTreeModel patternTreeModel;
  private final DefaultMutableTreeNode userTemplatesNode;
  private final JComponent panel;
  private final DefaultListModel historyModel;
  private final JList historyList;
  private final JComponent historyPanel;
  private DialogWrapper owner;
  private final Project project;

  private ExistingTemplatesComponent(Project project) {

    this.project = project;
    final DefaultMutableTreeNode root;
    patternTreeModel = new DefaultTreeModel(
      root = new DefaultMutableTreeNode(null)
    );

    DefaultMutableTreeNode parent = null;
    String lastCategory = null;
    LinkedList<Object> nodesToExpand = new LinkedList<Object>();

    final PredefinedConfiguration[] predefined = PredefinedConfiguration.getPredefinedTemplates();
    for (final PredefinedConfiguration info : predefined) {
      final DefaultMutableTreeNode node = new DefaultMutableTreeNode(info);

      if (lastCategory == null || !lastCategory.equals(info.getCategory())) {
        if (info.getCategory().length() > 0) {
          root.add(parent = new DefaultMutableTreeNode(info.getCategory()));
          nodesToExpand.add(parent);
          lastCategory = info.getCategory();
        }
        else {
          root.add(node);
          continue;
        }
      }

      parent.add(node);
    }

    parent = new DefaultMutableTreeNode(PredefinedConfiguration.USER_DEFINED_TYPE);
    userTemplatesNode = parent;
    root.add(parent);
    nodesToExpand.add(parent);

    final ConfigurationManager configurationManager = StructuralSearchPlugin.getInstance(this.project).getConfigurationManager();
    if (configurationManager.getConfigurations() != null) {
      for (final Configuration config : configurationManager.getConfigurations()) {
        parent.add(new DefaultMutableTreeNode(config));
      }
    }

    patternTree = createTree(patternTreeModel);

    for (final Object aNodesToExpand : nodesToExpand) {
      patternTree.expandPath(
        new TreePath(new Object[]{root, aNodesToExpand})
      );
    }

    DefaultActionGroup group = new DefaultActionGroup();
    AnAction addAction = new AnAction(SSRBundle.message("create.template.action.name"), null, IconLoader.getIcon("/general/add.png")) {
      public void actionPerformed(AnActionEvent e) {
        addSelectedTreeNodeAndClose();
      }
    };

    AnAction removeAction =
      new AnAction(SSRBundle.message("remove.template.action.name"), null, IconLoader.getIcon("/general/remove.png")) {
        public void actionPerformed(AnActionEvent e) {
          Object selection = patternTree.getLastSelectedPathComponent();

          if (selection instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)selection;

            if (node.getUserObject() instanceof Configuration) {
              Configuration configuration = (Configuration)node.getUserObject();
              patternTreeModel.removeNodeFromParent(node);
              configurationManager.removeConfiguration(configuration);
            }
          }
        }
      };

    group.add(addAction);
    group.add(removeAction);

    JPanel templatesPanel = new JPanel(new BorderLayout());
    addAction.registerCustomShortcutSet(CommonShortcuts.INSERT, templatesPanel);
    removeAction.registerCustomShortcutSet(CommonShortcuts.DELETE, templatesPanel);

    templatesPanel.add(
      BorderLayout.NORTH,
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent()
    );
    templatesPanel.add(BorderLayout.CENTER, ScrollPaneFactory.createScrollPane(patternTree));

    panel = templatesPanel;
    configureSelectTemplateAction(patternTree);

    historyModel = new DefaultListModel();
    historyPanel = new JPanel(new BorderLayout());
    historyPanel.add(
      BorderLayout.NORTH,
      new JLabel(SSRBundle.message("used.templates"))
    );
    Component view = historyList = new JBList(historyModel);
    historyPanel.add(
      BorderLayout.CENTER,
      ScrollPaneFactory.createScrollPane(view)
    );

    historyList.setCellRenderer(
      new ListCellRenderer()
    );

    historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    new ListSpeedSearch(historyList);

    if (configurationManager.getHistoryConfigurations() != null) {
      for (final Configuration configuration : configurationManager.getHistoryConfigurations()) {
        historyModel.addElement(configuration);
      }

      historyList.setSelectedIndex(0);
    }

    configureSelectTemplateAction(historyList);
  }

  private void configureSelectTemplateAction(JComponent component) {
    component.addKeyListener(
      new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            owner.close(DialogWrapper.OK_EXIT_CODE);
          }
        }
      }
    );

    component.addMouseListener(
      new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            owner.close(DialogWrapper.OK_EXIT_CODE);
          }
        }
      }
    );
  }

  private void addSelectedTreeNodeAndClose() {
    addConfigurationToUserTemplates(
      Configuration.getConfigurationCreator().createConfiguration()
    );
    owner.close(DialogWrapper.OK_EXIT_CODE);
  }

  private static Tree createTree(TreeModel treeModel) {
    final Tree tree = new Tree(treeModel);

    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setDragEnabled(false);
    tree.setEditable(false);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

    tree.setCellRenderer(new TreeCellRenderer());

    new TreeSpeedSearch(
      tree,
      new Convertor<TreePath, String>() {
        public String convert(TreePath object) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)object.getLastPathComponent();
          Object displayValue = node.getUserObject();

          if (displayValue instanceof PredefinedConfiguration) {
            displayValue = ((PredefinedConfiguration)displayValue).getConfiguration().getName();
          }
          else if (displayValue instanceof Configuration) {
            displayValue = ((Configuration)displayValue).getName();
          }
          else {
            displayValue = "";
          }
          return displayValue.toString();
        }
      }
    );

    return tree;
  }

  public JTree getPatternTree() {
    return patternTree;
  }

  public JComponent getTemplatesPanel() {
    return panel;
  }

  public static ExistingTemplatesComponent getInstance(Project project) {
    StructuralSearchPlugin plugin = StructuralSearchPlugin.getInstance(project);

    if (plugin.getExistingTemplatesComponent() == null) {
      plugin.setExistingTemplatesComponent(new ExistingTemplatesComponent(project));
    }

    return plugin.getExistingTemplatesComponent();
  }

  static class ListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (value instanceof Configuration) {
        value = ((Configuration)value).getName();
      }

      Component comp = super.getListCellRendererComponent(
        list,
        value,
        index,
        isSelected,
        cellHasFocus
      );

      return comp;
    }
  }

  static class TreeCellRenderer extends DefaultTreeCellRenderer {
    TreeCellRenderer() {
      setOpenIcon(null);
      setLeafIcon(null);
      setClosedIcon(null);
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean sel,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
      Object displayValue = treeNode.getUserObject();

      if (displayValue instanceof Configuration) {
        displayValue = ((Configuration)displayValue).getName();
      }

      Component comp = super.getTreeCellRendererComponent(
        tree,
        displayValue,
        sel,
        expanded,
        leaf,
        row,
        hasFocus
      );

      return comp;
    }
  }

  void addConfigurationToHistory(Configuration configuration) {
    //configuration.setName( configuration.getName() +" "+new Date());
    historyModel.insertElementAt(configuration, 0);
    ConfigurationManager configurationManager = StructuralSearchPlugin.getInstance(project).getConfigurationManager();
    configurationManager.addHistoryConfigurationToFront(configuration);
    historyList.setSelectedIndex(0);

    if (historyModel.getSize() > 25) {
      configurationManager.removeHistoryConfiguration(
        (Configuration)historyModel.getElementAt(25)
      );
      // we add by one!
      historyModel.removeElementAt(25);
    }
  }

  private void insertNode(Configuration configuration, DefaultMutableTreeNode parent, int index) {
    DefaultMutableTreeNode node;
    patternTreeModel.insertNodeInto(
      node = new DefaultMutableTreeNode(
        configuration
      ),
      parent,
      index
    );

    TreeUtil.selectPath(
      patternTree,
      new TreePath(new Object[]{patternTreeModel.getRoot(), parent, node})
    );
  }

  void addConfigurationToUserTemplates(Configuration configuration) {
    insertNode(configuration, userTemplatesNode, userTemplatesNode.getChildCount());
    ConfigurationManager configurationManager = StructuralSearchPlugin.getInstance(project).getConfigurationManager();
    configurationManager.addConfiguration(configuration);
  }

  boolean isConfigurationFromHistory(Configuration config) {
    return historyModel.indexOf(config) != -1;
  }

  public JList getHistoryList() {
    return historyList;
  }

  public JComponent getHistoryPanel() {
    return historyPanel;
  }

  public void setOwner(DialogWrapper owner) {
    this.owner = owner;
  }
}
