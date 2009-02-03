package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.util.StoringPropertyContainer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.BooleanProperty;

import java.util.Map;

public class RunManagerConfig {
  public static final String MAKE = ExecutionBundle.message("before.run.property.make");
  private static final BooleanProperty SHOW_SETTINGS = new BooleanProperty("showSettingsBeforeRunnig", true);
  private final StoringPropertyContainer myProperties;
  private final RunManagerImpl myManager;

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

  public boolean isCompileBeforeRunning(RunProfile runProfile){
    if (runProfile instanceof RunConfiguration){
      final Map<String,Boolean> beforeRun = myManager.getStepsBeforeLaunch((RunConfiguration)runProfile);
      final Boolean makeBeforeRun = beforeRun.get(MAKE);
      return Boolean.TRUE.equals(makeBeforeRun);
    }
    return false;
  }

}
