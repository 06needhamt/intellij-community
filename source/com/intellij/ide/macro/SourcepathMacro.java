
package com.intellij.ide.macro;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootsTraversing;

public final class SourcepathMacro extends Macro {
  public String getName() {
    return "Sourcepath";
  }

  public String getDescription() {
    return IdeBundle.message("macro.project.sourcepath");
  }

  public String expand(DataContext dataContext) {
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;
    return ProjectRootsTraversing.collectRoots(project, ProjectRootsTraversing.PROJECT_SOURCES).getPathsString();
  }
}
