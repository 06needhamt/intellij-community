package com.intellij.codeInspection.ex;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class DisableInspectionToolAction implements IntentionAction {
  private final String myToolId;
  public static final String NAME = InspectionsBundle.message("disable.inspection.action.name");

  public DisableInspectionToolAction(LocalInspectionTool tool) {
    myToolId = tool.getShortName();
  }

  public DisableInspectionToolAction(final HighlightDisplayKey key) {
    myToolId = key.toString();
  }

  @NotNull
  public String getText() {
    return NAME;
  }

  @NotNull
  public String getFamilyName() {
    return NAME;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(file.getProject());
    InspectionProfile inspectionProfile = profileManager.getInspectionProfile();
    ModifiableModel model = inspectionProfile.getModifiableModel();
    model.disableTool(myToolId);
    try {
      model.commit();
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
    }
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  public boolean startInWriteAction() {
    return false;
  }
}
