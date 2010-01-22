package com.jetbrains.python.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;

import static com.jetbrains.python.PyBundle.message;

/**
 * @author yole
 */
public class ExpressionParsing extends Parsing {
  private static final Logger LOG = Logger.getInstance("#ru.yole.pythonlanguage.parsing.ExpressionParsing");

  public ExpressionParsing(ParsingContext context) {
    super(context);
  }

  public boolean parsePrimaryExpression(PsiBuilder builder, boolean isTargetExpression) {
    final IElementType firstToken = builder.getTokenType();
    if (firstToken == PyTokenTypes.IDENTIFIER) {
      if (isTargetExpression) {
        buildTokenElement(PyElementTypes.TARGET_EXPRESSION, builder);
      }
      else {
        buildTokenElement(PyElementTypes.REFERENCE_EXPRESSION, builder);
      }
      return true;
    }
    else if (firstToken == PyTokenTypes.INTEGER_LITERAL) {
      buildTokenElement(PyElementTypes.INTEGER_LITERAL_EXPRESSION, builder);
      return true;
    }
    else if (firstToken == PyTokenTypes.FLOAT_LITERAL) {
      buildTokenElement(PyElementTypes.FLOAT_LITERAL_EXPRESSION, builder);
      return true;
    }
    else if (firstToken == PyTokenTypes.IMAGINARY_LITERAL) {
      buildTokenElement(PyElementTypes.IMAGINARY_LITERAL_EXPRESSION, builder);
      return true;
    }
    else if (firstToken == PyTokenTypes.NONE_KEYWORD) {
      buildTokenElement(PyElementTypes.NONE_LITERAL_EXPRESSION, builder);
      return true;
    }
    else if (firstToken == PyTokenTypes.TRUE_KEYWORD || firstToken == PyTokenTypes.FALSE_KEYWORD) {
      buildTokenElement(PyElementTypes.BOOL_LITERAL_EXPRESSION, builder);
      return true;
    }
    else if (firstToken == PyTokenTypes.STRING_LITERAL) {
      final PsiBuilder.Marker marker = builder.mark();
      while (builder.getTokenType() == PyTokenTypes.STRING_LITERAL) {
        builder.advanceLexer();
      }
      marker.done(PyElementTypes.STRING_LITERAL_EXPRESSION);
      return true;
    }
    else if (firstToken == PyTokenTypes.LPAR) {
      parseParenthesizedExpression(builder, isTargetExpression);
      return true;
    }
    else if (firstToken == PyTokenTypes.LBRACKET) {
      parseListLiteralExpression(builder, isTargetExpression);
      return true;
    }
    else if (firstToken == PyTokenTypes.LBRACE) {
      parseDictLiteralExpression(builder);
      return true;
    }
    else if (firstToken == PyTokenTypes.TICK) {
      parseReprExpression(builder);
      return true;
    }
    return false;
  }

  private void parseListLiteralExpression(final PsiBuilder builder, boolean isTargetExpression) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.LBRACKET);
    final PsiBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.RBRACKET) {
      builder.advanceLexer();
      expr.done(PyElementTypes.LIST_LITERAL_EXPRESSION);
      return;
    }
    if (!parseSingleExpression(isTargetExpression)) {
      builder.error(message("PARSE.expected.expression"));
    }
    if (builder.getTokenType() == PyTokenTypes.FOR_KEYWORD) {
      parseListCompExpression(expr, PyTokenTypes.RBRACKET, PyElementTypes.LIST_COMP_EXPRESSION);
    }
    else {
      while (builder.getTokenType() != PyTokenTypes.RBRACKET) {
        if (builder.getTokenType() == PyTokenTypes.COMMA) {
          builder.advanceLexer();
        }
        else if (!parseSingleExpression(isTargetExpression)) {
          builder.error(message("PARSE.expected.expr.or.comma.or.bracket"));
          break;
        }
      }
      checkMatches(PyTokenTypes.RBRACKET, message("PARSE.expected.rbracket"));
      expr.done(PyElementTypes.LIST_LITERAL_EXPRESSION);
    }
  }

  private void parseListCompExpression(PsiBuilder.Marker expr,
                                       final IElementType endToken,
                                       final IElementType exprType) {
    assertCurrentToken(PyTokenTypes.FOR_KEYWORD);
    while (true) {
      myBuilder.advanceLexer();
      parseExpression(true, true);
      checkMatches(PyTokenTypes.IN_KEYWORD, message("PARSE.expected.in"));
      if (!parseTupleExpression(false, false, true)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      while (myBuilder.getTokenType() == PyTokenTypes.IF_KEYWORD) {
        myBuilder.advanceLexer();
        parseOldExpression();
      }
      if (myBuilder.getTokenType() == endToken) {
        myBuilder.advanceLexer();
        break;
      }
      if (myBuilder.getTokenType() == PyTokenTypes.FOR_KEYWORD) {
        expr.done(exprType);
        expr = expr.precede();
        continue;
      }
      myBuilder.error(message("PARSE.expected.for.or.bracket"));
      break;
    }
    expr.done(exprType);
  }

  private void parseDictLiteralExpression(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.LBRACE);
    final PsiBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    while (builder.getTokenType() != PyTokenTypes.RBRACE) {
      if (!parseKeyValueExpression()) {
        break;
      }
      if (builder.getTokenType() != PyTokenTypes.RBRACE) {
        checkMatches(PyTokenTypes.COMMA, message("PARSE.expected.comma"));
      }
    }
    builder.advanceLexer();
    expr.done(PyElementTypes.DICT_LITERAL_EXPRESSION);
  }

  private boolean parseKeyValueExpression() {
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (!parseSingleExpression(false)) {
      marker.drop();
      return false;
    }
    checkMatches(PyTokenTypes.COLON, message("PARSE.expected.colon"));
    if (!parseSingleExpression(false)) {
      marker.drop();
      return false;
    }
    marker.done(PyElementTypes.KEY_VALUE_EXPRESSION);
    return true;
  }

  private void parseParenthesizedExpression(PsiBuilder builder, boolean isTargetExpression) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.LPAR);
    final PsiBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.RPAR) {
      builder.advanceLexer();
      expr.done(PyElementTypes.TUPLE_EXPRESSION);
    }
    else {
      parseYieldOrTupleExpression(builder, isTargetExpression);
      if (builder.getTokenType() == PyTokenTypes.FOR_KEYWORD) {
        parseListCompExpression(expr, PyTokenTypes.RPAR, PyElementTypes.GENERATOR_EXPRESSION);
      }
      else {
        checkMatches(PyTokenTypes.RPAR, message("PARSE.expected.rpar"));
        expr.done(PyElementTypes.PARENTHESIZED_EXPRESSION);
      }
    }
  }

  private void parseReprExpression(PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.TICK);
    final PsiBuilder.Marker expr = builder.mark();
    builder.advanceLexer();
    parseExpression();
    checkMatches(PyTokenTypes.TICK, message("PARSE.expected.tick"));
    expr.done(PyElementTypes.REPR_EXPRESSION);
  }

  public boolean parseMemberExpression(PsiBuilder builder, boolean isTargetExpression) {
    // in sequence a.b.... .c all members but last are always references, and the last may be target.
    boolean recast_first_identifier = false;
    boolean recast_qualifier = false;
    do {
      boolean first_identifier_is_target = isTargetExpression && ! recast_first_identifier;
      PsiBuilder.Marker expr = builder.mark();
      if (!parsePrimaryExpression(builder, first_identifier_is_target)) {
        expr.drop();
        return false;
      }

      while (true) {
        final IElementType tokenType = builder.getTokenType();
        if (tokenType == PyTokenTypes.DOT) {
          if (first_identifier_is_target) {
            recast_first_identifier = true;
            expr.rollbackTo();
            break;
          }
          else recast_first_identifier = false; 
          builder.advanceLexer();
          checkMatches(PyTokenTypes.IDENTIFIER, message("PARSE.expected.name"));
          if (isTargetExpression && ! recast_qualifier && builder.getTokenType() != PyTokenTypes.DOT) {
            expr.done(PyElementTypes.TARGET_EXPRESSION);
          }
          else {
            expr.done(PyElementTypes.REFERENCE_EXPRESSION);
          }
          expr = expr.precede();
        }
        else if (tokenType == PyTokenTypes.LPAR) {
          parseArgumentList();
          expr.done(PyElementTypes.CALL_EXPRESSION);
          expr = expr.precede();
        }
        else if (tokenType == PyTokenTypes.LBRACKET) {
          builder.advanceLexer();
          if (builder.getTokenType() == PyTokenTypes.COLON) {
            PsiBuilder.Marker sliceMarker = builder.mark();
            sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
            parseSliceEnd(builder, expr);
          }
          else {
            parseExpressionOptional();
            if (builder.getTokenType() == PyTokenTypes.COLON) {
              parseSliceEnd(builder, expr);
            }
            else {
              checkMatches(PyTokenTypes.RBRACKET, message("PARSE.expected.rbracket"));
              expr.done(PyElementTypes.SUBSCRIPTION_EXPRESSION);
              if (isTargetExpression && ! recast_qualifier) {
                recast_first_identifier = true; // subscription is always a reference
                recast_qualifier = true; // recast non-first qualifiers too
                expr.rollbackTo();
                break;
              }
            }
          }
          expr = expr.precede();
        }
        else {
          expr.drop();
          break;
        }
        recast_first_identifier = false; // it is true only after a break; normal flow always unsets it.
        // recast_qualifier is untouched, it remembers whether qualifiers were already recast 
      }
    }
    while (recast_first_identifier);

    return true;
  }

  private void parseSliceEnd(PsiBuilder builder, PsiBuilder.Marker expr) {
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.RBRACKET) {
      PsiBuilder.Marker sliceMarker = builder.mark();
      sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
      builder.advanceLexer();
    }
    else {
      if (builder.getTokenType() == PyTokenTypes.COLON) {
        PsiBuilder.Marker sliceMarker = builder.mark();
        sliceMarker.done(PyElementTypes.EMPTY_EXPRESSION);
      }
      else {
        parseExpression();
      }
      if (builder.getTokenType() != PyTokenTypes.RBRACKET && builder.getTokenType() != PyTokenTypes.COLON) {
        builder.error(message("PARSE.expected.colon.or.rbracket"));
      }
      if (builder.getTokenType() == PyTokenTypes.COLON) {
        builder.advanceLexer();
        parseExpressionOptional();
      }
      checkMatches(PyTokenTypes.RBRACKET, message("PARSE.expected.rbracket"));
    }
    expr.done(PyElementTypes.SLICE_EXPRESSION);
  }

  public void parseArgumentList() {
    LOG.assertTrue(myBuilder.getTokenType() == PyTokenTypes.LPAR);
    final PsiBuilder.Marker arglist = myBuilder.mark();
    final PsiBuilder.Marker genexpr = myBuilder.mark();
    myBuilder.advanceLexer();
    int argNumber = 0;
    boolean needBracket = true;
    while (myBuilder.getTokenType() != PyTokenTypes.RPAR) {
      argNumber++;
      if (argNumber > 1) {
        if (argNumber == 2 && myBuilder.getTokenType() == PyTokenTypes.FOR_KEYWORD && genexpr != null) {
          parseListCompExpression(genexpr, PyTokenTypes.RPAR, PyElementTypes.GENERATOR_EXPRESSION);
          needBracket = false;
          break;
        }
        else if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
          myBuilder.advanceLexer();
          if (myBuilder.getTokenType() == PyTokenTypes.RPAR) {
            break;
          }
        }
        else {
          myBuilder.error(message("PARSE.expected.comma.or.rpar"));
          break;
        }
      }
      if (myBuilder.getTokenType() == PyTokenTypes.MULT || myBuilder.getTokenType() == PyTokenTypes.EXP) {
        final PsiBuilder.Marker starArgMarker = myBuilder.mark();
        myBuilder.advanceLexer();
        if (!parseSingleExpression(false)) {
          myBuilder.error(message("PARSE.expected.expression"));
        }
        starArgMarker.done(PyElementTypes.STAR_ARGUMENT_EXPRESSION);
      }
      else {
        if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
          final PsiBuilder.Marker keywordArgMarker = myBuilder.mark();
          myBuilder.advanceLexer();
          if (myBuilder.getTokenType() == PyTokenTypes.EQ) {
            myBuilder.advanceLexer();
            if (!parseSingleExpression(false)) {
              myBuilder.error(message("PARSE.expected.expression"));
            }
            keywordArgMarker.done(PyElementTypes.KEYWORD_ARGUMENT_EXPRESSION);
            continue;
          }
          keywordArgMarker.rollbackTo();
        }
        if (!parseSingleExpression(false)) {
          myBuilder.error(message("PARSE.expected.expression"));
        }
      }
    }

    if (needBracket) {
      if (genexpr != null) {
        genexpr.drop();
      }
      checkMatches(PyTokenTypes.RPAR, message("PARSE.expected.rpar"));
    }
    arglist.done(PyElementTypes.ARGUMENT_LIST);
  }

  public boolean parseExpressionOptional() {
    return parseTupleExpression(false, false, false);
  }

  public boolean parseExpressionOptional(boolean isTargetExpression) {
    return parseTupleExpression(false, isTargetExpression, false);
  }

  public void parseExpression() {
    if (!parseExpressionOptional()) {
      myBuilder.error(message("PARSE.expected.expression"));
    }
  }

  public void parseExpression(boolean stopOnIn, boolean isTargetExpression) {
    if (!parseTupleExpression(stopOnIn, isTargetExpression, false)) {
      myBuilder.error(message("PARSE.expected.expression"));
    }
  }

  public boolean parseYieldOrTupleExpression(final PsiBuilder builder, final boolean isTargetExpression) {
    if (builder.getTokenType() == PyTokenTypes.YIELD_KEYWORD) {
      PsiBuilder.Marker yieldExpr = builder.mark();
      builder.advanceLexer();
      parseTupleExpression(false, isTargetExpression, false);
      yieldExpr.done(PyElementTypes.YIELD_EXPRESSION);
      return true;
    }
    else {
      return parseTupleExpression(false, isTargetExpression, false);
    }
  }

  private boolean parseTupleExpression(boolean stopOnIn, boolean isTargetExpression, final boolean oldTest) {
    PsiBuilder.Marker expr = myBuilder.mark();
    boolean exprParseResult = oldTest ? parseOldTestExpression() : parseTestExpression(stopOnIn, isTargetExpression);
    if (!exprParseResult) {
      expr.drop();
      return false;
    }
    if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
      while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        PsiBuilder.Marker expr2 = myBuilder.mark();
        exprParseResult = oldTest ? parseOldTestExpression() : parseTestExpression(stopOnIn, isTargetExpression);
        if (!exprParseResult) {
          expr2.rollbackTo();
          break;
        }
        expr2.drop();
      }
      expr.done(PyElementTypes.TUPLE_EXPRESSION);
    }
    else {
      expr.drop();
    }
    return true;
  }

  public boolean parseSingleExpression(boolean isTargetExpression) {
    return parseTestExpression(false, isTargetExpression);
  }

  public boolean parseOldExpression() {
    if (myBuilder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
      return parseLambdaExpression(false);
    }
    return parseORTestExpression(myBuilder, false, false);
  }

  private boolean parseTestExpression(boolean stopOnIn, boolean isTargetExpression) {
    if (myBuilder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
      return parseLambdaExpression(false);
    }
    PsiBuilder.Marker condExpr = myBuilder.mark();
    if (!parseORTestExpression(myBuilder, stopOnIn, isTargetExpression)) {
      condExpr.drop();
      return false;
    }
    if (myBuilder.getTokenType() == PyTokenTypes.IF_KEYWORD) {
      myBuilder.advanceLexer();
      if (!parseORTestExpression(myBuilder, stopOnIn, isTargetExpression)) {
        myBuilder.error(message("PARSE.expected.expression"));
      }
      else {
        if (myBuilder.getTokenType() != PyTokenTypes.ELSE_KEYWORD) {
          myBuilder.error(message("PARSE.expected.else"));
        }
        else {
          myBuilder.advanceLexer();
          if (!parseTestExpression(stopOnIn, isTargetExpression)) {
            myBuilder.error(message("PARSE.expected.expression"));
          }
        }
      }
      condExpr.done(PyElementTypes.CONDITIONAL_EXPRESSION);
    }
    else {
      condExpr.drop();
    }
    return true;
  }

  private boolean parseOldTestExpression() {
    if (myBuilder.getTokenType() == PyTokenTypes.LAMBDA_KEYWORD) {
      return parseLambdaExpression(true);
    }
    return parseORTestExpression(myBuilder, false, false);
  }

  private boolean parseLambdaExpression(final boolean oldTest) {
    PsiBuilder.Marker expr = myBuilder.mark();
    myBuilder.advanceLexer();
    getFunctionParser().parseParameterListContents(PyTokenTypes.COLON, false);
    boolean parseExpressionResult = oldTest ? parseOldTestExpression() : parseSingleExpression(false);
    if (!parseExpressionResult) {
      myBuilder.error(message("PARSE.expected.expression"));
    }
    expr.done(PyElementTypes.LAMBDA_EXPRESSION);
    return true;
  }

  private boolean parseORTestExpression(final PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseANDTestExpression(builder, stopOnIn, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (builder.getTokenType() == PyTokenTypes.OR_KEYWORD) {
      builder.advanceLexer();
      if (!parseANDTestExpression(builder, stopOnIn, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseANDTestExpression(final PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseNOTTestExpression(builder, stopOnIn, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (builder.getTokenType() == PyTokenTypes.AND_KEYWORD) {
      builder.advanceLexer();
      if (!parseNOTTestExpression(builder, stopOnIn, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseNOTTestExpression(final PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression) {
    if (builder.getTokenType() == PyTokenTypes.NOT_KEYWORD) {
      final PsiBuilder.Marker expr = builder.mark();
      builder.advanceLexer();
      if (!parseNOTTestExpression(builder, stopOnIn, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.PREFIX_EXPRESSION);
      return true;
    }
    else {
      return parseComparisonExpression(builder, stopOnIn, isTargetExpression);
    }
  }

  private boolean parseComparisonExpression(final PsiBuilder builder, boolean stopOnIn, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseBitwiseORExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    if (stopOnIn && builder.getTokenType() == PyTokenTypes.IN_KEYWORD) {
      expr.drop();
      return true;
    }
    while (PyTokenTypes.COMPARISON_OPERATIONS.contains(builder.getTokenType())) {
      if (builder.getTokenType() == PyTokenTypes.NOT_KEYWORD) {
        PsiBuilder.Marker notMarker = builder.mark();
        builder.advanceLexer();
        if (builder.getTokenType() != PyTokenTypes.IN_KEYWORD) {
          notMarker.rollbackTo();
          break;
        }
        notMarker.drop();
        builder.advanceLexer();
      }
      else if (builder.getTokenType() == PyTokenTypes.IS_KEYWORD) {
        builder.advanceLexer();
        if (builder.getTokenType() == PyTokenTypes.NOT_KEYWORD) {
          builder.advanceLexer();
        }
      }
      else {
        builder.advanceLexer();
      }

      if (!parseBitwiseORExpression(builder, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseBitwiseORExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseBitwiseXORExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (builder.getTokenType() == PyTokenTypes.OR) {
      builder.advanceLexer();
      if (!parseBitwiseXORExpression(builder, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseBitwiseXORExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseBitwiseANDExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (builder.getTokenType() == PyTokenTypes.XOR) {
      builder.advanceLexer();
      if (!parseBitwiseANDExpression(builder, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseBitwiseANDExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseShiftExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (builder.getTokenType() == PyTokenTypes.AND) {
      builder.advanceLexer();
      if (!parseShiftExpression(builder, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseShiftExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseAdditiveExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (PyTokenTypes.SHIFT_OPERATIONS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      if (!parseAdditiveExpression(builder, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseAdditiveExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseMultiplicativeExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }
    while (PyTokenTypes.ADDITIVE_OPERATIONS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      if (!parseMultiplicativeExpression(builder, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseMultiplicativeExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseUnaryExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }

    while (PyTokenTypes.MULTIPLICATIVE_OPERATIONS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      if (!parseUnaryExpression(builder, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
      expr = expr.precede();
    }

    expr.drop();
    return true;
  }

  private boolean parseUnaryExpression(final PsiBuilder builder, boolean isTargetExpression) {
    final IElementType tokenType = builder.getTokenType();
    if (PyTokenTypes.UNARY_OPERATIONS.contains(tokenType)) {
      final PsiBuilder.Marker expr = builder.mark();
      builder.advanceLexer();
      if (!parseUnaryExpression(builder, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.PREFIX_EXPRESSION);
      return true;
    }
    else {
      return parsePowerExpression(builder, isTargetExpression);
    }
  }

  private boolean parsePowerExpression(final PsiBuilder builder, boolean isTargetExpression) {
    PsiBuilder.Marker expr = builder.mark();
    if (!parseMemberExpression(builder, isTargetExpression)) {
      expr.drop();
      return false;
    }

    if (builder.getTokenType() == PyTokenTypes.EXP) {
      builder.advanceLexer();
      if (!parseUnaryExpression(builder, isTargetExpression)) {
        builder.error(message("PARSE.expected.expression"));
      }
      expr.done(PyElementTypes.BINARY_EXPRESSION);
    }
    else {
      expr.drop();
    }

    return true;
  }

  private static void buildTokenElement(IElementType type, PsiBuilder builder) {
    final PsiBuilder.Marker marker = builder.mark();
    builder.advanceLexer();
    marker.done(type);
  }
}
