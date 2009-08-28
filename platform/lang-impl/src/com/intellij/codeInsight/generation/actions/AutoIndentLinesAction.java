package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.AutoIndentLinesHandler;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

public class AutoIndentLinesAction extends BaseCodeInsightAction implements DumbAware {
  protected CodeInsightActionHandler getHandler() {
    return new AutoIndentLinesHandler();
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    final FileType fileType = file.getFileType();
    return fileType instanceof LanguageFileType &&
           LanguageFormatting.INSTANCE.forContext(((LanguageFileType)fileType).getLanguage(), file) != null;
  }
}