/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.Nullable;

public abstract class EditorAction extends AnAction {
  private EditorActionHandler myHandler;

  public final EditorActionHandler getHandler() {
    return myHandler;
  }

  protected EditorAction(EditorActionHandler defaultHandler) {
    myHandler = defaultHandler;
    setEnabledInModalContext(true);
  }

  public final EditorActionHandler setupHandler(EditorActionHandler newHandler) {
    EditorActionHandler tmp = myHandler;
    myHandler = newHandler;
    return tmp;
  }

  public final void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = getEditor(dataContext);
    actionPerformed(editor, dataContext);
  }

  @Nullable
  protected Editor getEditor(final DataContext dataContext) {
    return PlatformDataKeys.EDITOR.getData(dataContext);
  }

  public final void actionPerformed(final Editor editor, final DataContext dataContext) {
    if (editor == null) return;

    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    Runnable command = new Runnable() {
      public void run() {
        getHandler().execute(editor, getProjectAwareDataContext(editor, dataContext));
      }
    };

    String commandName = getTemplatePresentation().getText();
    if (commandName == null) commandName = "";
    // use new Ref() here to avoid merging two consequential commands, and, in the same time, pass along the Document
    commandProcessor.executeCommand(editor.getProject(), command, commandName, new Ref(editor.getDocument()), UndoConfirmationPolicy.DEFAULT, editor.getDocument());
  }

  public void update(Editor editor, Presentation presentation, DataContext dataContext) {
    presentation.setEnabled(getHandler().isEnabled(editor, dataContext));
  }

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    Editor editor = getEditor(dataContext);
    if (editor == null) {
      presentation.setEnabled(false);
    }
    else {
      update(editor, presentation, dataContext);
    }
  }

  private static DataContext getProjectAwareDataContext(final Editor editor, final DataContext original) {
    if (original.getData(DataConstants.PROJECT) == editor.getProject()) return original;

    return new DataContext() {
      public Object getData(String dataId) {
        if (DataConstants.PROJECT.equals(dataId)) {
          return editor.getProject();
        }
        return original.getData(dataId);
      }
    };
  }
}
