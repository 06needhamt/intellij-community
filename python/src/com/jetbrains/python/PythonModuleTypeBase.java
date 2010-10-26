package com.jetbrains.python;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PythonModuleTypeBase<T extends ModuleBuilder> extends ModuleType<T> {
  @NonNls public static final String PYTHON_MODULE = "PYTHON_MODULE";
  private final Icon myBigIcon = IconLoader.getIcon("/com/jetbrains/python/python_24.png");
  private final Icon myOpenIcon = IconLoader.getIcon("/com/jetbrains/python/pythonOpen.png");
  private final Icon myClosedIcon = IconLoader.getIcon("/com/jetbrains/python/pythonClosed.png");

  protected PythonModuleTypeBase() {
    super(PYTHON_MODULE);
  }

  public String getName() {
    return "Python Module";
  }

  public String getDescription() {
    return "Provides facilities for developing Python and Django applications";
  }

  public Icon getBigIcon() {
    return myBigIcon;
  }

  public Icon getNodeIcon(final boolean isOpened) {
    return isOpened ? myOpenIcon : myClosedIcon;
  }
}
