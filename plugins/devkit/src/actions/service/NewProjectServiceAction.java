/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.actions.service;

import com.intellij.psi.PsiDirectory;
import org.jetbrains.idea.devkit.DevKitBundle;

public class NewProjectServiceAction extends GenerateServiceClassAndPatchPluginXmlActionBase {
  public NewProjectServiceAction() {
    super(DevKitBundle.message("new.menu.project.service.text"),
          DevKitBundle.message("new.menu.project.service.description"), null);
  }

  @Override
  protected String getClassNamePrompt() {
    return DevKitBundle.message("new.project.service.prompt");
  }

  @Override
  protected String getClassNamePromptTitle() {
    return DevKitBundle.message("new.project.service.prompt.title");
  }

  @Override
  protected String getClassTemplateName() {
    return "ProjectService.java";
  }

  @Override
  protected String getErrorTitle() {
    return DevKitBundle.message("new.project.service.error");
  }

  @Override
  protected String getCommandName() {
    return DevKitBundle.message("new.project.service.command");
  }

  @Override
  protected String getActionName(PsiDirectory directory, String newName) {
    return DevKitBundle.message("new.project.service.action.name", directory, newName);
  }

  @Override
  protected String getTagName() {
    return "projectService";
  }
}
