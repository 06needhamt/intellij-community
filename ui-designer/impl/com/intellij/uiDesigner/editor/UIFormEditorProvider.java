package com.intellij.uiDesigner.editor;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class UIFormEditorProvider implements FileEditorProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.editor.UIFormEditorProvider");

  public boolean accept(@NotNull final Project project, @NotNull final VirtualFile file){
    return
      FileTypeManager.getInstance().getFileTypeByFile(file) == StdFileTypes.GUI_DESIGNER_FORM &&
      !StdFileTypes.GUI_DESIGNER_FORM.isBinary() &&
      VfsUtil.getModuleForFile(project, file) != null;
  }

  @NotNull public FileEditor createEditor(@NotNull final Project project, @NotNull final VirtualFile file){
    LOG.assertTrue(accept(project, file));
    return new UIFormEditor(project, file);
  }

  public void disposeEditor(@NotNull final FileEditor editor){
    Disposer.dispose(editor);
  }

  @NotNull
  public FileEditorState readState(@NotNull final Element element, @NotNull final Project project, @NotNull final VirtualFile file){
    //TODO[anton,vova] implement
    return new MyEditorState(-1, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void writeState(@NotNull final FileEditorState state, @NotNull final Project project, @NotNull final Element element){
    //TODO[anton,vova] implement
  }

  @NotNull public String getEditorTypeId(){
    return "ui-designer";
  }

  @NotNull public FileEditorPolicy getPolicy() {
    return
      ApplicationManagerEx.getApplicationEx().isInternal() ?
      FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR : FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }

}
