/*
 * @author max
 */
package org.jetbrains.jet.lang.parsing;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.JetNodeType;
import org.jetbrains.jet.JetNodeTypes;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lexer.JetLexer;
import org.jetbrains.jet.lexer.JetTokens;

public class JetParserDefinition implements ParserDefinition {
    @NotNull
    public Lexer createLexer(Project project) {
        return new JetLexer();
    }

    public PsiParser createParser(Project project) {
        return new JetParser();
    }

    public IFileElementType getFileNodeType() {
        return JetNodeTypes.JET_FILE;
    }

    @NotNull
    public TokenSet getWhitespaceTokens() {
        return JetTokens.WHITESPACES;
    }

    @NotNull
    public TokenSet getCommentTokens() {
        return JetTokens.COMMENTS;
    }

    @NotNull
    public TokenSet getStringLiteralElements() {
        return JetTokens.STRINGS;
    }

    @NotNull
    public PsiElement createElement(ASTNode astNode) {
        return ((JetNodeType) astNode.getElementType()).createPsi(astNode);
    }

    public PsiFile createFile(FileViewProvider fileViewProvider) {
        return new JetFile(fileViewProvider);
    }

    public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode astNode, ASTNode astNode1) {
        return SpaceRequirements.MAY;
    }
}
