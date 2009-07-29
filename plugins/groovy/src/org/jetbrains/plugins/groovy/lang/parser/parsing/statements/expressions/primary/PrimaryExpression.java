/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.AssignmentExpression;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeArguments;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class PrimaryExpression implements GroovyElementTypes {

  public static boolean parse(PsiBuilder builder) {

    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, BUILT_IN_TYPE_EXPRESSION);
      return true;
    }
    if (kTHIS.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, THIS_REFERENCE_EXPRESSION);
      return true;
    }
    if (kSUPER.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, SUPER_REFERENCE_EXPRESSION);
      return true;
    }
    if (kNEW.equals(builder.getTokenType())) {
      newExprParse(builder);
      return true;
    }
    if (mIDENT.equals(builder.getTokenType())) {
      ParserUtils.eatElement(builder, REFERENCE_EXPRESSION);
      return true;
    }
    if (mGSTRING_SINGLE_BEGIN.equals(builder.getTokenType())) {
      StringConstructorExpression.parse(builder);
      return true;
    }
    if (mREGEX_BEGIN.equals(builder.getTokenType())) {
      RegexConstructorExpression.parse(builder);
      return true;
    }
    if (mLBRACK.equals(builder.getTokenType())) {
      ListOrMapConstructorExpression.parse(builder);
      return true;
    }
    if (mLPAREN.equals(builder.getTokenType())) {
      return parenthesizedExprParse(builder);
    }
    if (mLCURLY.equals(builder.getTokenType())) {
      OpenOrClosableBlock.parseClosableBlock(builder);
      return true;
    }
    if (TokenSets.CONSTANTS.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, LITERAL);
      return true;
    }
    if (TokenSets.WRONG_CONSTANTS.contains(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      builder.advanceLexer();
      builder.error(GroovyBundle.message("wrong.string"));
      marker.done(LITERAL);
      return true;
    }

    // TODO implement all cases!

    return false;
  }

  public static boolean parenthesizedExprParse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, mLPAREN);
    if (!AssignmentExpression.parse(builder)) {
      marker.rollbackTo();
      return false;
    }
    ParserUtils.getToken(builder, mNLS);
    if (!ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"))) {
      builder.error(GroovyBundle.message("rparen.expected"));
      while (!builder.eof() && !mNLS.equals(builder.getTokenType()) && !mSEMI.equals(builder.getTokenType())
              && !mRPAREN.equals(builder.getTokenType())) {
        builder.error(GroovyBundle.message("rparen.expected"));
        builder.advanceLexer();
      }
      ParserUtils.getToken(builder, mRPAREN);
    }
    marker.done(PARENTHESIZED_EXPRESSION);
    return true;
  }

  /**
   * Parses 'new' expression
   *
   * @param builder
   * @return
   */
  public static GroovyElementType newExprParse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    ParserUtils.getToken(builder, kNEW);
    ParserUtils.getToken(builder, mNLS);
    PsiBuilder.Marker rb = builder.mark();
    TypeArguments.parse(builder);
    if (!TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType()) &&
            !mIDENT.equals(builder.getTokenType())) {
      rb.rollbackTo();
    } else {
      rb.drop();
    }


    if (TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())) {
      ParserUtils.eatElement(builder, BUILT_IN_TYPE);
    } else if (mIDENT.equals(builder.getTokenType())) {
      ReferenceElement.parseReferenceElement(builder);
    } else {
      builder.error(GroovyBundle.message("type.specification.expected"));
      marker.done(NEW_EXPRESSION);
      return NEW_EXPRESSION;
    }

    if (builder.getTokenType() == mLPAREN ||
            ParserUtils.lookAhead(builder, mNLS, mLPAREN)) {

      ParserUtils.getToken(builder, mNLS);
      methodCallArgsParse(builder);
      if (builder.getTokenType() == mLCURLY || ParserUtils.lookAhead(builder, mNLS, mLCURLY)) {
        ParserUtils.getToken(builder, mNLS);
        OpenOrClosableBlock.parseClosableBlock(builder);
      }
    } else if (builder.getTokenType() == mLBRACK) {
      PsiBuilder.Marker forArray = builder.mark();
      while (ParserUtils.getToken(builder, mLBRACK)) {
        ParserUtils.getToken(builder, mNLS);
        AssignmentExpression.parse(builder);
        ParserUtils.getToken(builder, mNLS);
        ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"));
      }
      forArray.done(ARRAY_DECLARATOR);
    } else {
      builder.error(GroovyBundle.message("lparen.expected"));
    }


    marker.done(NEW_EXPRESSION);
    return NEW_EXPRESSION;
  }

  /**
   * Parses method arguments
   *
   * @param builder
   * @return
   */
  public static void methodCallArgsParse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (ParserUtils.getToken(builder, mLPAREN, GroovyBundle.message("lparen.expected"))) {
      ParserUtils.getToken(builder, mNLS);
      ArgumentList.parseArgumentList(builder, mRPAREN);
      ParserUtils.getToken(builder, mNLS);
      ParserUtils.getToken(builder, mRPAREN, GroovyBundle.message("rparen.expected"));
    }

    marker.done(ARGUMENTS);
  }
}