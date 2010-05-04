package com.jetbrains.python.console;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.PathUtil;
import com.jetbrains.django.util.DjangoUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkType;

/**
 * @author oleg
 */
public class RunPythonConsoleAction extends AnAction implements DumbAware {

  public RunPythonConsoleAction() {
    super();
    getTemplatePresentation().setIcon(IconLoader.getIcon("/com/jetbrains/python/python.png"));
  }

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setVisible(false);
      e.getPresentation().setEnabled(false);
      final Project project = e.getData(LangDataKeys.PROJECT);
      if (project != null){
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          e.getPresentation().setVisible(true);
          if (PythonSdkType.findPythonSdk(module) != null){
            e.getPresentation().setEnabled(true);
            break;
          }
        }
      }
    }

    public void actionPerformed(final AnActionEvent e) {
      final Project project = e.getData(LangDataKeys.PROJECT);
      assert project != null : "Project is null";
      Sdk sdk = null;
      Module module = null;
      for (Module m : ModuleManager.getInstance(project).getModules()) {
        module = m;
        sdk = PythonSdkType.findPythonSdk(module);
        if (sdk != null){
          break;
        }
      }
      assert module != null : "Module is null";
      assert sdk != null : "Sdk is null";

      final String path = ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();
      PydevConsoleRunner.run(project, sdk, PyBundle.message("python.console"), path,
                             "sys.path.append('" + path + "')");
    }

}
