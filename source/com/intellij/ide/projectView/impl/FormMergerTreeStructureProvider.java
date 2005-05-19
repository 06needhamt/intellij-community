package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.Form;
import com.intellij.ide.projectView.impl.nodes.FormNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public class FormMergerTreeStructureProvider implements TreeStructureProvider, ProjectComponent{
  private final Project myProject;

  public FormMergerTreeStructureProvider(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    if (parent.getValue() instanceof Form) return children;

    // Optimization. Check if there are any forms at all.
    boolean formsFound = true;
    for (AbstractTreeNode node : children) {
      if (node.getValue() instanceof PsiFile) {
        PsiFile file = (PsiFile)node.getValue();
        if (file.getFileType() == StdFileTypes.GUI_DESIGNER_FORM) {
          break;
        }
      }
    }

    if (!formsFound) return children;

    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    ProjectViewNode[] copy = children.toArray(new ProjectViewNode[children.size()]);
    for (ProjectViewNode element : copy) {
      if (element.getValue() instanceof PsiClass) {
        PsiClass aClass = ((PsiClass)element.getValue());
        PsiFile[] forms = aClass.getManager().getSearchHelper().findFormsBoundToClass(aClass.getQualifiedName());
        Collection<AbstractTreeNode> formNodes = findFormsIn(children, forms);
        if (formNodes.size() > 0) {
          Collection<PsiFile> formFiles = convertToFiles(formNodes);
          Collection<AbstractTreeNode> subNodes = new ArrayList<AbstractTreeNode>(formNodes);
          subNodes.add(element);
          result.add(new FormNode(myProject, new Form(aClass, formFiles), settings, subNodes));
          children.remove(element);
          children.removeAll(formNodes);
        }
      }
    }
    result.addAll(children);
    return result;
  }

  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }

  private Collection<PsiFile> convertToFiles(Collection<AbstractTreeNode> formNodes) {
    ArrayList<PsiFile> psiFiles = new ArrayList<PsiFile>();
    for (AbstractTreeNode treeNode : formNodes) {
      psiFiles.add((PsiFile)treeNode.getValue());
    }
    return psiFiles;
  }

  private Collection<AbstractTreeNode> findFormsIn(Collection<AbstractTreeNode> children, PsiFile[] forms) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    HashSet<PsiFile> psiFiles = new HashSet<PsiFile>(Arrays.asList(forms));
    for (final AbstractTreeNode aChildren : children) {
      ProjectViewNode treeNode = (ProjectViewNode)aChildren;
      if (psiFiles.contains(treeNode.getValue())) result.add(treeNode);
    }
    return result;
  }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void disposeComponent() {
  }

  public String getComponentName() {
    return "FormNodesProvider";
  }

  public void initComponent() {
  }
}

