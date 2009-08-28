/**
 * @author cdr
 */
package com.intellij.ide.projectView.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import org.jetbrains.annotations.Nullable;

public class MoveModulesToGroupAction extends AnAction {
  protected final ModuleGroup myModuleGroup;

  public MoveModulesToGroupAction(ModuleGroup moduleGroup, String title) {
    super(title);
    myModuleGroup = moduleGroup;
  }

  public void update(AnActionEvent e) {
    Presentation presentation = getTemplatePresentation();
    final DataContext dataContext = e.getDataContext();
    final Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);

    String description = IdeBundle.message("message.move.modules.to.group", whatToMove(modules), myModuleGroup.presentableText());
    presentation.setDescription(description);
  }

  protected static String whatToMove(Module[] modules) {
    return modules.length == 1 ? IdeBundle.message("message.module", modules[0].getName()) : IdeBundle.message("message.modules");
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Module[] modules = (Module[])dataContext.getData(DataConstants.MODULE_CONTEXT_ARRAY);
    doMove(modules, myModuleGroup, dataContext);
  }

  public static void doMove(final Module[] modules, final ModuleGroup group, @Nullable final DataContext dataContext) {
    Project project = modules[0].getProject();
    for (final Module module : modules) {
      ModifiableModuleModel model = dataContext != null
                                    ? (ModifiableModuleModel)dataContext.getData(DataConstantsEx.MODIFIABLE_MODULE_MODEL)
                                    : null;
      if (model != null){
        model.setModuleGroupPath(module, group == null ? null : group.getGroupPath());
      } else {
        ModuleManagerImpl.getInstanceImpl(project).setModuleGroupPath(module, group == null ? null : group.getGroupPath());
      }
    }

    AbstractProjectViewPane pane = ProjectView.getInstance(project).getCurrentProjectViewPane();
    if (pane != null) {
      pane.updateFromRoot(true);
    }

    if (!ProjectSettingsService.getInstance(project).processModulesMoved(modules, group) && pane != null) {
      if (group != null) {
        pane.selectModuleGroup(group, true);
      }
      else {
        pane.selectModule(modules[0], true);
      }
    }
  }
}