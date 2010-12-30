package org.jetbrains.jet.lang.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.jet.JetNodeType;
import org.jetbrains.jet.lexer.JetTokens;

import static org.jetbrains.jet.JetNodeTypes.*;
import static org.jetbrains.jet.lexer.JetTokens.*;

/**
 * @author abreslav
 */
public class JetExpressionParsing extends AbstractJetParsing {
    private final JetParsing myJetParsing;

    public JetExpressionParsing(SemanticWhitespaceAwarePsiBuilder builder, JetParsing jetParsing) {
        super(builder);
        myJetParsing = jetParsing;
    }

    @SuppressWarnings({"UnusedDeclaration"})
    private enum Precedence {
        MEMBER_ACCESS(DOT, HASH, SAFE_ACCESS) {
            @Override
            public void parseHigherPrecedence(JetExpressionParsing parsing) {
                parsing.parseAtomicExpression();
            }
        },

        POSTFIX(PLUSPLUS, MINUSMINUS), // valueArguments  : arrayAccess

        PREFIX(MINUS, PLUS, MINUSMINUS, PLUSPLUS, EXCL) { // attributes

            @Override
            public void parseHigherPrecedence(JetExpressionParsing parsing) {
                throw new IllegalStateException("Don't call this method");
            }
        },

        MULTIPLICATIVE(MUL, DIV, PERC) {
            @Override
            public void parseHigherPrecedence(JetExpressionParsing parsing) {
                parsing.parsePrefixExpression();
            }
        },

        ADDITIVE(PLUS, MINUS),
        RANGE(JetTokens.RANGE),
        SIMPLE_NAME(IDENTIFIER),
        ELVIS(JetTokens.ELVIS),
        NAMED_INFIX_OR_TYPE(IN_KEYWORD, NOT_IN, IS_KEYWORD, NOT_IS, AS_KEYWORD, COLON),
        COMPARISON(LT, GT, LTEQ, GTEQ),
        EQUALITY(EQEQ, EXCLEQ, EQEQEQ, EXCLEQEQEQ),
        CONJUNCTION(ANDAND),
        DISJUNCTION(OROR),
        MATCH(MATCH_KEYWORD),
        // TODO: don't build a binary tree, build a tuple
        ASSIGNMENT(EQ, PLUSEQ, MINUSEQ, MULTEQ, DIVEQ, PERCEQ),
        ARROW(JetTokens.ARROW),
        ;

        static {
            Precedence[] values = Precedence.values();
            for (Precedence precedence : values) {
                int ordinal = precedence.ordinal();
                precedence.higher = ordinal > 0 ? values[ordinal - 1] : null;
            }
        }

        private Precedence higher;
        private final TokenSet operations;

        Precedence(IElementType... operations) {
            this.operations = TokenSet.create(operations);
        }

        public void parseHigherPrecedence(JetExpressionParsing parsing) {
            assert higher != null;
            parsing.parseBinaryExpression(higher);
        }

        public final TokenSet getOperations() {
            return operations;
        }
    }

    /*
     * expression
     *   : attributes expression
     *   : "(" expression ")" // see tupleLiteral
     *   : literalConstant
     *   : functionLiteral
     *   : tupleLiteral
     *   : "null"
     *   : "this" ("<" type ">")?
     *   : expressionWithPrecedences
     *   : if
     *   : try
     *   : "typeof" "(" expression ")"
     *   : "new" constructorInvocation
     *   : objectLiteral
     *   : declaration
     *   : jump
     *   : loop
     *   // block is syntactically equivalent to a functionLiteral with no parameters
     *   ;
     */
    public void parseExpression() {
        parseBinaryExpression(Precedence.ASSIGNMENT);
    }

    /*
     * expression (operation expression)*
     *
     * see the precedence table
     */
    private void parseBinaryExpression(Precedence precedence) {
//        System.out.println(precedence.name() + " at " + tt());
        PsiBuilder.Marker expression = mark();

        precedence.parseHigherPrecedence(this);

        while (!myBuilder.eolInLastWhitespace() && atSet(precedence.getOperations())) {
             advance(); // operation
             precedence.parseHigherPrecedence(this);
             expression.done(BINARY_EXPRESSION);
             expression = expression.precede();
        }

        expression.drop();
    }

    /*
     * operation? expression
     */
    private void parsePrefixExpression() {
//        System.out.println("pre at " + tt());
        if (at(LBRACKET)) {
            if (!parseLocalDeclaration()) {
                PsiBuilder.Marker attributes = mark();
                myJetParsing.parseAttributeList();
                parsePostfixExpression();
                attributes.done(ANNOTATED_EXPRESSION);
            }
        } else if (atSet(Precedence.PREFIX.getOperations())) {
            PsiBuilder.Marker expression = mark();
            advance(); // operation
            parsePostfixExpression();
            expression.done(PREFIX_EXPRESSION);
        } else {
            parsePostfixExpression();
        }
    }

    /*
     * expression operation?
     */
    private void parsePostfixExpression() {
//        System.out.println("post at " + tt());

        PsiBuilder.Marker expression = mark();
        parseBinaryExpression(Precedence.MEMBER_ACCESS);
        if (myBuilder.eolInLastWhitespace()) {
            expression.drop();
        } else if (at(LBRACKET)) {
            parseArrayAccess();
            expression.done(ARRAY_ACCESS_EXPRESSION);
        } else if (atSet(Precedence.POSTFIX.getOperations())) {
            advance(); // operation
            expression.done(POSTFIX_EXPRESSION);
        } else if (at(LPAR)) {
            parseValueArgumentList();
            expression.done(CALL_EXPRESSION);
        } else {
            expression.drop();
        }
    }

    /*
     * atomicExpression
     *   : tupleLiteral // or parenthesized expression
     *   : "this" ("<" type ">")?
     *   : "typeof" "(" expression ")"
     *   : "new" constructorInvocation
     *   : objectLiteral
     *   : jump
     *   : if
     *   : try
     *   : loop
     *   : literalConstant
     *   : functionLiteral
     *   : declaration
     *   : SimpleName
     *   ;
     */
    private void parseAtomicExpression() {
//        System.out.println("atom at " + tt());


        if (at(LPAR)) {
            parseParenthesizedExpressionOrTuple();
        }
        else if (at(THIS_KEYWORD)) {
            parseThisExpression();
        }
        else if (at(TYPEOF_KEYWORD)) {
            parseTypeOf();
        }
        else if (at(NEW_KEYWORD)) {
            parseNew();
        }
        else if (at(OBJECT_KEYWORD)) {
            parseObjectLiteral();
        }
        else if (at(THROW_KEYWORD)) {
            parseThrow();
        }
        else if (at(RETURN_KEYWORD)) {
            parseReturn();
        }
        else if (at(CONTINUE_KEYWORD)) {
            parseJump(CONTINUE);
        }
        else if (at(BREAK_KEYWORD)) {
            parseJump(BREAK);
        }
        else if (at(IF_KEYWORD)) {
            parseIf();
        }
        else if (at(TRY_KEYWORD)) {
            parseTry();
        }
        else if (at(FOR_KEYWORD)) {
            parseFor();
        }
        else if (at(WHILE_KEYWORD)) {
            parseWhile();
        }
        else if (at(DO_KEYWORD)) {
            parseDoWhile();
        }
        else if (at(INTEGER_LITERAL)) {
            parseOneTokenExpression(INTEGER_CONSTANT);
        }
        else if (at(LONG_LITERAL)) {
            parseOneTokenExpression(LONG_CONSTANT);
        }
        else if (at(CHARACTER_LITERAL)) {
            parseOneTokenExpression(CHARACTER_CONSTANT);
        }
        else if (at(FLOAT_LITERAL)) {
            parseOneTokenExpression(FLOAT_CONSTANT);
        }
        else if (at(STRING_LITERAL) || at(RAW_STRING_LITERAL)) {
            parseOneTokenExpression(STRING_CONSTANT);
        }
        else if (at(TRUE_KEYWORD)) {
            parseOneTokenExpression(BOOLEAN_CONSTANT);
        }
        else if (at(FALSE_KEYWORD)) {
            parseOneTokenExpression(BOOLEAN_CONSTANT);
        }
        else if (at(NULL_KEYWORD)) {
            parseOneTokenExpression(NULL);
        }
        else if (atSet(CLASS_KEYWORD, EXTENSION_KEYWORD, FUN_KEYWORD, VAL_KEYWORD,
                VAR_KEYWORD, TYPE_KEYWORD, DECOMPOSER_KEYWORD)) {
            parseLocalDeclaration();
        }
        else if (at(IDENTIFIER)) {
            if (JetParsing.MODIFIER_KEYWORD_MAP.containsKey(myBuilder.getTokenText())) {
                if (!parseLocalDeclaration()) {
                    parseSimpleName();
                }
            } else {
                parseSimpleName();
            }
        }
        else if (at(LBRACE)) {
            parseFunctionLiteral();
        }
        else {
            // TODO
            errorAndAdvance("Expecting an expression");
        }
    }


    /*
     * arrayAccess
     *   : "[" expression{","} "]"
     *   ;
     */
    private void parseArrayAccess() {
        assert at(LBRACKET);

        PsiBuilder.Marker indices = mark();

        advance(); // LBRACKET

        while (true) {
            if (at(COMMA)) errorAndAdvance("Expecting an index expression");
            if (at(RBRACKET)) {
                error("Expecting an index expression");
                break;
            }
            parseExpression();
            if (!at(COMMA)) break;
            advance(); // COMMA
        }

        expect(RBRACKET, "Expecting ']'");

        indices.done(INDICES);
    }

    /*
     * SimpleName
     */
    private void parseSimpleName() {
        assert at(IDENTIFIER);
//        PsiBuilder.Marker simpleName = mark();
        advance(); // IDENTIFIER
//        simpleName.done(SIMPLE_NAME);
    }

    /*
     * modifiers declarationRest
     */
    private boolean parseLocalDeclaration() {
        PsiBuilder.Marker decls = mark();
        JetParsing.EnumDetector enumDetector = new JetParsing.EnumDetector();
        myJetParsing.parseModifierList(enumDetector);

        JetNodeType declType = parseLocalDeclarationRest(enumDetector.isEnum());

        if (declType != null) {
            decls.done(declType);
            return true;
        } else {
            decls.rollbackTo();
            return false;
        }
    }

    /*
     * functionLiteral  // one can use "it" as a parameter name
     *   : "{" expressions "}"
     *   : "{" (type ".")? modifiers SimpleName "=>" expressions "}"
     *   : "{" (type ".")? "(" (modifiers SimpleName (":" type)?){","} ")" (":" type)? "=>" expressions "}"
     *   ;
     */
    private void parseFunctionLiteral() {
        assert at(LBRACE);

        PsiBuilder.Marker literal = mark();

        advance(); // LBRACE

        int doubleArrowPos = matchTokenStreamPredicate(new FirstBefore(new At(DOUBLE_ARROW), new At(RBRACE)) {
            @Override
            public boolean isTopLevel(int openAngleBrackets, int openBrackets, int openBraces, int openParentheses) {
                return openBraces == 0;
            }
        });

        if (doubleArrowPos >= 0) {
            boolean dontExpectParameters = false;

            int lastDot = matchTokenStreamPredicate(new LastBefore(new At(DOT), new AtOffset(doubleArrowPos)));
            if (lastDot >= 0) { // There is a receiver type
                PsiBuilder.Marker receiverType = mark();
                createTruncatedBuilder(lastDot).parseTypeRef();
                receiverType.done(RECEIVER_TYPE);
                assert at(DOT);
                advance(); // DOT;

                if (!at(LPAR)) {
                    int firstLParPos = matchTokenStreamPredicate(new FirstBefore(new At(LPAR), new AtOffset(doubleArrowPos)));

                    if (firstLParPos >= 0) {
                        errorUntilOffset("Expecting '('", firstLParPos);
                    } else {
                        errorUntilOffset("To specify a receiver type, use the full notation: {ReceiverType.(parameters) [: ReturnType] => ...}",
                            doubleArrowPos);
                        dontExpectParameters = true;
                    }
                }

            }

            if (at(LPAR)) {
                parseFunctionLiteralParameterList();

                if (at(COLON)) {
                    advance(); // COLON
                    if (at(DOUBLE_ARROW)) {
                        error("Expecting a type");
                    } else {
                        myJetParsing.parseTypeRef();
                    }
                }
            } else if (!dontExpectParameters) {
                PsiBuilder.Marker parameter = mark();
                int parameterNamePos = matchTokenStreamPredicate(new LastBefore(new At(IDENTIFIER), new AtOffset(doubleArrowPos)));

                createTruncatedBuilder(parameterNamePos).parseModifierList();

                expect(IDENTIFIER, "Expecting parameter name", TokenSet.create(DOUBLE_ARROW));

                parameter.done(VALUE_PARAMETER);

                if (at(COLON)) {
                    errorUntilOffset("To specify a type of a parameter or a return type, use the full notation: {(parameter : Type) : ReturnType => ...}", doubleArrowPos);
                } else if (at(COMMA)) {
                    errorUntilOffset("To specify many parameters, use the full notation: {(p1, p2, ...) => ...}", doubleArrowPos);
                } else if (!at(DOUBLE_ARROW)) {
                    errorUntilOffset("Expecting '=>'", doubleArrowPos);
                }

            }

            expectNoAdvance(DOUBLE_ARROW, "Expecting '=>'");
        }

        PsiBuilder.Marker body = mark();
        parseExpressions();
        body.done(BODY);

        expect(RBRACE, "Expecting '}'");

        literal.done(FUNCTION_LITERAL);
    }

    /*
     * "(" (modifiers SimpleName (":" type)?){","} ")"
     */
    private void parseFunctionLiteralParameterList() {
        PsiBuilder.Marker list = mark();
        expect(LPAR, "Expecting a parameter list in parentheses (...)", TokenSet.create(DOUBLE_ARROW, COLON));

        if (!at(RPAR)) {
            while (true) {
                if (at(COMMA)) errorAndAdvance("Expecting a parameter declaration");

                PsiBuilder.Marker parameter = mark();
                int parameterNamePos = matchTokenStreamPredicate(new LastBefore(new At(IDENTIFIER), new AtSet(COMMA, RPAR, COLON, DOUBLE_ARROW)));
                createTruncatedBuilder(parameterNamePos).parseModifierList();

                expect(IDENTIFIER, "Expecting parameter declaration");

                if (at(COLON)) {
                    advance(); // COLON
                    myJetParsing.parseTypeRef();
                }
                parameter.done(VALUE_PARAMETER);
                if (!at(COMMA)) break;
                advance(); // COMMA

                if (at(RPAR)) {
                    error("Expecting a parameter declaration");
                    break;
                }
            }
        }

        expect(RPAR, "Expecting ')", TokenSet.create(DOUBLE_ARROW, COLON));
        list.done(VALUE_PARAMETER_LIST);
    }

    /*
     * expressions
     *   : expression{SEMI} SEMI?
     */
    public void parseExpressions() {
        while (!eof() && !at(RBRACE)) {
            parseExpression();
            if (at(SEMICOLON)) {
                advance(); // SEMICOLON
            } else if (at(RBRACE)) {
                break;
            } else if (!myBuilder.eolInLastWhitespace()) {
                errorUntil("Unexpected tokens (use ';' to separate expressions on the same line", TokenSet.create(EOL_OR_SEMICOLON));
            }
        }
    }

    /*
     * declaration
     *   : function
     *   : property
     *   : extension
     *   : class
     *   : typedef
     *   ;
     */
    private JetNodeType parseLocalDeclarationRest(boolean isEnum) {
         IElementType keywordToken = tt();
         JetNodeType declType = null;
         if (keywordToken == CLASS_KEYWORD) {
             declType = myJetParsing.parseClass(isEnum);
         }
         else if (keywordToken == EXTENSION_KEYWORD) {
             declType = myJetParsing.parseExtension();
         }
         else if (keywordToken == FUN_KEYWORD) {
             declType = myJetParsing.parseFunction();
         }
         else if (keywordToken == VAL_KEYWORD || keywordToken == VAR_KEYWORD) {
             declType = myJetParsing.parseProperty();
         }
         else if (keywordToken == TYPE_KEYWORD) {
             declType = myJetParsing.parseTypeDef();
         }
         else if (keywordToken == DECOMPOSER_KEYWORD) {
             declType = myJetParsing.parseDecomposer();
         }
         return declType;
     }

    /*
     * doWhile
     *   : "do" expression "while" "(" expression ")"
     *   ;
     */
    private void parseDoWhile() {
        assert at(DO_KEYWORD);

        PsiBuilder.Marker loop = mark();

        advance(); // DO_KEYWORD

        parseControlStructureBody();

        expect(WHILE_KEYWORD, "Expecting 'while' followed by a post-condition");

        parseCondition();

        loop.done(DO_WHILE);
    }

    /*
     * while
     *   : "while" "(" expression ")" expression
     *   ;
     */
    private void parseWhile() {
        assert at(WHILE_KEYWORD);

        PsiBuilder.Marker loop = mark();

        advance(); // WHILE_KEYWORD

        parseCondition();

        parseControlStructureBody();

        loop.done(WHILE);
    }

    /*
     * for
     *   : "for" "(" attributes valOrVar? SimpleName (":" type)? "in" expression ")" expression
     *   ;
     */
    private void parseFor() {
        assert at(FOR_KEYWORD);

        PsiBuilder.Marker loop = mark();

        advance(); // FOR_KEYWORD

        expect(LPAR, "Expecting '(' to open a loop range", TokenSet.create(RPAR, VAL_KEYWORD, VAR_KEYWORD, IDENTIFIER));

        PsiBuilder.Marker parameter = mark();
        if (at(VAL_KEYWORD) || at(VAR_KEYWORD)) advance(); // VAL_KEYWORD or VAR_KEYWORD
        expect(IDENTIFIER, "Expecting a variable name", TokenSet.create(COLON));
        if (at(COLON)) {
            advance(); // COLON
            myJetParsing.parseTypeRef();
        }
        parameter.done(LOOP_PARAMETER);

        expect(IN_KEYWORD, "Expecting 'in'");

        PsiBuilder.Marker range = mark();
        parseExpression();
        range.done(LOOP_RANGE);

        expectNoAdvance(RPAR, "Expecting ')'");

        parseControlStructureBody();

        loop.done(FOR);
    }

    /*
     * expression
     */
    private void parseControlStructureBody() {
        PsiBuilder.Marker body = mark();
        parseExpression();
        body.done(BODY);

        // TODO: empty body?
    }

    /*
     * try
     *   : "try" block catchBlock* finallyBlock?
     *   ;
     * catchBlock
     *   : "catch" "(" attributes SimpleName ":" userType ")" block
     *   ;
     *
     * finallyBlock
     *   : "finally" block
     *   ;
     */
    private void parseTry() {
        assert at(TRY_KEYWORD);

        PsiBuilder.Marker tryExpression = mark();

        advance(); // TRY_KEYWORD

        myJetParsing.parseBlock();

        while (at(CATCH_KEYWORD)) {
            PsiBuilder.Marker catchBlock = mark();
            advance(); // CATCH_KEYWORD

            myJetParsing.parseValueParameterList(false, TokenSet.create(LBRACE, FINALLY_KEYWORD, CATCH_KEYWORD));

            myJetParsing.parseBlock();
            catchBlock.done(CATCH);
        }

        if (at(FINALLY_KEYWORD)) {
            PsiBuilder.Marker finallyBlock = mark();

            advance(); // FINALLY_KEYWORD

            myJetParsing.parseBlock();

            finallyBlock.done(FINALLY);
        }

        tryExpression.done(TRY);
    }

    /*
     * if
     *   : "if" "(" expression ")" expression ("else" expression)?
     *   ;
     */
    private void parseIf() {
        assert at(IF_KEYWORD);

        PsiBuilder.Marker marker = mark();

        advance(); //IF_KEYWORD

        parseCondition();

        // TODO: empty body?
        PsiBuilder.Marker thenBranch = mark();
        parseExpression();
        thenBranch.done(THEN);

        if (at(ELSE_KEYWORD)) {
            advance(); // ELSE_KEYWORD

            PsiBuilder.Marker elseBranch = mark();
            parseExpression();
            elseBranch.done(ELSE);
        }

        marker.done(IF);
    }

    /*
     * "(" expression ")"
     */
    private void parseCondition() {
        expect(LPAR, "Expecting a condition in parentheses '(...)'");

        PsiBuilder.Marker condition = mark();
        parseExpression();
        condition.done(CONDITION);

        expect(RPAR, "Expecting ')");
    }

    /*
     * : "continue" SimpleName
     * : "break" SimpleName
     */
    private void parseJump(JetNodeType type) {
        assert at(BREAK_KEYWORD) || at(CONTINUE_KEYWORD);

        PsiBuilder.Marker marker = mark();

        advance(); // BREAK_KEYWORD or CONTINUE_KEYWORD

        if (!eol() && at(IDENTIFIER)) advance(); // IDENTIFIER

        marker.done(type);
    }

    /*
     * "return" expression?
     */
    private void parseReturn() {
        assert at(RETURN_KEYWORD);

        PsiBuilder.Marker returnExpression = mark();

        advance(); // RETURN_KEYWORD

        if (!at(EOL_OR_SEMICOLON)) parseExpression();

        returnExpression.done(RETURN);
    }

    /*
     * : "throw" expression
     */
    private void parseThrow() {
        assert at(THROW_KEYWORD);

        PsiBuilder.Marker marker = mark();

        advance(); // THROW_KEYWORD

        parseExpression();

        marker.done(THROW);
    }

    /*
     * "new" constructorInvocation // identical to new nunctionCall
     *
     * constructorInvocation
     *   : userType valueArguments?
     */
    private void parseNew() {
        assert at(NEW_KEYWORD);

        PsiBuilder.Marker creation = mark();
        advance(); // NEW_KEYWORD

        myJetParsing.parseTypeRef();

        if (!eol() && at(LPAR)) {
            parseValueArgumentList();
        }
        creation.done(NEW);
    }

    /*
     * "typeof" "(" expression ")"
     */
    private void parseTypeOf() {
        assert at(TYPEOF_KEYWORD);

        PsiBuilder.Marker typeof = mark();
        advance(); // TYPEOF_KEYWORD

        expect(LPAR, "Expecting '('");
        parseExpression();
        expect(RPAR, "Expecting ')'");
        typeof.done(TYPEOF);
    }

    /*
     * mapLiteralEntry
     *   : expression ":" expression
     *   ;
     */
    private void parseMapLiteralEntry() {
        PsiBuilder.Marker entry = mark();
        parseExpression();
        expect(COLON, "Expecting ':'");
        parseExpression();
        entry.done(MAP_LITERAL_ENTRY);
    }

    /*
     * "(" expression ")" // see tupleLiteral
     * "(" expression{","} ")"
     * TODO: Labels in tuple literals?
     */
    private void parseParenthesizedExpressionOrTuple() {
        assert at(LPAR);

        PsiBuilder.Marker mark = mark();

        advance(); // LPAR

        while (true) {
            if (at(COMMA)) errorAndAdvance("Expecting a tuple entry (expression)");
            parseExpression();
            if (!at(COMMA)) break;
            advance(); // COMMA
        }

        expect(RPAR, "Expecting ')'");

        mark.done(TUPLE);
    }

    /*
     * "this" ("<" type ">")?
     */
    private void parseThisExpression() {
        assert at(THIS_KEYWORD);
        advance(); // THIS_KEYWORD
        if (at(LT)) {
            // This may be "this < foo" or "this<foo>", thus the backtracking
            PsiBuilder.Marker supertype = mark();

            advance(); // LT

            myJetParsing.parseTypeRef();

            if (at(GT)) {
                advance(); // GT
                supertype.done(SUPERTYE_QUALIFIER);
            }
            else {
                supertype.rollbackTo();
            }
        }
    }

    /*
     * valueArguments
     *   : "(" (SimpleName "=")? ("out" | "ref")? expression{","} ")"
     *   ;
     */
    public void parseValueArgumentList() {
        PsiBuilder.Marker list = mark();

        expect(LPAR, "Expecting a parameter list", TokenSet.create(RPAR));

        if (!at(RPAR)) {
            while (true) {
                parseValueArgument();
                if (!at(COMMA)) break;
                advance(); // COMMA
            }
        }

        expect(RPAR, "Expecting ')'");

        list.done(VALUE_ARGUMENT_LIST);
    }

    /*
     * (SimpleName "=")? ("out" | "ref")? expression
     */
    private void parseValueArgument() {
        PsiBuilder.Marker argument = mark();
        JetNodeType type = VALUE_ARGUMENT;
        if (at(IDENTIFIER) && lookahead(1) == EQ) {
            advance(); // IDENTIFIER
            advance(); // EQ
            type = NAMED_ARGUMENT;
        }
        if (at(OUT_KEYWORD) || at(REF_KEYWORD)) advance(); // REF or OUT
        parseExpression();
        argument.done(type);
    }

    /*
     * objectLiteral
     *   : "object" delegationSpecifier{","}? classBody // Cannot make class body optional: foo(object F, a)
     *   ;
     */
    public void parseObjectLiteral() {
        assert at(OBJECT_KEYWORD);

        advance(); // OBJECT_KEYWORD

        if (at(LBRACE)) {
            myJetParsing.parseClassBody();
        }
        else {
            myJetParsing.parseDelegationSpecifierList();
            if (at(LBRACE)) {
                myJetParsing.parseClassBody();
            }
        }
    }

    private void parseOneTokenExpression(JetNodeType type) {
        PsiBuilder.Marker mark = mark();
        advance();
        mark.done(type);
    }

}
