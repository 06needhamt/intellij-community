package com.intellij.ide.projectView.impl;

import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.properties.PropertiesFilesManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Alarm;
import gnu.trove.THashSet;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.*;

public class ProjectTreeBuilder extends BaseProjectTreeBuilder {
  private final ProjectViewPsiTreeChangeListener myPsiTreeChangeListener;
  private final ModuleRootListener myModuleRootListener;
  private final MyFileStatusListener myFileStatusListener;

  private final CopyPasteUtil.DefaultCopyPasteListener myCopyPasteListener;
  private final PropertiesFileListener myPropertiesFileListener;
  private final WolfTheProblemSolver.ProblemListener myProblemListener;

  public ProjectTreeBuilder(final Project project, JTree tree, DefaultTreeModel treeModel, Comparator<NodeDescriptor> comparator, ProjectAbstractTreeStructureBase treeStructure) {
    super(project, tree, treeModel, treeStructure, comparator);
    myPsiTreeChangeListener = new ProjectViewPsiTreeChangeListener(){
      protected DefaultMutableTreeNode getRootNode(){
        return myRootNode;
      }

      protected AbstractTreeUpdater getUpdater(){
        return myUpdater;
      }

      protected boolean isFlattenPackages(){
        return ((AbstractProjectTreeStructure)getTreeStructure()).isFlattenPackages();
      }
    };
    myModuleRootListener = new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }
      public void rootsChanged(ModuleRootEvent event) {
        myUpdater.addSubtreeToUpdate(myRootNode);
      }
    };
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myPsiTreeChangeListener);
    ProjectRootManager.getInstance(myProject).addModuleRootListener(myModuleRootListener);
    myFileStatusListener = new MyFileStatusListener();
    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener);
    myCopyPasteListener = new CopyPasteUtil.DefaultCopyPasteListener(myUpdater);
    CopyPasteManager.getInstance().addContentChangedListener(myCopyPasteListener);

    myPropertiesFileListener = new PropertiesFileListener();

    PropertiesFilesManager.getInstance().addPropertiesFileListener(myPropertiesFileListener);
    myProblemListener = new MyProblemListener();
    WolfTheProblemSolver.getInstance(project).addProblemListener(myProblemListener);
    initRootNode();
  }

  public final void dispose() {
    super.dispose();
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myPsiTreeChangeListener);
    ProjectRootManager.getInstance(myProject).removeModuleRootListener(myModuleRootListener);
    FileStatusManager.getInstance(myProject).removeFileStatusListener(myFileStatusListener);
    CopyPasteManager.getInstance().removeContentChangedListener(myCopyPasteListener);
    PropertiesFilesManager.getInstance().removePropertiesFileListener(myPropertiesFileListener);
    WolfTheProblemSolver.getInstance(myProject).removeProblemListener(myProblemListener);
  }

  private final class MyFileStatusListener implements FileStatusListener {
    public void fileStatusesChanged() {
      myUpdater.addSubtreeToUpdate(myRootNode);
    }

    public void fileStatusChanged(VirtualFile vFile) {
      PsiElement element;
      PsiManager psiManager = PsiManager.getInstance(myProject);
      if (vFile.isDirectory()) {
        element = psiManager.findDirectory(vFile);
      }
      else {
        element = psiManager.findFile(vFile);
      }

      final boolean fileAdded = myUpdater.addSubtreeToUpdateByElement(element);
      if (!fileAdded) {
        if (element instanceof PsiFile) {
          myUpdater.addSubtreeToUpdateByElement(((PsiFile)element).getContainingDirectory());
        } else {
          myUpdater.addSubtreeToUpdate(myRootNode);
        }
      }
    }
  }

  private class PropertiesFileListener implements PropertiesFilesManager.PropertiesFileListener {
    public void fileAdded(VirtualFile propertiesFile) {
      fileChanged(propertiesFile, null);
    }

    public void fileRemoved(VirtualFile propertiesFile) {
      fileChanged(propertiesFile, null);
    }

    public void fileChanged(VirtualFile propertiesFile, final VirtualFilePropertyEvent event) {
      if (!myProject.isDisposed()) {
        VirtualFile parent = propertiesFile.getParent();
        if (parent != null && parent.isValid()) {
          PsiDirectory dir = PsiManager.getInstance(myProject).findDirectory(parent);
          myUpdater.addSubtreeToUpdateByElement(dir);
        }
      }
    }
  }

  private class MyProblemListener implements WolfTheProblemSolver.ProblemListener {
    Alarm myUpdateProblemAlarm = new Alarm();
    Collection<VirtualFile> myFilesToRefresh = new THashSet<VirtualFile>();

    public void problemsChanged(Collection<VirtualFile> added, Collection<VirtualFile> removed) {
      Set<VirtualFile> filesToRefresh = new THashSet<VirtualFile>(added);
      filesToRefresh.addAll(removed);

      synchronized (myFilesToRefresh) {
        if (myFilesToRefresh.addAll(filesToRefresh)) {
        myUpdateProblemAlarm.cancelAllRequests();
        myUpdateProblemAlarm.addRequest(new Runnable() {
          public void run() {
            Set<VirtualFile> filesToRefresh;
            synchronized (myFilesToRefresh) {
              filesToRefresh = new THashSet<VirtualFile>(myFilesToRefresh);
            }
            updateNodesContaining(filesToRefresh, myRootNode);
            synchronized (myFilesToRefresh) {
              myFilesToRefresh.removeAll(filesToRefresh);
            }
          }
        }, 200, ModalityState.NON_MMODAL);
        }
      }
    }
  }

  private void updateNodesContaining(final Set<VirtualFile> filesToRefresh, final DefaultMutableTreeNode rootNode) {
    if (!(rootNode.getUserObject() instanceof ProjectViewNode)) return;
    ProjectViewNode node = (ProjectViewNode)rootNode.getUserObject();
    Set<VirtualFile> containingFiles = null;
    for (VirtualFile virtualFile : filesToRefresh) {
      if (node.contains(virtualFile)) {
        if (containingFiles == null) containingFiles = new THashSet<VirtualFile>();
        containingFiles.add(virtualFile);
      }
    }
    if (containingFiles != null) {
      updateNode(rootNode);
      Enumeration children = rootNode.children();
      while (children.hasMoreElements()) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode)children.nextElement();
        updateNodesContaining(containingFiles, child);
      }
    }
  }
}