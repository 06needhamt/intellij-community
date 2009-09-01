package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.roots.ProjectClasspathTraversing;

public final class ClasspathMacro extends Macro {
  public String getName() {
    return "Classpath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.project.classpath");
  }

  public String expand(DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    return ProjectRootsTraversing.collectRoots(project, ProjectClasspathTraversing.FULL_CLASSPATH_RECURSIVE).getPathsString();
  }
}
