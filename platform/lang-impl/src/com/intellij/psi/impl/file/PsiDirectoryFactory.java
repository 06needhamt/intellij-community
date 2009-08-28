package com.intellij.psi.impl.file;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class PsiDirectoryFactory {
  public static PsiDirectoryFactory getInstance(Project project) {
    return ServiceManager.getService(project, PsiDirectoryFactory.class);
  }

  public abstract PsiDirectory createDirectory(VirtualFile file);

  @NotNull
  public abstract String getQualifiedName(@NotNull PsiDirectory directory, final boolean presentable);

  public abstract boolean isPackage(PsiDirectory directory);

  public abstract boolean isValidPackageName(String name);
}
