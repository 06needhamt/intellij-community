package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.generation.surroundWith.JavaWithIfSurrounder;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class SurroundWithIfFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.AddAssertStatementFix");
  private PsiExpression myExpression;
  private String myText;

  public String getName() {
    return InspectionsBundle.message("inspection.surround.if.quickfix", myExpression.getText());
  }

  public SurroundWithIfFix(PsiExpression expressionToAssert) {
    myExpression = expressionToAssert;
    myText = myExpression.getText();
  }

  private static Editor getEditor(Project project, PsiElement element) {
    FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(element.getContainingFile().getVirtualFile());
    for (FileEditor fileEditor : editors) {
      if (fileEditor instanceof TextEditor) return ((TextEditor)fileEditor).getEditor();
    }
    return null;
  }

  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    PsiStatement anchorStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    LOG.assertTrue(anchorStatement != null);
    Editor editor = getEditor(project, element);
    if (editor == null) return;
    PsiFile file = element.getContainingFile();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(file);
    try {
      TextRange textRange = new JavaWithIfSurrounder().surroundElements(project, editor, new PsiElement[]{anchorStatement});
      if (textRange == null) return;

      @NonNls String newText = myText + " != null";
      document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(),newText);
      editor.getCaretModel().moveToOffset(textRange.getEndOffset() + newText.length());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public String getFamilyName() {
    return InspectionsBundle.message("inspection.surround.if.family");
  }
}
