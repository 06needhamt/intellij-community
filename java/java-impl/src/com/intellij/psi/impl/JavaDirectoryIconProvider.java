package com.intellij.psi.impl;

import com.intellij.ide.IconProvider;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.IconSet;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public class JavaDirectoryIconProvider extends IconProvider implements DumbAware {
  @Nullable
  public Icon getIcon(@NotNull final PsiElement element, final int flags) {
    if (element instanceof PsiDirectory) {
      final PsiDirectory psiDirectory = (PsiDirectory)element;
      final VirtualFile vFile = psiDirectory.getVirtualFile();
      final Project project = psiDirectory.getProject();
      boolean isJarRoot = vFile.getParent() == null && vFile.getFileSystem() instanceof JarFileSystem;
      boolean isContentRoot = ProjectRootsUtil.isModuleContentRoot(vFile, project);
      boolean inTestSource = ProjectRootsUtil.isInTestSource(vFile, project);
      boolean isSourceOrTestRoot = ProjectRootsUtil.isSourceOrTestRoot(vFile, project);
      Icon symbolIcon;
      final boolean isOpen = (flags & Iconable.ICON_FLAG_OPEN) != 0;
      if (isJarRoot) {
        symbolIcon = Icons.JAR_ICON;
      }
      else if (isContentRoot) {
        symbolIcon = isOpen ? Icons.CONTENT_ROOT_ICON_OPEN : Icons.CONTENT_ROOT_ICON_CLOSED;
      }
      else if (isSourceOrTestRoot) {
        symbolIcon = IconSet.getSourceRootIcon(inTestSource, isOpen);
      }
      else if (JavaDirectoryService.getInstance().getPackage(psiDirectory) != null) {
        symbolIcon = isOpen ? Icons.PACKAGE_OPEN_ICON : Icons.PACKAGE_ICON;
      }
      else {
        symbolIcon = isOpen ? Icons.DIRECTORY_OPEN_ICON : Icons.DIRECTORY_CLOSED_ICON;
      }
      boolean isExcluded = ElementPresentationUtil.isExcluded(vFile, project);
      return ElementBase.createLayeredIcon(symbolIcon, isExcluded ? ElementPresentationUtil.FLAGS_EXCLUDED : 0);
    }
    return null;
  }
}
