package com.jetbrains.python.codeInsight;

import com.intellij.application.options.editor.AutoImportOptionsProvider;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;

/**
 * @author yole
 */
public class PyAutoImportOptions implements AutoImportOptionsProvider {
  private JPanel myMainPanel;
  private JRadioButton myRbFromImport;
  private JRadioButton myRbImport;

  public JComponent createComponent() {
    return myMainPanel;
  }

  public void reset() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    myRbFromImport.setSelected(settings.PREFER_FROM_IMPORT);
  }

  public boolean isModified() {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    return settings.PREFER_FROM_IMPORT != myRbFromImport.isSelected();
  }

  public void apply() throws ConfigurationException {
    final PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();
    settings.PREFER_FROM_IMPORT = myRbFromImport.isSelected();
  }

  public void disposeUIResources() {
  }
}
