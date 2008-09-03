package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.pom.java.LanguageLevel;

import javax.swing.*;
import java.awt.*;

/**
 * @author ven
 */
public class LanguageLevelCombo extends ComboBox {
  public static final String USE_PROJECT_LANGUAGE_LEVEL = ProjectBundle.message("project.language.level.combo.item");

  public LanguageLevelCombo() {
    addItem(LanguageLevel.JDK_1_3);
    addItem(LanguageLevel.JDK_1_4);
    addItem(LanguageLevel.JDK_1_5);
    addItem(LanguageLevel.JDK_1_6);
    setRenderer(new MyDefaultListCellRenderer());
  }

  public void reset(Project project){
    setSelectedItem(LanguageLevelProjectExtension.getInstance(project).getLanguageLevel());
  }

  public void setSelectedItem(Object anObject) {
    if (anObject == null){
      anObject = USE_PROJECT_LANGUAGE_LEVEL;
    }
    super.setSelectedItem(anObject);
  }

  private static class MyDefaultListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof LanguageLevel) {
        setText(((LanguageLevel)value).getPresentableText());
      }
      else if (value instanceof String) {
        setText((String)value);
      }
      return this;
    }
  }
}
