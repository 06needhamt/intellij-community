package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class ProjectPathMacroManager extends BasePathMacroManager {
  private ProjectImpl myProject;


  public ProjectPathMacroManager(final ProjectImpl project) {
    myProject = project;
  }

  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = super.getExpandMacroMap();
    getExpandProjectHomeReplacements(result);
    return result;
  }

  public ReplacePathToMacroMap getReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    getProjectHomeReplacements(result, false);
    result.putAll(super.getReplacePathMap());
    getProjectHomeReplacements(result, myProject.getStateStore().isSavePathsRelative());
    return result;
  }


  private void getProjectHomeReplacements(@NonNls ReplacePathToMacroMap result, final boolean savePathsRelative) {
    String projectDir = getProjectDir();
    if (projectDir == null) return;

    File f = new File(projectDir.replace('/', File.separatorChar));
    //LOG.assertTrue(f.exists());

    String macro = "$" + PathMacrosImpl.PROJECT_DIR_MACRO_NAME + "$";

    while (f != null) {
      String path = PathMacroMap.quotePath(f.getAbsolutePath());
      String s = macro;

      if (StringUtil.endsWithChar(path, '/')) s += "/";
      if (path.equals("/")) break;

      result.put("file://" + path, "file://" + s);
      result.put("file:/" + path, "file:/" + s);
      result.put("file:" + path, "file:" + s);
      result.put("jar://" + path, "jar://" + s);
      result.put("jar:/" + path, "jar:/" + s);
      result.put("jar:" + path, "jar:" + s);
      //noinspection HardCodedStringLiteral
      if (!path.equalsIgnoreCase("e:/") && !path.equalsIgnoreCase("r:/")) {
        result.put(path, s);
      }

      if (!savePathsRelative) break;
      macro += "/..";
      f = f.getParentFile();
    }
  }

  private void getExpandProjectHomeReplacements(ExpandMacroToPathMap result) {
    String projectDir = getProjectDir();
    if (projectDir == null) return;

    File f = new File(projectDir.replace('/', File.separatorChar));

    getExpandProjectHomeReplacements(result, f, "$" + PathMacrosImpl.PROJECT_DIR_MACRO_NAME + "$");
  }

  private static void getExpandProjectHomeReplacements(ExpandMacroToPathMap result, File f, String macro) {
    if (f == null) return;

    getExpandProjectHomeReplacements(result, f.getParentFile(), macro + "/..");
    String path = PathMacroMap.quotePath(f.getAbsolutePath());
    String s = macro;

    if (StringUtil.endsWithChar(path, '/')) s += "/";

    result.put(s, path);
  }

  @Nullable
    private String getProjectDir() {
    String path = myProject.getProjectFilePath();
    String projectDir = new File(path).getParent();
    if (projectDir == null) return null;
    projectDir = projectDir.replace(File.separatorChar, '/');
    if (projectDir.endsWith(":/")) {
      projectDir = projectDir.substring(0, projectDir.length() - 1);
    }
    return projectDir;
  }

}
