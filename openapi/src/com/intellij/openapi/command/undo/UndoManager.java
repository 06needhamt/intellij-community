/*
* Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.command.undo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class UndoManager {
  public static UndoManager getInstance(Project project) {
    return project.getComponent(UndoManager.class);
  }

  public static UndoManager getGlobalInstance() {
    return ApplicationManager.getApplication().getComponent(UndoManager.class);
  }

  public abstract void undoableActionPerformed(UndoableAction action);

  public abstract boolean isUndoInProgress();
  public abstract boolean isRedoInProgress();

  public abstract void undo(FileEditor editor);
  public abstract void redo(FileEditor editor);
  public abstract boolean isUndoAvailable(FileEditor editor);
  public abstract boolean isRedoAvailable(FileEditor editor);

  public abstract void clearUndoRedoQueue(VirtualFile file);
  public abstract void clearUndoRedoQueue(FileEditor editor);
  public abstract void clearUndoRedoQueue(Document document);

  public abstract void dropHistory();
}