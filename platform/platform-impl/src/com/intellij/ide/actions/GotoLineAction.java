package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.GotoLineNumberDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;

public class GotoLineAction extends AnAction implements DumbAware {
  public GotoLineAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (Boolean.TRUE.equals(e.getData(PlatformDataKeys.IS_MODAL_CONTEXT))) {
      GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
      dialog.show();
    }
    else {
      CommandProcessor processor = CommandProcessor.getInstance();
      processor.executeCommand(
          project, new Runnable(){
          public void run() {
            GotoLineNumberDialog dialog = new GotoLineNumberDialog(project, editor);
            dialog.show();
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
          }
        },
        IdeBundle.message("command.go.to.line"),
        null
      );
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    Editor editor = event.getData(PlatformDataKeys.EDITOR);
    presentation.setEnabled(editor != null);
    presentation.setVisible(editor != null);
  }
}
