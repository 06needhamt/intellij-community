package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Computable;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

/**
 * User: anna
 * Date: Nov 24, 2004
 */
public class PluginBuildUtil {

  @Nullable public static String getPluginExPath(Module module) {
    final ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
    if (jdk == null || !(jdk.getSdkType() instanceof IdeaJdk)) {
      return null;
    }
    String sandboxHome = ((Sandbox)jdk.getSdkAdditionalData()).getSandboxHome();
    try {
      sandboxHome = new File(sandboxHome).getCanonicalPath();
    }
    catch (IOException e) {
      return null;
    }
    return sandboxHome + File.separator + "plugins" + File.separator + module.getName();
  }

  public static void getDependencies(Module module, Set<Module> modules) {
    if (modules.contains(module)) return;
    Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
    for (int i = 0; i < dependencies.length; i++) {
      Module dependency = dependencies[i];
      if (dependency.getModuleType() == ModuleType.JAVA) {
        modules.add(dependency);
        getDependencies(dependency, modules);
      }
    }
  }

  public static Module[] getWrongSetDependencies(final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      public Module[] compute() {
        ArrayList<Module> result = new ArrayList<Module>();
        final Module[] projectModules = ModuleManager.getInstance(module.getProject()).getModules();
        for (int i = 0; i < projectModules.length; i++) {
          if (ArrayUtil.find(ModuleRootManager.getInstance(projectModules[i]).getDependencies(), module) > -1) {
            result.add(projectModules[i]);
          }
        }
        return result.toArray(new Module[result.size()]);
      }
    });

  }

  public static void getLibraries(Module module, Set<Library> libs) {
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (int i = 0; i < orderEntries.length; i++) {
      OrderEntry orderEntry = orderEntries[i];
      if (orderEntry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libEntry = (LibraryOrderEntry)orderEntry;
        Library lib = libEntry.getLibrary();
        if (lib == null) continue;
        libs.add(lib);
      }
    }
  }
}
