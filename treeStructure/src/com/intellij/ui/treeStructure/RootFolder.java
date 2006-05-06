/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.treeStructure;

import com.intellij.openapi.project.Project;

/**
 * @author kir
 */
public class RootFolder extends FolderNode {

  public RootFolder(Project aProject) {
    super(aProject, null);
  }

  public boolean isAutoExpandNode() {
    return true;
  }
}
