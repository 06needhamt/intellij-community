package com.intellij.util.xml.tree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xml.DomChangeAdapter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

public class DomModelTreeView extends Wrapper implements DataProvider, Disposable {

  @NonNls public static String DOM_MODEL_TREE_VIEW_KEY = "DOM_MODEL_TREE_VIEW_KEY";
  @NonNls public static String DOM_MODEL_TREE_VIEW_POPUP = "DOM_MODEL_TREE_VIEW_POPUP";

  private final SimpleTree myTree;
  private final LazySimpleTreeBuilder myBuilder;
  private DomManager myDomManager;
  @Nullable private DomElement myRootElement;

  public DomModelTreeView(@NotNull DomElement rootElement) {
    this(rootElement, rootElement.getManager(), new DomModelTreeStructure(rootElement));
  }

  protected DomModelTreeView(DomElement rootElement, DomManager manager, SimpleTreeStructure treeStructure) {
    myDomManager = manager;
    myRootElement = rootElement;
    myTree = new SimpleTree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myTree.setRootVisible(isRootVisible());
    myTree.setShowsRootHandles(true);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    ToolTipManager.sharedInstance().registerComponent(myTree);
    TreeUtil.installActions(myTree);

    myBuilder = new LazySimpleTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), treeStructure, WeightBasedComparator.INSTANCE);
    Disposer.register(this, myBuilder);

    myBuilder.setNodeDescriptorComparator(null);

    myBuilder.initRoot();

    add(myTree, BorderLayout.CENTER);

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      public void treeExpanded(TreeExpansionEvent event) {
        final SimpleNode simpleNode = myTree.getNodeFor(event.getPath());

        if (simpleNode instanceof AbstractDomElementNode) {
          ((AbstractDomElementNode)simpleNode).setExpanded(true);
        }
      }

      public void treeCollapsed(TreeExpansionEvent event) {
        final SimpleNode simpleNode = myTree.getNodeFor(event.getPath());

        if (simpleNode instanceof AbstractDomElementNode) {
          ((AbstractDomElementNode)simpleNode).setExpanded(false);
          simpleNode.update();
        }
      }
    });

    myDomManager.addDomEventListener(new DomChangeAdapter() {
      protected void elementChanged(DomElement element) {
        if (element.isValid()) {
          queueUpdate(element.getRoot().getFile().getVirtualFile());
        }
      }
    }, this);
    WolfTheProblemSolver.getInstance(myDomManager.getProject()).addProblemListener(new WolfTheProblemSolver.ProblemListener() {
      public void problemsAppeared(VirtualFile file) {
        queueUpdate(file);
      }

      public void problemsChanged(VirtualFile file) {
        queueUpdate(file);
      }

      public void problemsDisappeared(VirtualFile file) {
        queueUpdate(file);
      }

    }, this);

    myTree.setPopupGroup(getPopupActions(), DOM_MODEL_TREE_VIEW_POPUP);
  }

  protected boolean isRightFile(final VirtualFile file) {
    return myRootElement.isValid() && file.equals(myRootElement.getRoot().getFile().getVirtualFile());
  }

  private void queueUpdate(final VirtualFile file) {
    if (file == null) return;
    if (getProject().isDisposed()) return;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (getProject().isDisposed()) return;
        if (file.isValid() && isRightFile(file)) {
          myBuilder.queueUpdate();
        }
      }
    });
  }

  protected boolean isRootVisible() {
    return true;
  }

  public final void updateTree() {
    myBuilder.updateFromRoot();
  }

  public DomElement getRootElement() {
    return myRootElement;
  }

  protected final Project getProject() {
    return myDomManager.getProject();
  }

  public LazySimpleTreeBuilder getBuilder() {
    return myBuilder;
  }

  public void dispose() {
  }

  public SimpleTree getTree() {
    return myTree;
  }

  protected ActionGroup getPopupActions() {
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(ActionManager.getInstance().getAction("DomElementsTreeView.TreePopup"));
    group.addSeparator();

    group.add(new ExpandAllAction(myTree));
    group.add(new CollapseAllAction(myTree));

    return group;
  }

  @Nullable
  public Object getData(String dataId) {
    if (DOM_MODEL_TREE_VIEW_KEY.equals(dataId)) {
      return this;
    }
    final SimpleNode simpleNode = getTree().getSelectedNode();
    if (simpleNode instanceof AbstractDomElementNode) {
      final DomElement domElement = ((AbstractDomElementNode)simpleNode).getDomElement();
      if (domElement != null) {
        if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)) {
          final XmlElement tag = domElement.getXmlElement();
          if (tag instanceof Navigatable) {
            return new Navigatable[] { (Navigatable)tag };
          }
        }
      }
    }
    return null;
  }

  public void setSelectedDomElement(final DomElement domElement) {
    if (domElement == null) return;

    final List<SimpleNode> parentsNodes = getNodesFor(domElement);


    if (parentsNodes.size() > 0) {
      final SimpleNode parent = parentsNodes.get(parentsNodes.size() - 1);
      getTree().setSelectedNode(getBuilder(), parent, true);
    }
  }

  private List<SimpleNode> getNodesFor(final DomElement domElement) {
    final List<SimpleNode> parentsNodes = new ArrayList<SimpleNode>();

    myBuilder.setWaiting(false);
    myTree.accept(myBuilder, new SimpleNodeVisitor() {
      public boolean accept(SimpleNode simpleNode) {
        if (simpleNode instanceof AbstractDomElementNode) {
          final DomElement nodeElement = ((AbstractDomElementNode)simpleNode).getDomElement();
          if (isParent(nodeElement, domElement)) {
            parentsNodes.add(simpleNode);
          }
          if (nodeElement != null && nodeElement.equals(domElement)) {
            return true;
          }
        }
        return false;
      }
    });

    return parentsNodes;
  }

  private static boolean isParent(final DomElement potentialParent, final DomElement domElement) {
    DomElement currParent = domElement;
    while (currParent != null) {
      if (currParent.equals(potentialParent)) return true;

      currParent = currParent.getParent();
    }
    return false;
  }

}

