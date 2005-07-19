/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

import java.awt.*;

public abstract class ShowSettingsUtil {
  public static ShowSettingsUtil getInstance() {
    return ApplicationManager.getApplication().getComponent(ShowSettingsUtil.class);
  }

  public abstract void showSettingsDialog(Project project, ConfigurableGroup[] group);

  public abstract boolean editConfigurable(Project project, Configurable configurable);

  public abstract boolean editConfigurable(Project project, Configurable configurable, Runnable advancedInitialization);

  public abstract boolean showCodeStyleSettings(Project project, Class pageToSelect);

  public abstract boolean editConfigurable(Component parent, Configurable configurable);

  public abstract boolean editConfigurable(Project project, String dimensionServiceKey, Configurable configurable);

  public abstract boolean editConfigurable(Component parent, String dimensionServiceKey, Configurable configurable);
}