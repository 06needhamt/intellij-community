package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: Jan 16, 2005
 */
public class BackwardDependenciesAction extends BaseAnalysisAction {
  private AdditionalSettingsPanel myPanel;


  public BackwardDependenciesAction() {
    super(AnalysisScopeBundle.message("action.backward.dependency.analysis"), AnalysisScopeBundle.message("action.analysis.noun"));
  }

  protected void analyze(@NotNull final Project project, final AnalysisScope scope) {
    scope.setSearchInLibraries(true); //find library usages in project
    new BackwardDependenciesHandler(project, scope, new AnalysisScope(myPanel.myCombo.getSelectedScope(), project)).analyze();
    myPanel = null;
  }

  @Override
  protected void canceled() {
    super.canceled();
    myPanel = null;
  }

  @Nullable
  protected JComponent getAdditionalActionSettings(final Project project, final BaseAnalysisActionDialog dialog) {
    myPanel = new AdditionalSettingsPanel();
    myPanel.myCombo.init(project, null);
    return myPanel.myWholePanel;
  }

  private static class AdditionalSettingsPanel {
    private ScopeChooserCombo myCombo;
    private JPanel myWholePanel;
  }
}
