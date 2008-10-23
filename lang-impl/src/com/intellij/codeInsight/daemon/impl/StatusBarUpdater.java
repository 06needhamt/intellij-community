
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;

public class StatusBarUpdater {
  private final Project myProject;
  private String myLastStatusText;
  private final CaretListener myCaretListener;
  private final UpdateStatusRunnable myUpdateStatusRunnable = new UpdateStatusRunnable();

  public StatusBarUpdater(Project project) {
    myProject = project;

    myCaretListener = new CaretListener() {
      public void caretPositionChanged(CaretEvent e) {
        ApplicationManager.getApplication().invokeLater(myUpdateStatusRunnable);
      }
    };
    EditorFactory.getInstance().getEventMulticaster().addCaretListener(myCaretListener);

    FileEditorManager.getInstance(myProject).addFileEditorManagerListener(
      new FileEditorManagerAdapter() {
        public void selectionChanged(FileEditorManagerEvent e) {
          ApplicationManager.getApplication().invokeLater(myUpdateStatusRunnable);
        }
      }
    );
  }

  public void dispose() {
    EditorFactory.getInstance().getEventMulticaster().removeCaretListener(myCaretListener);
  }

  public void updateStatus() {
    Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (editor == null || !editor.getContentComponent().hasFocus()){
      return;
    }

    final Document document = editor.getDocument();
    if (document instanceof DocumentEx && ((DocumentEx)document).isInBulkUpdate()) return;

    int offset = editor.getCaretModel().getOffset();
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(myProject);
    HighlightInfo info = ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(document, offset, false);
    String text = info != null && info.description != null ? info.description : "";

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (!text.equals(myLastStatusText)){
      statusBar.setInfo(text);
      myLastStatusText = text;
    }
    if (statusBar instanceof StatusBarEx) {
      ((StatusBarEx)statusBar).update(editor);
    }
  }

  private class UpdateStatusRunnable implements Runnable {
    public void run() {
      if (!myProject.isDisposed()) {
        updateStatus();
      }
    }
  }
}