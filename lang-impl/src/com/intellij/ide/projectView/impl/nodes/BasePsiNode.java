package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

@SuppressWarnings({"unchecked", "CastToIncompatibleInterface", "InstanceofIncompatibleInterface"})
public abstract class BasePsiNode <T extends PsiElement> extends ProjectViewNode<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.impl.nodes.BasePsiNode");

  protected BasePsiNode(Project project, T value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @NotNull
  public final Collection<AbstractTreeNode> getChildren() {
    T value = getValue();
    if (value == null) return new ArrayList<AbstractTreeNode>();
    boolean valid = value.isValid();
    if (!LOG.assertTrue(valid)) {
      return Collections.emptyList();
    }
    
    final Collection<AbstractTreeNode> children = getChildrenImpl();
    return children != null ? children : Collections.<AbstractTreeNode>emptyList();
  }

  protected abstract Collection<AbstractTreeNode> getChildrenImpl();

  protected boolean isMarkReadOnly() {
    final Object parentValue = getParentValue();
    return parentValue instanceof PsiDirectory || parentValue instanceof Module;
  }

  public FileStatus getFileStatus() {
    VirtualFile file = getVirtualFileForValue();
    if (file == null) {
      return FileStatus.NOT_CHANGED;
    }
    else {
      return FileStatusManager.getInstance(getProject()).getStatus(file);
    }
  }

  @Nullable
  private VirtualFile getVirtualFileForValue() {
    T value = getValue();
    if (value == null) return null;
    return PsiUtilBase.getVirtualFile(value);
  }
  // Should be called in atomic action

  protected abstract void updateImpl(PresentationData data);


  public void update(PresentationData data) {
    if (!validate()) {
      return;
    }

    T value = getValue();
    LOG.assertTrue(value.isValid());

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    Icon icon = value.getIcon(flags);
    data.setClosedIcon(icon);
    data.setOpenIcon(icon);
    data.setLocationString(myLocationString);
    data.setPresentableText(myName);
    data.setTooltip(calcTooltip());
    if (isDeprecated()) {
      data.setAttributesKey(CodeInsightColors.DEPRECATED_ATTRIBUTES);
    }
    updateImpl(data);
  }

  protected boolean isDeprecated() {
    return false;
  }

  public boolean contains(@NotNull VirtualFile file) {
    if (getValue() == null || !getValue().isValid()) return false;
    PsiFile containingFile = getValue().getContainingFile();
    if (containingFile == null) {
      return false;
    }
    final VirtualFile valueFile = containingFile.getVirtualFile();
    return valueFile != null && file.equals(valueFile);
  }

  public void navigate(boolean requestFocus) {
    if (canNavigate()) {
      ((NavigationItem)getValue()).navigate(requestFocus);
    }
  }

  public boolean canNavigate() {
    return getValue() instanceof NavigationItem && ((NavigationItem)getValue()).canNavigate();
  }

  public boolean canNavigateToSource() {
    return getValue() instanceof NavigationItem && ((NavigationItem)getValue()).canNavigateToSource();
  }

  @Nullable
  protected String calcTooltip() {
    return null;
  }

  @Override
  public boolean validate() {
    T value = getValue();
    if (value == null || !value.isValid()) {
      setValue(null);
    }

    value = getValue();

    return value != null;
  }
}
