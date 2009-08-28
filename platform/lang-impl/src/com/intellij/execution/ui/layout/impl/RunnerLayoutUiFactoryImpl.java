package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class RunnerLayoutUiFactoryImpl extends RunnerLayoutUi.Factory {
  private final Project myProject;

  public RunnerLayoutUiFactoryImpl(Project project) {
    myProject = project;
  }

  public RunnerLayoutUi create(@NotNull final String runnerType, @NotNull final String runnerTitle, @NotNull final String sessionName, @NotNull final Disposable parent) {
    final RunnerLayoutUiImpl ui = new RunnerLayoutUiImpl(myProject, parent, runnerType, runnerTitle, sessionName);
    return ui;
  }
}
