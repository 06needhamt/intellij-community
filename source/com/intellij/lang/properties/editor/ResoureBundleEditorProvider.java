package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class ResoureBundleEditorProvider implements FileEditorProvider, ApplicationComponent {
  private static final ResourceBundleFileType RESOURCE_BUNDLE_FILE_TYPE = new ResourceBundleFileType();

  public ResoureBundleEditorProvider(Application application, final FileTypeManagerEx fileTypeManagerEx) {
    application.runWriteAction(new Runnable() {
      public void run() {
        fileTypeManagerEx.registerFileType(RESOURCE_BUNDLE_FILE_TYPE);
      }
    });
  }

  public boolean accept(@NotNull Project project, @NotNull VirtualFile file){
    if (file instanceof ResourceBundleAsVirtualFile) return true;
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile instanceof PropertiesFile && ((PropertiesFile)psiFile).getResourceBundle().getPropertiesFiles(project).size() > 1;
  }

  @NotNull
  public FileEditor createEditor(@NotNull Project project, @NotNull final VirtualFile file){
    ResourceBundle resourceBundle;
    if (file instanceof ResourceBundleAsVirtualFile) {
      resourceBundle = ((ResourceBundleAsVirtualFile)file).getResourceBundle();
    }
    else {
      PropertiesFile psiFile = (PropertiesFile)PsiManager.getInstance(project).findFile(file);
      if (psiFile == null) {
        throw new IllegalArgumentException("psifile cannot be null");
      }
      resourceBundle = psiFile.getResourceBundle();
    }

    return new ResourceBundleEditor(project, resourceBundle);
  }

  public void disposeEditor(@NotNull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @NotNull
  public FileEditorState readState(@NotNull Element element, @NotNull Project project, @NotNull VirtualFile file) {
    return new ResourceBundleEditor.ResourceBundleEditorState(null);
  }

  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element element){
  }

  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
  }

  @NotNull
  public String getEditorTypeId(){
    return "ResourceBundle";
  }

  @NotNull
  public String getComponentName(){
    return "ResourceBundle" + "Provider";
  }

  public void initComponent() { }

  public void disposeComponent(){
  }
}
