package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.util.StoringPropertyContainer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.config.BooleanProperty;

public class RunManagerConfig {
  public static final String MAKE = ExecutionBundle.message("before.run.property.make");
  public static final String NONE = ExecutionBundle.message("before.run.property.none");
  public static final String MAKE_ANT = ExecutionBundle.message("before.run.property.make.ant");
  public static final String ANT = ExecutionBundle.message("before.run.property.ant");
  public static final String [] METHODS = new String[]{NONE, MAKE, ANT, MAKE_ANT};

  private static final BooleanProperty SHOW_SETTINGS = new BooleanProperty("showSettingsBeforeRunnig", true);
  private static final BooleanProperty COMPILE_BERFORE_RUNNING = new BooleanProperty("compileBeforeRunning", true);
  private StoringPropertyContainer myProperties;
  private RunManagerImpl myManager;

  public RunManagerConfig(PropertiesComponent propertiesComponent,
                          RunManagerImpl manager) {
    myManager = manager;
    myProperties = new StoringPropertyContainer("RunManagerConfig.", propertiesComponent);
  }

  public boolean isShowSettingsBeforeRun() {
    return SHOW_SETTINGS.value(myProperties);
  }

  public void setShowSettingsBeforeRun(final boolean value) {
    SHOW_SETTINGS.primSet(myProperties, value);
  }

  public boolean isCompileBeforeRunning() {
    return COMPILE_BERFORE_RUNNING.value(myProperties);
  }

  public void setCompileBeforeRunning(final boolean value) {
    COMPILE_BERFORE_RUNNING.primSet(myProperties, value);
  }

  public boolean isCompileBeforeRunning(RunProfile runProfile){
    return isCompileBeforeRunning() &&
           (!(runProfile instanceof RunConfiguration) ||
            Comparing.strEqual(myManager.getCompileMethodBeforeRun((RunConfiguration)runProfile), MAKE) ||
            Comparing.strEqual(myManager.getCompileMethodBeforeRun((RunConfiguration)runProfile), MAKE_ANT));
  }

}
