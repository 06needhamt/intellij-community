package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.List;

public class EquivalenceChecker{
    private EquivalenceChecker(){
        super();
    }

    private static final int THIS_EXPRESSION = 0;
    private static final int LITERAL_EXPRESSION = 1;
    private static final int CLASS_OBJECT_EXPRESSION = 2;
    private static final int REFERENCE_EXPRESSION = 3;
    private static final int SUPER_EXPRESSION = 4;
    private static final int METHOD_CALL_EXPRESSION = 5;
    private static final int NEW_EXPRESSION = 6;
    private static final int ARRAY_INITIALIZER_EXPRESSION = 7;
    private static final int TYPECAST_EXPRESSION = 8;
    private static final int ARRAY_ACCESS_EXPRESSION = 9;
    private static final int PREFIX_EXPRESSION = 10;
    private static final int POSTFIX_EXPRESSION = 11;
    private static final int BINARY_EXPRESSION = 12;
    private static final int CONDITIONAL_EXPRESSION = 13;
    private static final int ASSIGNMENT_EXPRESSION = 14;

    private static final int ASSERT_STATEMENT = 0;
    private static final int BLOCK_STATEMENT = 1;
    private static final int BREAK_STATEMENT = 2;
    private static final int CONTINUE_STATEMENT = 3;
    private static final int DECLARATION_STATEMENT = 4;
    private static final int DO_WHILE_STATEMENT = 5;
    private static final int EMPTY_STATEMENT = 6;
    private static final int EXPRESSION_LIST_STATEMENT = 7;
    private static final int EXPRESSION_STATEMENT = 8;
    private static final int FOR_STATEMENT = 9;
    private static final int IF_STATEMENT = 10;
    private static final int LABELED_STATEMENT = 11;
    private static final int RETURN_STATEMENT = 12;
    private static final int SWITCH_LABEL_STATEMENT = 13;
    private static final int SWITCH_STATEMENT = 14;
    private static final int SYNCHRONIZED_STATEMENT = 15;
    private static final int THROW_STATEMENT = 16;
    private static final int TRY_STATEMENT = 17;
    private static final int WHILE_STATEMENT = 18;

    public static boolean statementsAreEquivalent(PsiStatement exp1,
                                                  PsiStatement exp2){
        if(exp1 == null && exp2 == null){
            return true;
        }
        if(exp1 == null || exp2 == null){
            return false;
        }
        final int type1 = getStatementType(exp1);
        final int type2 = getStatementType(exp2);
        if(type1 != type2){
            return false;
        }
        switch(type1){
        case ASSERT_STATEMENT:
               return assertStatementsAreEquivalent((PsiAssertStatement) exp1,
                                                    (PsiAssertStatement) exp2);
        case BLOCK_STATEMENT:
               return blockStatementsAreEquivalent((PsiBlockStatement) exp1,
                                                   (PsiBlockStatement) exp2);
        case BREAK_STATEMENT:
               return breakStatementsAreEquivalent((PsiBreakStatement) exp1,
                                                   (PsiBreakStatement) exp2);
        case CONTINUE_STATEMENT:
               return continueStatementsAreEquivalent((PsiContinueStatement) exp1,
                                                      (PsiContinueStatement) exp2);
        case DECLARATION_STATEMENT:
               return declarationStatementsAreEquivalent((PsiDeclarationStatement) exp1,
                                                         (PsiDeclarationStatement) exp2);
        case DO_WHILE_STATEMENT:
               return doWhileStatementsAreEquivalent((PsiDoWhileStatement) exp1,
                                                     (PsiDoWhileStatement) exp2);
        case EMPTY_STATEMENT:
               return true;
        case EXPRESSION_LIST_STATEMENT:
               return expressionListStatementsAreEquivalent((PsiExpressionListStatement) exp1,
                                                            (PsiExpressionListStatement) exp2);
        case EXPRESSION_STATEMENT:
               return expressionStatementsAreEquivalent((PsiExpressionStatement) exp1,
                                                        (PsiExpressionStatement) exp2);
        case FOR_STATEMENT:
               return forStatementsAreEquivalent((PsiForStatement) exp1,
                                                 (PsiForStatement) exp2);
        case IF_STATEMENT:
               return ifStatementsAreEquivalent((PsiIfStatement) exp1,
                                                (PsiIfStatement) exp2);
        case LABELED_STATEMENT:
               return labeledStatementsAreEquivalent((PsiLabeledStatement) exp1,
                                                     (PsiLabeledStatement) exp2);
        case RETURN_STATEMENT:
               return returnStatementsAreEquivalent((PsiReturnStatement) exp1,
                                                    (PsiReturnStatement) exp2);
        case SWITCH_LABEL_STATEMENT:
               return switchLabelStatementsAreEquivalent((PsiSwitchLabelStatement) exp1,
                                                         (PsiSwitchLabelStatement) exp2);
        case SWITCH_STATEMENT:
               return switchStatementsAreEquivalent((PsiSwitchStatement) exp1,
                                                    (PsiSwitchStatement) exp2);
        case SYNCHRONIZED_STATEMENT:
               return synchronizedStatementsAreEquivalent((PsiSynchronizedStatement) exp1,
                                                          (PsiSynchronizedStatement) exp2);
        case THROW_STATEMENT:
               return throwStatementsAreEquivalent((PsiThrowStatement) exp1,
                                                   (PsiThrowStatement) exp2);
        case TRY_STATEMENT:
               return tryStatementsAreEquivalent((PsiTryStatement) exp1,
                                                 (PsiTryStatement) exp2);
        case WHILE_STATEMENT:
               return whileStatementsAreEquivalent((PsiWhileStatement) exp1,
                                                   (PsiWhileStatement) exp2);
        default:
               return false;
        }
    }

    private static boolean declarationStatementsAreEquivalent(PsiDeclarationStatement statement1,
                                                              PsiDeclarationStatement statement2){
        final PsiElement[] elements1 = statement1.getDeclaredElements();
        final List vars1 = new ArrayList(elements1.length);
        for(int i = 0; i < elements1.length; i++){
            if(elements1[i] instanceof PsiLocalVariable){
                vars1.add(elements1[i]);
            }
        }
        final PsiElement[] elements2 = statement2.getDeclaredElements();
        final List vars2 = new ArrayList(elements2.length);
        for(int i = 0; i < elements2.length; i++){
            if(elements2[i] instanceof PsiLocalVariable){
                vars2.add(elements2[i]);
            }
        }
        if(vars1.size() != vars2.size()){
            return false;
        }
        for(int i = 0; i < vars1.size(); i++){
            final PsiLocalVariable var1 = (PsiLocalVariable) vars1.get(i);
            final PsiLocalVariable var2 = (PsiLocalVariable) vars2.get(i);
            if(localVariableAreEquivalent(var1, var2)){
                return false;
            }
        }
        return true;
    }

    private static boolean localVariableAreEquivalent(PsiLocalVariable var1,
                                                      PsiLocalVariable var2){
        final PsiType type1 = var1.getType();
        final PsiType type2 = var2.getType();
        if(!typesAreEquivalent(type1, type2)){
            return false;
        }
        final String name1 = var1.getName();
        final String name2 = var2.getName();
        if(name1 == null){
            return name2 == null;
        }
        return name1.equals(name2);
    }

    private static boolean tryStatementsAreEquivalent(PsiTryStatement statement1,
                                                      PsiTryStatement statement2){
        final PsiCodeBlock tryBlock1 = statement1.getTryBlock();
        final PsiCodeBlock tryBlock2 = statement2.getTryBlock();
        if(!codeBlocksAreEquivalent(tryBlock1, tryBlock2)){
            return false;
        }
        final PsiCodeBlock finallyBlock1 = statement1.getFinallyBlock();
        final PsiCodeBlock finallyBlock2 = statement2.getFinallyBlock();
        if(!codeBlocksAreEquivalent(finallyBlock1, finallyBlock2)){
            return false;
        }
        final PsiCodeBlock[] catchBlocks1 = statement1.getCatchBlocks();
        final PsiCodeBlock[] catchBlocks2 = statement2.getCatchBlocks();
        if(catchBlocks1.length != catchBlocks2.length){
            return false;
        }
        for(int i = 0; i < catchBlocks2.length; i++){
            if(!codeBlocksAreEquivalent(catchBlocks1[i], catchBlocks2[i])){
                return false;
            }
        }
        final PsiParameter[] catchParameters1 =
                statement1.getCatchBlockParameters();
        final PsiParameter[] catchParameters2 =
                statement2.getCatchBlockParameters();
        if(catchParameters1.length != catchParameters2.length){
            return false;
        }
        for(int i = 0; i < catchParameters2.length; i++){
            if(!parametersAreEquivalent(catchParameters2[i],
                                        catchParameters1[i])){
                return false;
            }
        }
        return true;
    }

    private static boolean parametersAreEquivalent(PsiParameter parameter1,
                                                   PsiParameter parameter2){
        final PsiType type1 = parameter1.getType();
        final PsiType type2 = parameter2.getType();
        if(!typesAreEquivalent(type1, type2)){
            return false;
        }
        final String name1 = parameter1.getName();
        final String name2 = parameter2.getName();
        if(name1 == null){
            return name2 == null;
        }
        return name1.equals(name2);
    }

    private static boolean typesAreEquivalent(PsiType type1, PsiType type2){
        if(type1 == null){
            return type2 == null;
        }
        if(type2 == null){
            return false;
        }
        final String type1Text = type1.getCanonicalText();
        final String type2Text = type2.getCanonicalText();
        return type1Text.equals(type2Text);
    }

    private static boolean whileStatementsAreEquivalent(PsiWhileStatement statement1,
                                                        PsiWhileStatement statement2){
        final PsiExpression condition1 = statement1.getCondition();
        final PsiExpression condition2 = statement2.getCondition();
        final PsiStatement body1 = statement1.getBody();
        final PsiStatement body2 = statement2.getBody();
        return expressionsAreEquivalent(condition1, condition2) &&
                statementsAreEquivalent(body1, body2);
    }

    private static boolean forStatementsAreEquivalent(PsiForStatement statement1,
                                                      PsiForStatement statement2){
        final PsiExpression condition1 = statement1.getCondition();
        final PsiExpression condition2 = statement2.getCondition();
        if(!expressionsAreEquivalent(condition1, condition2)){
            return false;
        }
        final PsiStatement initialization1 = statement1.getInitialization();
        final PsiStatement initialization2 = statement2.getInitialization();
        if(!statementsAreEquivalent(initialization1, initialization2)){
            return false;
        }
        final PsiStatement update1 = statement1.getUpdate();
        final PsiStatement update2 = statement2.getUpdate();
        if(!statementsAreEquivalent(update1, update2)){
            return false;
        }
        final PsiStatement body1 = statement1.getBody();
        final PsiStatement body2 = statement2.getBody();
        return statementsAreEquivalent(body1, body2);
    }

    private static boolean switchStatementsAreEquivalent(PsiSwitchStatement statement1,
                                                         PsiSwitchStatement statement2){
        final PsiExpression switchExpression1 = statement1.getExpression();
        final PsiExpression swithcExpression2 = statement2.getExpression();
        final PsiCodeBlock body1 = statement1.getBody();
        final PsiCodeBlock body2 = statement2.getBody();
        return expressionsAreEquivalent(switchExpression1, swithcExpression2) &&
                codeBlocksAreEquivalent(body1, body2);
    }

    private static boolean doWhileStatementsAreEquivalent(PsiDoWhileStatement statement1,
                                                          PsiDoWhileStatement statement2){
        final PsiExpression condition1 = statement1.getCondition();
        final PsiExpression condition2 = statement2.getCondition();
        final PsiStatement body1 = statement1.getBody();
        final PsiStatement body2 = statement2.getBody();
        return expressionsAreEquivalent(condition1, condition2) &&
                statementsAreEquivalent(body1, body2);
    }

    private static boolean assertStatementsAreEquivalent(PsiAssertStatement statement1,
                                                         PsiAssertStatement statement2){
        final PsiExpression condition1 = statement1.getAssertCondition();
        final PsiExpression condition2 = statement2.getAssertCondition();
        final PsiExpression description1 = statement1.getAssertDescription();
        final PsiExpression description2 = statement2.getAssertDescription();
        return expressionsAreEquivalent(condition1, condition2) &&
                expressionsAreEquivalent(description1, description2);
    }

    private static boolean synchronizedStatementsAreEquivalent(PsiSynchronizedStatement statement1,
                                                               PsiSynchronizedStatement statement2){
        final PsiExpression lock1 = statement1.getLockExpression();
        final PsiExpression lock2 = statement2.getLockExpression();
        final PsiCodeBlock body1 = statement1.getBody();
        final PsiCodeBlock body2 = statement2.getBody();
        return expressionsAreEquivalent(lock1, lock2) &&
                codeBlocksAreEquivalent(body1, body2);
    }

    private static boolean blockStatementsAreEquivalent(PsiBlockStatement statement1,
                                                        PsiBlockStatement statement2){
        final PsiCodeBlock block1 = statement1.getCodeBlock();
        final PsiCodeBlock block2 = statement2.getCodeBlock();
        return codeBlocksAreEquivalent(block1, block2);
    }

    private static boolean breakStatementsAreEquivalent(PsiBreakStatement statement1,
                                                        PsiBreakStatement statement2){
        final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
        final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
        if(identifier1 == null){
            return identifier2 == null;
        }
        if(identifier2 == null){
            return false;
        }
        final String text1 = identifier1.getText();
        final String text2 = identifier2.getText();
        return text1.equals(text2);
    }

    private static boolean continueStatementsAreEquivalent(PsiContinueStatement statement1,
                                                           PsiContinueStatement statement2){
        final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
        final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
        if(identifier1 == null){
            return identifier2 == null;
        }
        if(identifier2 == null){
            return false;
        }
        final String text1 = identifier1.getText();
        final String text2 = identifier2.getText();
        return text1.equals(text2);
    }

    private static boolean switchLabelStatementsAreEquivalent(PsiSwitchLabelStatement statement1,
                                                              PsiSwitchLabelStatement statement2){
        if(statement1.isDefaultCase()){
            return statement2.isDefaultCase();
        }
        if(statement2.isDefaultCase()){
            return false;
        }
        final PsiExpression caseExpression1 = statement1.getCaseValue();
        final PsiExpression caseExpression2 = statement2.getCaseValue();
        return expressionsAreEquivalent(caseExpression1, caseExpression2);
    }

    private static boolean labeledStatementsAreEquivalent(PsiLabeledStatement statement1,
                                                          PsiLabeledStatement statement2){

        final PsiIdentifier identifier1 = statement1.getLabelIdentifier();
        final PsiIdentifier identifier2 = statement2.getLabelIdentifier();
        if(identifier1 == null){
            return identifier2 == null;
        }
        if(identifier2 == null){
            return false;
        }
        final String text1 = identifier1.getText();
        final String text2 = identifier2.getText();
        return text1.equals(text2);
    }

    private static boolean codeBlocksAreEquivalent(PsiCodeBlock block1,
                                                   PsiCodeBlock block2){
        if(block1 == null && block2 == null){
            return true;
        }
        if(block1 == null || block2 == null){
            return false;
        }
        final PsiStatement[] statements1 = block1.getStatements();
        final PsiStatement[] statements2 = block2.getStatements();
        if(statements2.length != statements1.length){
            return false;
        }
        for(int i = 0; i < statements2.length; i++){
            if(!statementsAreEquivalent(statements2[i], statements1[i])){
                return false;
            }
        }
        return true;
    }

    private static boolean ifStatementsAreEquivalent(PsiIfStatement statement1,
                                                     PsiIfStatement statement2){
        final PsiExpression condition1 = statement1.getCondition();
        final PsiExpression condition2 = statement2.getCondition();
        final PsiStatement thenBranch1 = statement1.getThenBranch();
        final PsiStatement thenBranch2 = statement2.getThenBranch();
        final PsiStatement elseBranch1 = statement1.getElseBranch();
        final PsiStatement elseBranch2 = statement2.getElseBranch();
        return expressionsAreEquivalent(condition1, condition2) &&
                statementsAreEquivalent(thenBranch1, thenBranch2) &&
                statementsAreEquivalent(elseBranch1, elseBranch2);
    }

    private static boolean expressionStatementsAreEquivalent(PsiExpressionStatement statement1,
                                                             PsiExpressionStatement statement2){
        final PsiExpression expression1 = statement1.getExpression();
        final PsiExpression expression2 = statement2.getExpression();
        return expressionsAreEquivalent(expression1, expression2);
    }

    private static boolean returnStatementsAreEquivalent(PsiReturnStatement statement1,
                                                         PsiReturnStatement statement2){
        final PsiExpression returnValue1 = statement1.getReturnValue();
        final PsiExpression returnValue2 = statement2.getReturnValue();
        return expressionsAreEquivalent(returnValue1, returnValue2);
    }

    private static boolean throwStatementsAreEquivalent(PsiThrowStatement statement1,
                                                        PsiThrowStatement statement2){
        final PsiExpression exception1 = statement1.getException();
        final PsiExpression exception2 = statement2.getException();
        return expressionsAreEquivalent(exception1, exception2);
    }

    private static boolean expressionListStatementsAreEquivalent(PsiExpressionListStatement statement1,
                                                                 PsiExpressionListStatement statement2){
        final PsiExpressionList expressionList1 =
                statement1.getExpressionList();
        final PsiExpression[] expressions1 = expressionList1.getExpressions();
        final PsiExpressionList expressionList2 =
                statement2.getExpressionList();
        final PsiExpression[] expressions2 = expressionList2.getExpressions();
        return expressionListsAreEquivalent(expressions1,
                                            expressions2);
    }

    public static boolean expressionsAreEquivalent(PsiExpression exp1,
                                                   PsiExpression exp2){
        if(exp1 == null && exp2 == null){
            return true;
        }
        if(exp1 == null || exp2 == null){
            return false;
        }
        PsiExpression expToCompare1 = exp1;
        while(expToCompare1 instanceof PsiParenthesizedExpression){
            expToCompare1 = ((PsiParenthesizedExpression) expToCompare1).getExpression();
        }
        PsiExpression expToCompare2 = exp2;
        while(expToCompare2 instanceof PsiParenthesizedExpression){
            expToCompare2 = ((PsiParenthesizedExpression) expToCompare2).getExpression();
        }
        final int type1 = getExpressionType(expToCompare1);
        final int type2 = getExpressionType(expToCompare2);
        if(type1 != type2){
            return false;
        }
        switch(type1){
        case THIS_EXPRESSION:
        case SUPER_EXPRESSION:
               return true;
        case LITERAL_EXPRESSION:
        case CLASS_OBJECT_EXPRESSION:
        case REFERENCE_EXPRESSION:
               final String text1 = expToCompare1.getText();
               final String text2 = expToCompare2.getText();
               return text1.equals(text2);
        case METHOD_CALL_EXPRESSION:
               return methodCallExpressionsAreEquivalent((PsiMethodCallExpression) expToCompare1,
                                                         (PsiMethodCallExpression) expToCompare2);
        case NEW_EXPRESSION:
               return newExpressionsAreEquivalent((PsiNewExpression) expToCompare1,
                                                  (PsiNewExpression) expToCompare2);
        case ARRAY_INITIALIZER_EXPRESSION:
               return arrayInitializerExpressionsAreEquivalent((PsiArrayInitializerExpression) expToCompare1,
                                                               (PsiArrayInitializerExpression) expToCompare2);
        case TYPECAST_EXPRESSION:
               return typecastExpressionsAreEquivalent((PsiTypeCastExpression) expToCompare2,
                                                       (PsiTypeCastExpression) expToCompare1);
        case ARRAY_ACCESS_EXPRESSION:
               return arrayAccessExpressionsAreEquivalent((PsiArrayAccessExpression) expToCompare2,
                                                          (PsiArrayAccessExpression) expToCompare1);
        case PREFIX_EXPRESSION:
               return prefixExpressionsAreEquivalent((PsiPrefixExpression) expToCompare1,
                                                     (PsiPrefixExpression) expToCompare2);
        case POSTFIX_EXPRESSION:
               return postfixExpressionsAreEquivalent((PsiPostfixExpression) expToCompare1,
                                                      (PsiPostfixExpression) expToCompare2);
        case BINARY_EXPRESSION:
               return binaryExpressionsAreEquivalent((PsiBinaryExpression) expToCompare1,
                                                     (PsiBinaryExpression) expToCompare2);
        case ASSIGNMENT_EXPRESSION:
               return assignmentExpressionsAreEquivalent((PsiAssignmentExpression) expToCompare1,
                                                         (PsiAssignmentExpression) expToCompare2);
        case CONDITIONAL_EXPRESSION:
               return conditionalExpressionsAreEquivalent((PsiConditionalExpression) expToCompare1,
                                                          (PsiConditionalExpression) expToCompare2);
        default:
               return false;
        }
    }

    private static boolean methodCallExpressionsAreEquivalent(PsiMethodCallExpression methodExp1,
                                                              PsiMethodCallExpression methodExp2){
        final PsiReferenceExpression methodExpression1 =
                methodExp1.getMethodExpression();
        final PsiReferenceExpression methodExpression2 =
                methodExp2.getMethodExpression();
        if(!expressionsAreEquivalent(methodExpression1, methodExpression2)){
            return false;
        }
        final PsiExpressionList argumentList1 = methodExp1.getArgumentList();
        final PsiExpression[] args1 = argumentList1.getExpressions();
        final PsiExpressionList argumentList2 = methodExp2.getArgumentList();
        final PsiExpression[] args2 = argumentList2.getExpressions();
        return expressionListsAreEquivalent(args1,
                                            args2);
    }

    private static boolean newExpressionsAreEquivalent(PsiNewExpression newExp1,
                                                       PsiNewExpression newExp2){
        final PsiExpression[] arrayDimensions1 = newExp1.getArrayDimensions();
        final PsiExpression[] arrayDimensions2 = newExp2.getArrayDimensions();
        if(!expressionListsAreEquivalent(arrayDimensions1, arrayDimensions2)){
            return false;
        }
        final PsiArrayInitializerExpression arrayInitializer1 =
                newExp1.getArrayInitializer();
        final PsiArrayInitializerExpression arrayInitializer2 =
                newExp2.getArrayInitializer();
        if(!expressionsAreEquivalent(arrayInitializer1, arrayInitializer2)){
            return false;
        }
        final PsiExpression qualifier1 = newExp1.getQualifier();
        final PsiExpression qualifier2 = newExp2.getQualifier();
        if(!expressionsAreEquivalent(qualifier1, qualifier2)){
            return false;
        }
        final PsiExpressionList argumentList1 = newExp1.getArgumentList();
        final PsiExpression[] args1 = argumentList1 == null?null:
                        argumentList1.getExpressions();
        final PsiExpressionList argumentList2 = newExp2.getArgumentList();
        final PsiExpression[] args2 = argumentList2 == null?null:
                        argumentList2.getExpressions();
        return expressionListsAreEquivalent(args1, args2);
    }

    private static boolean arrayInitializerExpressionsAreEquivalent(PsiArrayInitializerExpression arrInitExp1,
                                                                    PsiArrayInitializerExpression arrInitExp2){
        final PsiExpression[] initializers1 = arrInitExp1.getInitializers();
        final PsiExpression[] initializers2 = arrInitExp2.getInitializers();
        return expressionListsAreEquivalent(initializers1, initializers2);
    }

    private static boolean typecastExpressionsAreEquivalent(PsiTypeCastExpression typecastExp2,
                                                            PsiTypeCastExpression typecastExp1){
        final PsiTypeElement castType2 = typecastExp2.getCastType();
        final PsiTypeElement castType1 = typecastExp1.getCastType();
        if(!castType2.equals(castType1)){
            return false;
        }
        final PsiExpression operand1 = typecastExp1.getOperand();
        final PsiExpression operand2 = typecastExp2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean arrayAccessExpressionsAreEquivalent(PsiArrayAccessExpression arrAccessExp2,
                                                               PsiArrayAccessExpression arrAccessExp1){
        final PsiExpression arrayExpression2 =
                arrAccessExp2.getArrayExpression();
        final PsiExpression arrayExpression1 =
                arrAccessExp1.getArrayExpression();
        final PsiExpression indexExpression2 =
                arrAccessExp2.getIndexExpression();
        final PsiExpression indexExpression1 =
                arrAccessExp1.getIndexExpression();
        return expressionsAreEquivalent(arrayExpression2, arrayExpression1)
                && expressionsAreEquivalent(indexExpression2, indexExpression1);
    }

    private static boolean prefixExpressionsAreEquivalent(PsiPrefixExpression prefixExp1,
                                                          PsiPrefixExpression prefixExp2){
        final PsiJavaToken sign1 = prefixExp1.getOperationSign();
        final PsiJavaToken sign2 = prefixExp2.getOperationSign();
        if(sign1.getTokenType() != sign2.getTokenType()){
            return false;
        }
        final PsiExpression operand1 = prefixExp1.getOperand();
        final PsiExpression operand2 = prefixExp2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean postfixExpressionsAreEquivalent(PsiPostfixExpression postfixExp1,
                                                           PsiPostfixExpression postfixExp2){
        final PsiJavaToken sign1 = postfixExp1.getOperationSign();
        final PsiJavaToken sign2 = postfixExp2.getOperationSign();
        if(sign1.getTokenType() != sign2.getTokenType()){
            return false;
        }
        final PsiExpression operand1 = postfixExp1.getOperand();
        final PsiExpression operand2 = postfixExp2.getOperand();
        return expressionsAreEquivalent(operand1, operand2);
    }

    private static boolean binaryExpressionsAreEquivalent(PsiBinaryExpression binaryExp1,
                                                          PsiBinaryExpression binaryExp2){
        final PsiJavaToken sign1 = binaryExp1.getOperationSign();
        final PsiJavaToken sign2 = binaryExp2.getOperationSign();
        if(sign1.getTokenType() != sign2.getTokenType()){
            return false;
        }
        final PsiExpression lhs1 = binaryExp1.getLOperand();
        final PsiExpression lhs2 = binaryExp2.getLOperand();
        final PsiExpression rhs1 = binaryExp1.getROperand();
        final PsiExpression rhs2 = binaryExp2.getROperand();
        return expressionsAreEquivalent(lhs1, lhs2)
                && expressionsAreEquivalent(rhs1, rhs2);
    }

    private static boolean assignmentExpressionsAreEquivalent(PsiAssignmentExpression assignExp1,
                                                              PsiAssignmentExpression assignExp2){
        final PsiJavaToken sign1 = assignExp1.getOperationSign();
        final PsiJavaToken sign2 = assignExp2.getOperationSign();
        if(sign1.getTokenType() != sign2.getTokenType()){
            return false;
        }
        final PsiExpression lhs1 = assignExp1.getLExpression();
        final PsiExpression lhs2 = assignExp2.getLExpression();
        final PsiExpression rhs1 = assignExp1.getRExpression();
        final PsiExpression rhs2 = assignExp2.getRExpression();
        return expressionsAreEquivalent(lhs1, lhs2)
                && expressionsAreEquivalent(rhs1, rhs2);
    }

    private static boolean conditionalExpressionsAreEquivalent(PsiConditionalExpression condExp1,
                                                               PsiConditionalExpression condExp2){
        final PsiExpression condition1 = condExp1.getCondition();
        final PsiExpression condition2 = condExp2.getCondition();
        final PsiExpression thenExpression1 = condExp1.getThenExpression();
        final PsiExpression thenExpression2 = condExp2.getThenExpression();
        final PsiExpression elseExpression1 = condExp1.getElseExpression();
        final PsiExpression elseExpression2 = condExp2.getElseExpression();
        return expressionsAreEquivalent(condition1, condition2)
                && expressionsAreEquivalent(thenExpression1, thenExpression2)
                && expressionsAreEquivalent(elseExpression1, elseExpression2);
    }

    private static boolean expressionListsAreEquivalent(PsiExpression[] expressions1,
                                                        PsiExpression[] expressions2){
        if(expressions1 == null && expressions2 == null){
            return true;
        }
        if(expressions1 == null || expressions2 == null){
            return false;
        }
        if(expressions1.length != expressions2.length){
            return false;
        }
        for(int i = 0; i < expressions1.length; i++){
            if(!expressionsAreEquivalent(expressions1[i], expressions2[i])){
                return false;
            }
        }
        return true;
    }

    private static int getExpressionType(PsiExpression exp){
        if(exp instanceof PsiThisExpression){
            return THIS_EXPRESSION;
        }
        if(exp instanceof PsiLiteralExpression){
            return LITERAL_EXPRESSION;
        }
        if(exp instanceof PsiClassObjectAccessExpression){
            return CLASS_OBJECT_EXPRESSION;
        }
        if(exp instanceof PsiReferenceExpression){
            return REFERENCE_EXPRESSION;
        }
        if(exp instanceof PsiSuperExpression){
            return SUPER_EXPRESSION;
        }
        if(exp instanceof PsiMethodCallExpression){
            return METHOD_CALL_EXPRESSION;
        }
        if(exp instanceof PsiNewExpression){
            return NEW_EXPRESSION;
        }
        if(exp instanceof PsiArrayInitializerExpression){
            return ARRAY_INITIALIZER_EXPRESSION;
        }
        if(exp instanceof PsiTypeCastExpression){
            return TYPECAST_EXPRESSION;
        }
        if(exp instanceof PsiArrayAccessExpression){
            return ARRAY_ACCESS_EXPRESSION;
        }
        if(exp instanceof PsiPrefixExpression){
            return PREFIX_EXPRESSION;
        }
        if(exp instanceof PsiPostfixExpression){
            return POSTFIX_EXPRESSION;
        }
        if(exp instanceof PsiBinaryExpression){
            return BINARY_EXPRESSION;
        }
        if(exp instanceof PsiConditionalExpression){
            return CONDITIONAL_EXPRESSION;
        }
        if(exp instanceof PsiAssignmentExpression){
            return ASSIGNMENT_EXPRESSION;
        }
        return -1;
    }

    private static int getStatementType(PsiStatement statement){
        if(statement instanceof PsiAssertStatement){
            return ASSERT_STATEMENT;
        }
        if(statement instanceof PsiBlockStatement){
            return BLOCK_STATEMENT;
        }
        if(statement instanceof PsiBreakStatement){
            return BREAK_STATEMENT;
        }
        if(statement instanceof PsiContinueStatement){
            return CONTINUE_STATEMENT;
        }
        if(statement instanceof PsiDeclarationStatement){
            return DECLARATION_STATEMENT;
        }
        if(statement instanceof PsiDoWhileStatement){
            return DO_WHILE_STATEMENT;
        }
        if(statement instanceof PsiEmptyStatement){
            return EMPTY_STATEMENT;
        }
        if(statement instanceof PsiExpressionListStatement){
            return EXPRESSION_LIST_STATEMENT;
        }
        if(statement instanceof PsiExpressionStatement){
            return EXPRESSION_STATEMENT;
        }
        if(statement instanceof PsiForStatement){
            return FOR_STATEMENT;
        }
        if(statement instanceof PsiIfStatement){
            return IF_STATEMENT;
        }
        if(statement instanceof PsiLabeledStatement){
            return LABELED_STATEMENT;
        }
        if(statement instanceof PsiReturnStatement){
            return RETURN_STATEMENT;
        }
        if(statement instanceof PsiSwitchLabelStatement){
            return SWITCH_LABEL_STATEMENT;
        }
        if(statement instanceof PsiSwitchStatement){
            return SWITCH_STATEMENT;
        }
        if(statement instanceof PsiSynchronizedStatement){
            return SYNCHRONIZED_STATEMENT;
        }
        if(statement instanceof PsiThrowStatement){
            return THROW_STATEMENT;
        }
        if(statement instanceof PsiTryStatement){
            return TRY_STATEMENT;
        }
        if(statement instanceof PsiWhileStatement){
            return WHILE_STATEMENT;
        }
        return -1;
    }
}
