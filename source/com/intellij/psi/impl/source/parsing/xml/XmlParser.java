/*
 * @author max
 */
package com.intellij.psi.impl.source.parsing.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class XmlParser implements PsiParser {

  @NotNull
  public ASTNode parse(final IElementType root, final PsiBuilder builder) {
    builder.enforeCommentTokens(TokenSet.EMPTY);
    final PsiBuilder.Marker file = builder.mark();
    new XmlParsing(builder).parseDocument();
    file.done(root);
    return builder.getTreeBuilt();
  }
}