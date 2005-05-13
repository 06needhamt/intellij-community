package com.intellij.codeInsight.editorActions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.jsp.JspFile;
import com.intellij.lang.java.JavaLanguage;

import java.util.List;

public class SelectWordHandler extends EditorActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.editorActions.SelectWordHandler");

  private EditorActionHandler myOriginalHandler;

  public SelectWordHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void execute(Editor editor, DataContext dataContext) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: execute(editor='" + editor + "')");
    }

    Project project = (Project)DataManager.getInstance().getDataContext(editor.getComponent()).getData(DataConstants.PROJECT);
    if (project == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    Document document = editor.getDocument();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);

    if (file == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, dataContext);
      }
      return;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    doAction(editor, file);
  }

  private static void doAction(Editor editor, PsiFile file) {
    CharSequence text = editor.getDocument().getCharsSequence();

    if (file instanceof PsiCompiledElement) {
      file = (PsiFile)((PsiCompiledElement)file).getMirror();
    }

    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.select.word");

    int caretOffset = editor.getCaretModel().getOffset();

    if (caretOffset > 0 && caretOffset < editor.getDocument().getTextLength() &&
        !Character.isJavaIdentifierPart(text.charAt(caretOffset)) &&
        Character.isJavaIdentifierPart(text.charAt(caretOffset - 1))) {
      caretOffset--;
    }

    PsiElement element = file.findElementAt(caretOffset);

    if (element instanceof PsiWhiteSpace && caretOffset > 0) {
      PsiElement anotherElement = file.findElementAt(caretOffset - 1);

      if (!(anotherElement instanceof PsiWhiteSpace)) {
        element = anotherElement;
      }
    }

    while (element instanceof PsiWhiteSpace) {
      if (element.getNextSibling() == null) {
        element = element.getParent();
        continue;
      }

      element = element.getNextSibling();
      caretOffset = element.getTextRange().getStartOffset();
    }

    TextRange selectionRange = new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());

    TextRange newRange = null;

    int textLength = editor.getDocument().getTextLength();

    while (element != null && !(element instanceof PsiFile)) {
      newRange = advance(selectionRange, element, text, caretOffset, editor);

      if (newRange != null) {
        break;
      }

      element = getUpperElement(element, selectionRange);
    }

    if (newRange == null) {
      newRange = new TextRange(0, textLength);
    }

    if (!editor.getSelectionModel().hasSelection() || newRange.contains(selectionRange)) {
      selectionRange = newRange;
    }

    editor.getSelectionModel().setSelection(selectionRange.getStartOffset(), selectionRange.getEndOffset());
  }

  private static PsiElement getUpperElement(final PsiElement e, final TextRange selectionRange) {
    final PsiElement parent = e.getParent();

    if (e.getContainingFile() instanceof JspFile && e.getLanguage() instanceof JavaLanguage) {
      final JspFile psiFile = (JspFile)e.getContainingFile();
      if (e.getParent().getTextLength() == psiFile.getTextLength()) {
        final PsiFile[] psiRoots = psiFile.getPsiRoots();
        for (PsiFile root : psiRoots) {
          if (root instanceof XmlFile) {
            XmlFile xmlFile = (XmlFile)root;
            XmlTag tag = PsiTreeUtil.getParentOfType(xmlFile.getDocument().findElementAt(e.getTextRange().getStartOffset()), XmlTag.class);
            while (tag != null && !tag.getTextRange().contains(selectionRange)) {
              tag = tag.getParentTag();
            }

            if (tag != null) return tag;
          }
        }
      }
    }

    return parent;
  }

  private static TextRange advance(TextRange selectionRange,
                                   PsiElement element,
                                   CharSequence text,
                                   int cursorOffset,
                                   Editor editor) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: advance(selectionRange='" + selectionRange + "', element='" + element + "')");
    }
    TextRange minimumRange = null;

    for (SelectWordUtil.Selectioner selectioner : SelectWordUtil.SELECTIONERS) {
      if (!selectioner.canSelect(element)) {
        continue;
      }
      List<TextRange> ranges = selectioner.select(element, text, cursorOffset, editor);

      if (ranges == null) {
        continue;
      }

      for (int j = 0; j < ranges.size(); j++) {
        TextRange range = ranges.get(j);

        if (range == null) {
          continue;
        }

        if (range.contains(selectionRange) && !range.equals(selectionRange)) {
          if (minimumRange == null || minimumRange.contains(range)) {
            minimumRange = range;
          }
        }
      }
    }

    //TODO remove assert
    if (minimumRange != null) {
      LOG.assertTrue(minimumRange.getEndOffset() <= text.length());
    }

    return minimumRange;
  }
}