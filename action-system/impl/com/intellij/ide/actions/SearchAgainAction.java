package com.intellij.ide.actions;

import com.intellij.find.FindManager;
import com.intellij.find.FindUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;

public class SearchAgainAction extends AnAction {
  public SearchAgainAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final FileEditor editor = e.getData(DataKeys.FILE_EDITOR);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
        project, new Runnable() {
        public void run() {
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
          if(FindManager.getInstance(project).findNextUsageInEditor(editor)) {
            return;
          }
          FindUtil.searchAgain(project, editor);
        }
      },
      IdeBundle.message("command.find.next"),
      null
    );
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(DataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    FileEditor editor = event.getData(DataKeys.FILE_EDITOR);
    presentation.setEnabled(editor instanceof TextEditor);
  }
}
