// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.diagnostic.logging.LogsFragment;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.ui.*;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.RawCommandLineEditor;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.execution.ui.CommandLinePanel.setMinimumWidth;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class JavaApplicationSettingsEditor extends RunConfigurationFragmentedEditor<ApplicationConfiguration> {
  private final Project myProject;

  public JavaApplicationSettingsEditor(ApplicationConfiguration configuration) {
    super(configuration, JavaRunConfigurationExtensionManager.getInstance());
    myProject = configuration.getProject();
  }

  @Override
  protected List<SettingsEditorFragment<ApplicationConfiguration, ?>> createRunFragments() {
    List<SettingsEditorFragment<ApplicationConfiguration, ?>> fragments = new ArrayList<>();
    BeforeRunComponent beforeRunComponent = new BeforeRunComponent();
    fragments.add(BeforeRunFragment.createComponent(beforeRunComponent));
    fragments.addAll(BeforeRunFragment.createGroup());

    ModuleClasspathCombo.Item item = new ModuleClasspathCombo.Item(ExecutionBundle.message("application.configuration.include.provided.scope"));
    SettingsEditorFragment<ApplicationConfiguration, ModuleClasspathCombo>
      moduleClasspath = CommonJavaFragments.moduleClasspath(item, configuration -> configuration.isProvidedScopeIncluded(),
                                                            (configuration, value) -> configuration.setIncludeProvidedScope(value));
    ModuleClasspathCombo classpathCombo = moduleClasspath.component();
    Computable<Boolean> hasModule = () -> classpathCombo.getSelectedModule() != null;

    fragments.add(CommonTags.parallelRun());
    fragments.add(CommonParameterFragments.createRedirectFragment(hasModule));

    CommonParameterFragments<ApplicationConfiguration> commonParameterFragments = new CommonParameterFragments<>(myProject, hasModule);
    fragments.addAll(commonParameterFragments.getFragments());
    fragments.add(CommonJavaFragments.createBuildBeforeRun(beforeRunComponent));
    fragments.add(CommonJavaFragments.createEnvParameters());

    JrePathEditor jrePathEditor = new JrePathEditor();
    setMinimumWidth(jrePathEditor, 100);
    jrePathEditor.getLabel().setVisible(false);
    jrePathEditor.setDefaultJreSelector(DefaultJreSelector.projectSdk(myProject));

    RawCommandLineEditor vmOptions = new RawCommandLineEditor() {
      @Override
      public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
      }
    };
    setMinimumWidth(vmOptions, 400);
    vmOptions.getEditorField().getEmptyText().setText(ExecutionBundle.message("run.configuration.java.vm.parameters.empty.text"));
    MacrosDialog.addMacroSupport(vmOptions.getEditorField(), MacrosDialog.Filters.ALL, hasModule);
    String group = ExecutionBundle.message("group.java.options");
    fragments.add(new SettingsEditorFragment<>("jrePath", null, null, jrePathEditor, 5,
                                               (configuration, editor) -> editor
                                                 .setPathOrName(configuration.getAlternativeJrePath(),
                                                                configuration.isAlternativeJrePathEnabled()),
                                               (configuration, editor) -> {
                                                 configuration.setAlternativeJrePath(editor.getJrePathOrName());
                                                 configuration.setAlternativeJrePathEnabled(editor.isAlternativeJreSelected());
                                               },
                                               configuration -> true));
    EditorTextField mainClass = ClassEditorField.createClassField(myProject);
    setMinimumWidth(mainClass, 300);
    fragments.add(new SettingsEditorFragment<>("mainClass", null, null, mainClass, 20,
                                               (configuration, component) -> component.setText(configuration.getMainClassName()),
                                               (configuration, component) -> configuration.setMainClassName(component.getText()),
                                               configuration -> true));
    fragments.add(new SettingsEditorFragment<>("vmParameters", ExecutionBundle.message("run.configuration.java.vm.parameters.name"), group, vmOptions, 15,
                                               (configuration, component) -> component.setText(configuration.getVMParameters()),
                                               (configuration, component) -> configuration.setVMParameters(component.getText()),
                                               configuration -> isNotEmpty(configuration.getVMParameters())));
    fragments.add(moduleClasspath);

    ShortenCommandLineModeCombo combo = new ShortenCommandLineModeCombo(myProject, jrePathEditor, () -> classpathCombo.getSelectedModule(),
                                                                        listener -> classpathCombo.addActionListener(listener));
    fragments.add(new SettingsEditorFragment<>("shorten.command.line",
                                               ExecutionBundle.message("application.configuration.shorten.command.line"),
                                               group, LabeledComponent.create(combo, ExecutionBundle.message("application.configuration.shorten.command.line.label"), BorderLayout.WEST),
                                               (configuration, c) -> c.getComponent().setItem(configuration.getShortenCommandLine()),
                                               (configuration, c) -> configuration.setShortenCommandLine(c.getComponent().getSelectedItem()),
                                               configuration -> configuration.getShortenCommandLine() != null));
    fragments.add(SettingsEditorFragment.createTag("formSnapshots", ExecutionBundle.message("show.swing.inspector.name"), group,
                                                   configuration -> configuration.isSwingInspectorEnabled(),
                                                   (configuration, enabled) -> configuration.setSwingInspectorEnabled(enabled)));

    fragments.add(new LogsFragment<>());
    return fragments;
  }
}
