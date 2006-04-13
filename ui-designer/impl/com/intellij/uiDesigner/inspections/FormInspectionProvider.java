package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.codeInspection.InspectionToolProvider;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public class FormInspectionProvider implements ApplicationComponent, InspectionToolProvider {
  @NonNls public String getComponentName() {
    return "FormInspectionProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public Class[] getInspectionClasses() {
    return new Class[] {
      DuplicateMnemonicInspection.class,
      MissingMnemonicInspection.class,
      NoLabelForInspection.class,
      NoButtonGroupInspection.class,
      NoScrollPaneInspection.class,
      BoundFieldAssignmentInspection.class
    };
  }
}
