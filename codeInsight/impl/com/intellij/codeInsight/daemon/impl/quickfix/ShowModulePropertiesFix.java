package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ShowModulePropertiesFix implements IntentionAction {
  private final String myModuleName;

  public ShowModulePropertiesFix(String moduleName) {
    myModuleName = moduleName;
  }
  public ShowModulePropertiesFix(PsiElement context) {
    Module module = ModuleUtil.findModuleForPsiElement(context);
    myModuleName = module.getName();
  }

  @NotNull
  public String getText() {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.MODULE_SETTINGS);
    return action.getTemplatePresentation().getText();
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(final Project project, final Editor editor, final PsiFile file) {
    return true;
  }

  public void invoke(final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    ModulesConfigurator.showDialog(project, myModuleName, null, false);
  }

  public boolean startInWriteAction() {
    return false;
  }
}