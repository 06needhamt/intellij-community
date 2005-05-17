package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lexer.Lexer;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 11:22:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesValueHighlighter extends PropertiesHighlighter {

  public Lexer getHighlightingLexer() {
    return new PropertiesValueHighlightingLexer();
  }
}
