package com.jetbrains.python;

import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PythonModuleTypeBase<T extends ModuleBuilder> extends ModuleType<T> {
  public static ModuleType getInstance() {
    return ModuleTypeManager.getInstance().findByID(PYTHON_MODULE);
  }

  @NonNls public static final String PYTHON_MODULE = "PYTHON_MODULE";

  protected PythonModuleTypeBase() {
    super(PYTHON_MODULE);
  }

  @NotNull
  public String getName() {
    return "Python Module";
  }

  @NotNull
  public String getDescription() {
    return "Python modules are used for developing <b>Python</b> applications. Supported technologies include <b>Django, Google App Engine, Mako, Jinja2</b> and others.";
  }

  public Icon getBigIcon() {
    return PythonIcons.Python.Python_24;
  }

  public Icon getNodeIcon(final boolean isOpened) {
    return PythonIcons.Python.PythonClosed;
  }
}
