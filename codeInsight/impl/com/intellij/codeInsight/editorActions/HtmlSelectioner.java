/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 18, 2002
 * Time: 10:30:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.xml.XmlComment;

import java.util.List;

public class HtmlSelectioner extends SelectWordUtil.WordSelectioner {
  private static SelectWordUtil.Selectioner ourStyleSelectioner;

  public static void setStyleSelectioner(SelectWordUtil.Selectioner _styleSelectioner) {
    ourStyleSelectioner = _styleSelectioner;
  }

  protected boolean canSelectXml(PsiElement e) {
    return true;
  }

  public boolean canSelect(PsiElement e) {
    if (e instanceof XmlToken) {
      PsiFile file = e.getContainingFile();
      VirtualFile virtualFile = file != null ? file.getVirtualFile() : null;
      FileType fType = virtualFile != null ? FileTypeManager.getInstance().getFileTypeByFile(virtualFile) : null;

      return fType == StdFileTypes.HTML || fType == StdFileTypes.XHTML || fType == StdFileTypes.JSPX || fType == StdFileTypes.JSP;
    }
    return false;
  }

  public List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
    List<TextRange> result = super.select(e, editorText, cursorOffset, editor);

    if (ourStyleSelectioner!=null) {
      List<TextRange> o = ourStyleSelectioner.select(e, editorText, cursorOffset, editor);
      if (o!=null) result.addAll(o);
    }

    final PsiElement parent = e.getParent();
    if (parent instanceof XmlComment) {
      result.addAll(SelectWordUtil.expandToWholeLine(editorText, parent.getTextRange(), true));
    }
    
    PsiFile psiFile = e.getContainingFile();
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(psiFile.getVirtualFile());

    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(e.getProject(), psiFile.getVirtualFile());
    highlighter.setText(editorText);

    HighlighterIterator i = highlighter.createIterator(cursorOffset);
    if (i.atEnd()) return result;

    final IElementType tokenType = i.getTokenType();
    if (tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
        tokenType == XmlTokenType.XML_CHAR_ENTITY_REF ||
        tokenType == XmlTokenType.XML_ENTITY_REF_TOKEN ||
        tokenType == XmlTokenType.XML_NAME) {
      addAttributeSelection(result, i);
    }

    addTagSelection(editorText, cursorOffset, fileType, highlighter, result);

    return result;
  }

  private static void addTagSelection(CharSequence editorText, int cursorOffset, FileType fileType, EditorHighlighter highlighter, List<TextRange> result) {
    int start = cursorOffset;

    while (true) {
      if (start < 0) return;
      HighlighterIterator i = highlighter.createIterator(start);
      if (i.atEnd()) return;

      while (true) {
        if (i.getTokenType() ==  XmlTokenType.XML_START_TAG_START) break;
        i.retreat();
        if (i.atEnd()) return;
      }

      start = i.getStart();
      final boolean matched = BraceMatchingUtil.matchBrace(editorText, fileType, i, true);

      if (matched) {
        final int tagEnd = i.getEnd();
        result.add(new TextRange(start, tagEnd));

        HighlighterIterator j = highlighter.createIterator(start);
        while (!j.atEnd() && j.getTokenType() != XmlTokenType.XML_TAG_END) j.advance();
        while (!i.atEnd() && i.getTokenType() != XmlTokenType.XML_END_TAG_START) i.retreat();

        if (!i.atEnd() && !j.atEnd()) {
          result.add(new TextRange(j.getEnd(), i.getStart()));
        }
        if (!j.atEnd()) {
          result.add(new TextRange(start, j.getEnd()));
        }
        if (!i.atEnd()) {
          result.add(new TextRange(i.getStart(),tagEnd));
        }
      }

      start--;
    }
  }

  private static void addAttributeSelection(List<TextRange> result, HighlighterIterator i) {
    result.add(new TextRange(i.getStart(), i.getEnd()));

    if (i.getTokenType() == XmlTokenType.XML_CHAR_ENTITY_REF || i.getTokenType() == XmlTokenType.XML_ENTITY_REF_TOKEN) {
      i.retreat();
      if (i.atEnd()) {
        i.advance();
      } else if (i.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
        i.advance();
        i.advance();
        if (i.atEnd() || i.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
          i.retreat();
          return;
        }
      }
    }

    if (i.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
      int start = i.getStart() - 1;
      int end = i.getEnd() + 1;

      // Check quote before value
      i.retreat();
      boolean hasQuotes = true;
      int retreatCount = 1;

      if (!i.atEnd()) {
        IElementType tokenType = i.getTokenType();

        while(tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
              tokenType == XmlTokenType.XML_CHAR_ENTITY_REF ||
              tokenType == XmlTokenType.XML_ENTITY_REF_TOKEN
          )   {
          retreatCount++;
          i.retreat();
          if (i.atEnd()) {
            tokenType = null;
            break;
          }
          tokenType = i.getTokenType();
        }

        if (tokenType != XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER) {
          hasQuotes = false;
        } else {
          start = i.getStart();
        }
      }

      while(retreatCount-- > 0) i.advance();

      // Check quote after value
      i.advance();
      int advanceCount = 1;

      if (!i.atEnd()) {
        IElementType tokenType = i.getTokenType();
        while(tokenType == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN ||
              tokenType == XmlTokenType.XML_CHAR_ENTITY_REF ||
              tokenType == XmlTokenType.XML_ENTITY_REF_TOKEN
          )   {
          advanceCount++;
          i.advance();
          if (i.atEnd()) {
            tokenType = null;
            break;
          }
          tokenType = i.getTokenType();
        }

        if (tokenType != XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
          hasQuotes = false;
        }else {
          end = i.getEnd();
        }
      }

      while(advanceCount-- > 0) i.retreat();

      if (hasQuotes) {
        result.add(new TextRange(start , end ));
        if (i.getStart() != start + 1 ||  i.getEnd() != end - 1) result.add(new TextRange(start + 1, end - 1 ));
      }
    }

    while (!i.atEnd() && i.getTokenType() != XmlTokenType.XML_NAME) { i.retreat(); }
    if (i.atEnd()) return;
    i.retreat();

    IElementType tokenType = i.getTokenType();
    i.advance();
    if(tokenType == XmlTokenType.XML_START_TAG_START) {
      return;
    }
    int start = i.getStart();

    while (!i.atEnd() &&
           i.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN &&
           i.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER
           ) { i.advance(); }
    if (i.atEnd()) return;

    int end = i.getEnd();

    if (i.getTokenType() != XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) i.advance();
    if (!i.atEnd() && i.getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
      end = i.getEnd();
    }
    
    result.add(new TextRange(start, end));
  }
}
