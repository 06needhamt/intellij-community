package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.completion.CodeCompletionHandler;
import com.intellij.codeInsight.completion.SmartCodeCompletionHandler;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 *
 */
public class SmartCodeCompletionAction extends BaseCodeInsightAction implements HintManager.ActionToIgnore{
  private static boolean ourDoingSmartCodeCompleteAction;

  public SmartCodeCompletionAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return createHandler();
  }

  public static CodeInsightActionHandler createHandler() {
    return new CodeInsightActionHandler() {
      public void invoke(Project project, Editor editor, PsiFile file) {
        try {
          ourDoingSmartCodeCompleteAction = true;
          if (file.getViewProvider().getRelevantLanguages().contains(StdLanguages.JAVA)) {
            FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.general");
            new SmartCodeCompletionHandler().invoke(project, editor, file);
          } else {
            FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.basic");
            new CodeCompletionHandler().invoke(project, editor, file);
          }
        }
        finally {
          ourDoingSmartCodeCompleteAction = false;
        }
      }

      public boolean startInWriteAction() {
        return false;
      }
    };
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }

  protected boolean isValidForLookup() {
    return true;
  }

  public static boolean isDoingSmartCodeCompleteAction() {
    return ourDoingSmartCodeCompleteAction;
  }

  public static void setDoingSmartCompletionAction(final boolean doingSmartCompletion) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    ourDoingSmartCodeCompleteAction = doingSmartCompletion;
  }
}
