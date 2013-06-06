package com.jetbrains.python.testing.nosetest;

import com.intellij.execution.Location;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.*;

public class PythonNoseTestConfigurationProducer extends
                                                 PythonTestConfigurationProducer {
  public PythonNoseTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().PY_NOSETEST_FACTORY);
  }

  protected boolean isAvailable(Location location) {
    PsiElement element = location.getPsiElement();
    Module module = location.getModule();
    if (module == null) module = ModuleManager.getInstance(element.getProject()).getModules()[0];
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    return (TestRunnerService.getInstance(module).getProjectConfiguration().equals(
      PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME) && sdk != null);
  }

  @Override
  protected boolean isTestFunction(PyFunction pyFunction) {
    return pyFunction != null && PythonUnitTestUtil.isTestCaseFunction(pyFunction, false);
  }
}