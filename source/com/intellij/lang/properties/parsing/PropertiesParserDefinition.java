package com.intellij.lang.properties.parsing;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.properties.parsing.PropertiesParser;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 6:07:21 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesParserDefinition implements ParserDefinition {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.PropertiesParserDefinition");

  public Lexer createLexer(Project project) {
    return new PropertiesLexer();
  }

  public IElementType getFileNodeType() {
    return PropertiesElementTypes.FILE;
  }

  public TokenSet getWhitespaceTokens() {
    return PropertiesTokenTypes.WHITESPACES;
  }

  public TokenSet getCommentTokens() {
    return PropertiesTokenTypes.COMMENTS;
  }

  public PsiParser createParser(final Project project) {
    return new PropertiesParser();
  }

  public PsiFile createFile(final Project project, VirtualFile file) {
    return new PropertiesFileImpl(project, file);
  }

  public PsiFile createFile(final Project project, String name, CharSequence text) {
    return new PropertiesFileImpl(project, name, text);
  }

  public PsiElement createElement(ASTNode node) {
    final IElementType type = node.getElementType();
    if (type == PropertiesElementTypes.PROPERTY) {
      return new PropertyImpl(node);
    }

    LOG.error("Alien element type [" + type + "]. Can't create Property PsiElement for that.");

    return new ASTWrapperPsiElement(node);
  }
}
