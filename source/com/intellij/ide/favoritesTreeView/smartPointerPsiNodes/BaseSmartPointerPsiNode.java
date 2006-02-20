package com.intellij.ide.favoritesTreeView.smartPointerPsiNodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

public abstract class BaseSmartPointerPsiNode <Type extends SmartPsiElementPointer> extends ProjectViewNode<Type> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.nodes.BasePsiNode");

  protected BaseSmartPointerPsiNode(Project project, Type value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @NotNull
  public final Collection<AbstractTreeNode> getChildren() {
    PsiElement value = getPsiElement();
    if (value == null) return new ArrayList<AbstractTreeNode>();
    boolean valid = value.isValid();
    if (!LOG.assertTrue(valid)) {
      return null;
    }
    return getChildrenImpl();
  }

  protected abstract Collection<AbstractTreeNode> getChildrenImpl();

  protected boolean isMarkReadOnly() {
    final Object parentValue = getParentValue();
    return parentValue instanceof PsiDirectory || parentValue instanceof PackageElement;
  }

  public FileStatus getFileStatus() {
    VirtualFile file = getVirtualFileForValue();
    if (file != null) {
      return FileStatusManager.getInstance(getProject()).getStatus(file);
    } else {
      return FileStatus.NOT_CHANGED;
    }
  }

  private VirtualFile getVirtualFileForValue() {
    PsiElement value = getPsiElement();
    if (value == null) return null;
    if (value instanceof PsiDirectory) {
      return ((PsiDirectory)value).getVirtualFile();
    }
    else {
      PsiFile containingFile = value.getContainingFile();
      if (containingFile == null) {
        return null;
      }
      else {
        return containingFile.getVirtualFile();
      }
    }
  }
  // Should be called in atomic action

  protected abstract void updateImpl(PresentationData data);


  public void update(PresentationData data) {
    final PsiElement value = getPsiElement();
    if (value == null || !value.isValid()) {
      setValue(null);
    }
    if (getPsiElement() == null) return;

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    LOG.assertTrue(value.isValid());

    Icon icon = value.getIcon(flags);
    data.setClosedIcon(icon);
    data.setOpenIcon(icon);
    data.setLocationString(myLocationString);
    data.setPresentableText(myName);
    if (isDeprecated()) {
      data.setAttributesKey(CodeInsightColors.DEPRECATED_ATTRIBUTES);
    }
    updateImpl(data);
  }

  private boolean isDeprecated() {
    final PsiElement element = getPsiElement();
    if (element == null || !element.isValid()) return false;
    if (!(element instanceof PsiDocCommentOwner)) return false;
    return ((PsiDocCommentOwner)element).isDeprecated();

  }

  public boolean contains(VirtualFile file) {
    if (getPsiElement() == null) return false;
    PsiFile containingFile = getPsiElement().getContainingFile();
    return containingFile.getVirtualFile().equals(file);
  }

  public void navigate(boolean requestFocus) {
    if (canNavigate()) {
      ((NavigationItem)getPsiElement()).navigate(requestFocus);
    }
  }

  public boolean canNavigate() {
    return getPsiElement() instanceof NavigationItem && ((NavigationItem)getPsiElement()).canNavigate();
  }

  public boolean canNavigateToSource() {
    return getPsiElement() instanceof NavigationItem && ((NavigationItem)getPsiElement()).canNavigateToSource();
  }

  protected PsiElement getPsiElement(){
    final Type value = getValue();
    return value == null ? null : value.getElement();
  }
}
