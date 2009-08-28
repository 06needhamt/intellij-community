package com.intellij.codeInsight.highlighting;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author msk
 */
public abstract class HighlightHandlerBase {
  static void setupFindModel(final Project project) {
    final FindManager findManager = FindManager.getInstance(project);
    FindModel model = findManager.getFindNextModel();
    if (model == null) {
      model = findManager.getFindInFileModel();
    }
    model.setSearchHighlighters(true);
    findManager.setFindWasPerformed();
    findManager.setFindNextModel(model);
  }

  protected static void setLineTextErrorStripeTooltip(final RangeHighlighter highlighter) {
    Document document = highlighter.getDocument();
    final int lineNumber = document.getLineNumber(highlighter.getStartOffset());
    final String lineText = document.getText().substring(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber));
    highlighter.setErrorStripeTooltip("  " + StringUtil.escapeXml(lineText.trim()) + "  ");
  }
}
