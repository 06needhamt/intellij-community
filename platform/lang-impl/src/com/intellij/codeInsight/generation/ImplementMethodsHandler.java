package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;

public class ImplementMethodsHandler implements CodeInsightActionHandler{
  public final void invoke(final Project project, final Editor editor, PsiFile file) {
    if (!FileDocumentManager.getInstance().fileForDocumentCheckedOutSuccessfully(editor.getDocument(), project)){
      return;
    }

    Language language = PsiUtilBase.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    final LanguageCodeInsightActionHandler codeInsightActionHandler = CodeInsightActions.IMPLEMENT_METHOD.forLanguage(language);
    if (codeInsightActionHandler != null) {
      codeInsightActionHandler.invoke(project, editor, file);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}