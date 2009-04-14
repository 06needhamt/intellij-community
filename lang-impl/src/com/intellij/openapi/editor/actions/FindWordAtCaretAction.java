/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 18, 2002
 * Time: 5:49:15 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.actions;

import com.intellij.find.FindUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;

public class FindWordAtCaretAction extends EditorAction implements DumbAware {
  private static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      FindUtil.findWordAtCaret(project, editor);
    }

    public boolean isEnabled(Editor editor, DataContext dataContext) {
      Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      return project != null;
    }
  }

  public FindWordAtCaretAction() {
    super(new Handler());
  }
}
