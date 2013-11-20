package com.jetbrains.json;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.psi.StringEscapesTokenTypes.*;
import static com.jetbrains.json.JsonElementTypes.*;

public class JsonSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  @NotNull
  @Override
  public SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
    return new MyHighlighter();
  }

  private static class MyHighlighter extends SyntaxHighlighterBase {
    private static final Map<IElementType, TextAttributesKey> ourAttributes = new HashMap<IElementType, TextAttributesKey>();

    static {
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.BRACES, L_CURLY, R_CURLY);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.BRACKETS, L_BRACKET, R_BRACKET);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.COMMA, COMMA);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.SEMICOLON, COLON);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.STRING, STRING);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.NUMBER, NUMBER);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.KEYWORD, TRUE, FALSE, NULL);
      fillMap(ourAttributes, HighlighterColors.BAD_CHARACTER, TokenType.BAD_CHARACTER);

      // StringLexer's tokens
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE, VALID_STRING_ESCAPE_TOKEN);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE, INVALID_CHARACTER_ESCAPE_TOKEN);
      fillMap(ourAttributes, DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE, INVALID_UNICODE_ESCAPE_TOKEN);
    }

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
//      LayeredLexer layeredLexer = new LayeredLexer(new JsonLexer());
//      StringLiteralLexer stringLexer = new StringLiteralLexer('\"', STRING, false, "/", false, false);
//      layeredLexer.registerSelfStoppingLayer(stringLexer, new IElementType[]{STRING}, IElementType.EMPTY_ARRAY);
      return new JsonLexer();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType type) {
      return pack(ourAttributes.get(type));
    }
  }
}
