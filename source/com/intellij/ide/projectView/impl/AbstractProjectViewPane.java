/**
 * @author cdr
 */
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.AutoScrollFromSourceHandler;
import com.intellij.ui.AutoScrollToSourceHandler;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public abstract class AbstractProjectViewPane implements JDOMExternalizable, DataProvider {
  protected final Project myProject;
  protected Runnable myTreeChangeListener;
  protected ProjectViewTree myTree;
  protected AbstractTreeStructure myTreeStructure;
  protected BaseProjectTreeBuilder myTreeBuilder;
  private final TreeState myReadTreeState = new TreeState();

  protected final void fireTreeChangeListener() {
    if (myTreeChangeListener != null) myTreeChangeListener.run();
  }

  public final void setTreeChangeListener(Runnable listener) {
    myTreeChangeListener = listener;
  }
  public final void removeTreeChangeListener() {
    myTreeChangeListener = null;
  }

  protected AbstractProjectViewPane(Project project) {
    myProject = project;
  }

  public abstract String getTitle();
  public abstract Icon getIcon();
  public abstract String getId();
  public abstract JComponent getComponent();
  public JComponent getComponentToFocus() {
    return myTree;
  }
  public abstract void expand(final Object[] path);
  public abstract void expand(final Object element);
  public abstract void dispose();
  public abstract void updateFromRoot(boolean restoreExpandedPaths);
  public abstract void select(Object element, VirtualFile file, boolean requestFocus);

  public abstract TreePath[] getSelectionPaths();
  public abstract void installAutoScrollToSourceHandler(AutoScrollToSourceHandler autoScrollToSourceHandler);

  public static void installAutoScrollFromSourceHandler(AutoScrollFromSourceHandler autoScrollFromSourceHandler) {
    autoScrollFromSourceHandler.install();
  }

  public void addToolbarActions(DefaultActionGroup actionGroup) {
  }

  public abstract void updateTreePopupHandler();

  private List<AbstractTreeNode> getSelectedNodes(){
    final ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();

    TreePath[] paths = getSelectionPaths();
    if (paths == null) return null;
    for (int i = 0; i < paths.length; i++) {
      TreePath path = paths[i];
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
        Object userObject = node.getUserObject();
        if (userObject instanceof AbstractTreeNode) {
          result.add((AbstractTreeNode)userObject);
        }
      }
    }
    return result;
  }

  public Object getData(String dataId) {
    if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)){
      TreePath[] paths = getSelectionPaths();
      if (paths == null) return null;
      final ArrayList<Navigatable> navigatables = new ArrayList<Navigatable>();
      for (int i = 0; i < paths.length; i++) {
        TreePath path = paths[i];
        Object lastPathComponent = path.getLastPathComponent();
        if (lastPathComponent instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
          Object userObject = node.getUserObject();
          if (userObject instanceof AbstractTreeNode) {
            navigatables.add((AbstractTreeNode)userObject);
          }
        }
      }
      if (navigatables.isEmpty()) {
        return null;
      } else {
        return navigatables.toArray(new Navigatable[navigatables.size()]);
      }
    }
    if (myTreeStructure instanceof AbstractTreeStructureBase){
      final List<TreeStructureProvider> providers = ((AbstractTreeStructureBase)myTreeStructure).getProviders();
      if (providers != null) {
        final List<AbstractTreeNode> selectedNodes = getSelectedNodes();
        for (Iterator<TreeStructureProvider> iterator = providers.iterator(); iterator.hasNext();) {
          TreeStructureProvider treeStructureProvider = iterator.next();
          final Object fromProvider = treeStructureProvider.getData(selectedNodes, dataId);
          if (fromProvider != null) {
            return fromProvider;
          }
        }
      }
    }
    return null;
  }

  // used for sorting tabs in the tabbed pane
  public int getWeight() {
    return 0;
  }

  public final TreePath getSelectedPath() {
    final TreePath[] paths = getSelectionPaths();
    if (paths != null && paths.length == 1) return paths[0];
    return null;
  }

  public final NodeDescriptor getSelectedDescriptor() {
    final DefaultMutableTreeNode node = getSelectedNode();
    if (node == null) return null;
    Object userObject = node.getUserObject();
    if (userObject instanceof NodeDescriptor) {
      return (NodeDescriptor)userObject;
    }
    return null;
  }

  public final DefaultMutableTreeNode getSelectedNode() {
    TreePath path = getSelectedPath();
    if (path == null) {
      return null;
    }
    Object lastPathComponent = path.getLastPathComponent();
    if (!(lastPathComponent instanceof DefaultMutableTreeNode)) {
      return null;
    }
    return (DefaultMutableTreeNode)lastPathComponent;
  }

  public final Object getSelectedElement() {
    final Object[] elements = getSelectedElements();
    if (elements.length == 1) return elements[0];
    return null;
  }
  public final PsiElement[] getSelectedPSIElements() {
    final Object[] elements = getSelectedElements();
    List<PsiElement> psiElements = new ArrayList<PsiElement>();
    for (int i = 0; i < elements.length; i++) {
      Object element = elements[i];
      if (element instanceof PsiElement) {
        psiElements.add((PsiElement)element);
      } else if (element instanceof PackageElement) {
        PsiPackage aPackage = ((PackageElement)element).getPackage();
        if (aPackage != null) {
          psiElements.add(aPackage);
        }
      }
    }
    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }
  public final Object[] getSelectedElements() {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return PsiElement.EMPTY_ARRAY;
    ArrayList<Object> list = new ArrayList<Object>(paths.length);
    for (int i = 0; i < paths.length; i++) {
      TreePath path = paths[i];
      Object lastPathComponent = path.getLastPathComponent();
      if (lastPathComponent instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
        Object userObject = node.getUserObject();
        if (userObject instanceof AbstractTreeNode) {
          AbstractTreeNode descriptor = (AbstractTreeNode)userObject;
          Object element = descriptor.getValue();
          list.add(element);
        } else if (userObject instanceof NodeDescriptor) {
          NodeDescriptor descriptor = (NodeDescriptor)userObject;
          Object element = descriptor.getElement();
          list.add(element);
        }

      }
    }
    return list.toArray(new Object[list.size()]);
  }
  public BaseProjectTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  public void readExternal(Element element) throws InvalidDataException {
    myReadTreeState.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (myTree != null) {
      TreeState.createOn(myTree).writeExternal(element);
    }
  }

  public final void restoreState(){
    myReadTreeState.applyTo(myTree);
  }
}
