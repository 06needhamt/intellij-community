/*
 * @author max
 */
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ContentBasedClassFileProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

public class ClassFileDecompiler implements BinaryFileDecompiler {
  @NotNull
  public CharSequence decompile(final VirtualFile file) {
    assert file.getFileType() == StdFileTypes.CLASS;

    final Project project;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      project = ((ProjectManagerEx)ProjectManager.getInstance()).getCurrentTestProject();
      assert project != null;
    }
    else {
      final Project[] projects = ProjectManager.getInstance().getOpenProjects();
      if (projects.length == 0) return "";
      project = projects[0];
    }

    final ContentBasedClassFileProcessor[] processors = Extensions.getExtensions(ContentBasedClassFileProcessor.EP_NAME);
    for (ContentBasedClassFileProcessor processor : processors) {
      if (processor.isApplicable(project, file)) {
        return processor.obtainFileText(project, file);
      }
    }

    return ClsFileImpl.decompile(PsiManager.getInstance(project), file);
  }
}