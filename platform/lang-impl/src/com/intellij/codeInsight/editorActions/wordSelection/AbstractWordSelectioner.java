package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.codeInsight.editorActions.SelectWordUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class AbstractWordSelectioner extends ExtendWordSelectionHandlerBase {
  public boolean canSelect(final PsiElement e) {
    return false;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> ranges;
    if (canSelect(e)) {
      ranges = super.select(e, editorText, cursorOffset, editor);
    }
    else {
      ranges = new ArrayList<TextRange>();
    }
    SelectWordUtil.addWordSelection(editor.getSettings().isCamelWords(), editorText, cursorOffset, ranges);
    return ranges;
  }
}