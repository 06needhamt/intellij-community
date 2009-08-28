package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;

public class CompareWithLastVersion extends AbstractShowDiffAction{
  @Override
  protected VcsBackgroundableActions getKey() {
    return VcsBackgroundableActions.COMPARE_WITH;
  }

  @Override
  protected DiffActionExecutor getExecutor(final DiffProvider diffProvider, final VirtualFile selectedFile, final Project project) {
    return new DiffActionExecutor.DeletionAwareExecutor(diffProvider, selectedFile, project, getKey());
  }
}
