/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.util.HtmlUtil;

/**
* @author peter
*/
public class XmlAttributeInsertHandler implements InsertHandler<MutableLookupElement> {
  public static final XmlAttributeInsertHandler INSTANCE = new XmlAttributeInsertHandler();

  public void handleInsert(InsertionContext context, MutableLookupElement item) {
    final Editor editor = context.getEditor();

    final Document document = editor.getDocument();
    final int caretOffset = editor.getCaretModel().getOffset();
    if (PsiDocumentManager.getInstance(editor.getProject()).getPsiFile(document).getLanguage() == HTMLLanguage.INSTANCE &&
        HtmlUtil.isSingleHtmlAttribute((String)item.getObject())) {
      return;
    }

    final CharSequence chars = document.getCharsSequence();
    if (!CharArrayUtil.regionMatches(chars, caretOffset, "=\"") && !CharArrayUtil.regionMatches(chars, caretOffset, "='")) {
      if (caretOffset >= document.getTextLength() || "/> \n\t\r".indexOf(document.getCharsSequence().charAt(caretOffset)) < 0) {
        document.insertString(caretOffset, "=\"\" ");
      }
      else {
        document.insertString(caretOffset, "=\"\"");
      }

      if ('=' == context.getCompletionChar()) {
        context.setAddCompletionChar(false); // IDEA-19449
      }
    }

    editor.getCaretModel().moveToOffset(caretOffset + 2);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }
}
