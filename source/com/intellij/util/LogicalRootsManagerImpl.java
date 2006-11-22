/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.util;

import com.intellij.ProjectTopics;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author spleaner
 */
public class LogicalRootsManagerImpl extends LogicalRootsManager {
  private final Map<Module, MultiValuesMap<LogicalRootType, LogicalRoot>> myRoots = new THashMap<Module, MultiValuesMap<LogicalRootType, LogicalRoot>>();
  private final MultiValuesMap<LogicalRootType,NotNullFunction> myProviders = new MultiValuesMap<LogicalRootType, NotNullFunction>();
  private final MultiValuesMap<FileType,LogicalRootType> myFileTypes2RootTypes = new MultiValuesMap<FileType, LogicalRootType>();
  private ModuleManager myModuleManager;

  public LogicalRootsManagerImpl(final MessageBus bus, final ModuleManager moduleManager) {
    myModuleManager = moduleManager;

    final MessageBusConnection connection = bus.connect();
    connection.subscribe(ProjectTopics.LOGICAL_ROOTS, new LogicalRootListener() {
      public void logicalRootsChanged() {
        updateCache(moduleManager);
      }
    });
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        bus.asyncPublisher(ProjectTopics.LOGICAL_ROOTS).logicalRootsChanged();
      }
    });
  }

  private void updateCache(final ModuleManager moduleManager) {
    myRoots.clear();
    final Module[] modules = moduleManager.getModules();
    for (Module module : modules) {
      final MultiValuesMap<LogicalRootType, LogicalRoot> map = new MultiValuesMap<LogicalRootType, LogicalRoot>();
      for (Map.Entry<LogicalRootType, Collection<NotNullFunction>> entry : myProviders.entrySet()) {
        final Collection<NotNullFunction> functions = entry.getValue();
        for (NotNullFunction function : functions) {
          map.putAll(entry.getKey(), (List<LogicalRoot>)function.fun(module));
        }
      }
      myRoots.put(module, map);
    }
  }

  public void projectOpened() {
    registerLogicalRootProvider(LogicalRootType.SOURCE_ROOT, new NotNullFunction<Module, List<VirtualFileLogicalRoot>>() {
      @NotNull
      public List<VirtualFileLogicalRoot> fun(final Module module) {
        return ContainerUtil.map2List(ModuleRootManager.getInstance(module).getSourceRoots(), new Function<VirtualFile, VirtualFileLogicalRoot>() {
          public VirtualFileLogicalRoot fun(final VirtualFile s) {
            return new VirtualFileLogicalRoot(s);
          }
        });
      }
    });
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public List<LogicalRoot> getLogicalRoots(@NotNull final Module module) {
    return new ArrayList<LogicalRoot>(myRoots.get(module).values());
  }

  public List<LogicalRoot> getLogicalRootsOfType(@NotNull final Module module, @NotNull final LogicalRootType... types) {
    return ContainerUtil.concat(types, new Function<LogicalRootType, Collection<? extends LogicalRoot>>() {
      public Collection<? extends LogicalRoot> fun(final LogicalRootType s) {
        return getLogicalRootsOfType(module, s);
      }
    });
  }

  public <T extends LogicalRoot> List<T> getLogicalRootsOfType(@NotNull final Module module, @NotNull final LogicalRootType<T> type) {
    final MultiValuesMap<LogicalRootType, LogicalRoot> map = myRoots.get(module);
    if (map == null) {
      return Collections.emptyList();
    }

    return new ArrayList<T>((Collection<T>) map.get(type));
  }

  @NotNull
  public LogicalRootType[] getRootTypes(@NotNull final FileType type) {
    final Collection<LogicalRootType> rootTypes = myFileTypes2RootTypes.get(type);
    if (rootTypes == null) {
      return new LogicalRootType[0];
    }

    return rootTypes.toArray(new LogicalRootType[rootTypes.size()]);
  }

  public void registerRootType(@NotNull final FileType fileType, @NotNull final LogicalRootType... rootTypes) {
    myFileTypes2RootTypes.putAll(fileType, rootTypes);
  }

  public <T extends LogicalRoot> void registerLogicalRootProvider(@NotNull final LogicalRootType<T> rootType, @NotNull NotNullFunction<Module, List<T>> provider) {
    myProviders.put(rootType, provider);
    updateCache(myModuleManager);
  }
}
