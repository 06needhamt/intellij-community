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
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleCircularDependencyException;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.facet.FacetInfo;
import com.intellij.facet.FacetManager;
import com.intellij.util.EventDispatcher;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public abstract class ModuleBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.ModuleBuilder");
  private String myName;
  private String myModuleFilePath;
  @Nullable
  private AddSupportContext[] myAddSupportContexts;
  private FacetInfo[] myFacetInfos = FacetInfo.EMPTY_ARRAY;
  private EventDispatcher<ModuleBuilderListener> myDispatcher = EventDispatcher.create(ModuleBuilderListener.class);

  @Nullable
  protected final String acceptParameter(String param) {
    return param != null && param.length() > 0 ? param : null;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = acceptParameter(name);
  }

  public String getModuleFilePath() {
    return myModuleFilePath;
  }

  public void setModuleFilePath(String path) {
    myModuleFilePath = acceptParameter(path);
  }

  @Nullable
  public String getModuleFileDirectory() {
    if (myModuleFilePath == null) {
      return null;
    }
    final String parent = new File(myModuleFilePath).getParent();
    if (parent == null) {
      return null;
    }
    return parent.replace(File.separatorChar, '/');
  }

  @NotNull
  public Module createModule(ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
    LOG.assertTrue(myName != null);
    LOG.assertTrue(myModuleFilePath != null);

    final ModuleType moduleType = getModuleType();
    final Module module = moduleModel.newModule(myModuleFilePath, moduleType);
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    setupRootModel(modifiableModel);
    modifiableModel.commit();

    module.setSavePathsRelative(true); // default setting

    FacetManager.getInstance(module).createAndCommitFacets(myFacetInfos);
    return module;
  }

  public void onModuleInitialized(final Module module) {
    myDispatcher.getMulticaster().moduleCreated(module);
  }

  public abstract void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException;

  public abstract ModuleType getModuleType();

  @NotNull
  public Module createAndCommit(ModifiableModuleModel moduleModel, boolean runFromProjectWizard) throws
                                                                                                 InvalidDataException,
                                                                                                 ConfigurationException,
                                                                                                 IOException,
                                                                                                 JDOMException,
                                                                                                 ModuleWithNameAlreadyExists,
                                                                                                 ModuleCircularDependencyException {
    final Module module = createModule(moduleModel);
    moduleModel.commit();

    if (runFromProjectWizard) {
      StartupManager.getInstance(module.getProject()).registerPostStartupActivity(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            onModuleInitialized(module);
            addSupport(module);
          }
        });
      }
    });
    }
    else {
      onModuleInitialized(module);
      addSupport(module);
    }
    return module;
  }

  public void addSupport(@NotNull Module module) {

    ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    if (myAddSupportContexts != null) {
      for (AddSupportContext supportContext : myAddSupportContexts) {
        if (supportContext.isAddSupport()) {
          supportContext.installSupportInAction(module, rootModel);
        }
      }
    }
    rootModel.commit();
  }

  public void setFacetInfos(final FacetInfo[] facetInfos) {
    myFacetInfos = facetInfos;
  }


  public void addListener(ModuleBuilderListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(ModuleBuilderListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Nullable
  public AddSupportContext[] getAddSupportContexts() {
    return myAddSupportContexts;
  }

  public void setAddSupportContexts(final AddSupportContext[] addSupportContexts) {
    myAddSupportContexts = addSupportContexts;
  }
}
