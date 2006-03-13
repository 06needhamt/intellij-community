package com.intellij.execution.application;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.junit2.configuration.ClassBrowser;
import com.intellij.execution.junit2.configuration.CommonJavaParameters;
import com.intellij.execution.junit2.configuration.ConfigurationModuleSelector;
import com.intellij.execution.ui.AlternativeJREPanel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;

public class ApplicationConfigurable2 extends SettingsEditor<ApplicationConfiguration>{
  private CommonJavaParameters myCommonJavaParameters;
  private LabeledComponent<TextFieldWithBrowseButton> myMainClass;
  private LabeledComponent<JComboBox> myModule;
  private JPanel myWholePanel;

  private final ConfigurationModuleSelector myModuleSelector;
  private final LogConfigurationPanel myLogConfigurations;
  private JPanel myLogsPanel;
  private AlternativeJREPanel myAlternativeJREPanel;
  private JCheckBox myShowSwingInspectorCheckbox;

  public ApplicationConfigurable2(final Project project) {
    myModuleSelector = new ConfigurationModuleSelector(project, myModule.getComponent());
    myLogConfigurations = new LogConfigurationPanel();
    myLogsPanel.setLayout(new BorderLayout());
    myLogsPanel.add(myLogConfigurations.getLoggerComponent(), BorderLayout.CENTER);
    ClassBrowser.createApplicationClassBrowser(project, myModuleSelector).setField(getMainClassField());
  }

  public void applyEditorTo(final ApplicationConfiguration configuration) throws ConfigurationException {
    myCommonJavaParameters.applyTo(configuration);
    myModuleSelector.applyTo(configuration);
    myLogConfigurations.applyTo(configuration);
    configuration.MAIN_CLASS_NAME = getMainClassField().getText();
    configuration.ALTERNATIVE_JRE_PATH = myAlternativeJREPanel.getPath();
    configuration.ALTERNATIVE_JRE_PATH_ENABLED = myAlternativeJREPanel.isPathEnabled();
    configuration.ENABLE_SWING_INSPECTOR = myShowSwingInspectorCheckbox.isSelected();
  }

  public void resetEditorFrom(final ApplicationConfiguration configuration) {
    myCommonJavaParameters.reset(configuration);
    myModuleSelector.reset(configuration);
    myLogConfigurations.resetFrom(configuration);
    getMainClassField().setText(configuration.MAIN_CLASS_NAME);
    myAlternativeJREPanel.init(configuration.ALTERNATIVE_JRE_PATH, configuration.ALTERNATIVE_JRE_PATH_ENABLED);
    myShowSwingInspectorCheckbox.setSelected(configuration.ENABLE_SWING_INSPECTOR);
  }

  public TextFieldWithBrowseButton getMainClassField() {
    return myMainClass.getComponent();
  }

  public CommonJavaParameters getCommonJavaParameters() {
    return myCommonJavaParameters;
  }

  @NotNull
  public JComponent createEditor() {
    return myWholePanel;
  }

  public void disposeEditor() {
  }

  public String getHelpTopic() {
    return null;
  }
}
