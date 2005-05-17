package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;

public class ControlFlowUtils{
    private ControlFlowUtils(){
        super();
    }

    public static boolean statementMayCompleteNormally(PsiStatement statement){
        if(statement instanceof PsiBreakStatement ||
                statement instanceof PsiContinueStatement ||
                statement instanceof PsiReturnStatement ||
                statement instanceof PsiThrowStatement){
            return false;
        } else if(statement instanceof PsiExpressionListStatement ||
                statement instanceof PsiExpressionStatement ||
                statement instanceof PsiEmptyStatement ||
                statement instanceof PsiAssertStatement ||
                statement instanceof PsiDeclarationStatement){
            return true;
        } else if(statement instanceof PsiForStatement){
            final PsiForStatement loopStatement = (PsiForStatement) statement;
            final PsiExpression test = loopStatement.getCondition();

            return test != null && !isBooleanConstant(test, false) ||
                    statementIsBreakTarget(loopStatement);
        } else if(statement instanceof PsiWhileStatement){
            final PsiWhileStatement loopStatement =
                    (PsiWhileStatement) statement;
            final PsiExpression test = loopStatement.getCondition();
            return !isBooleanConstant(test, true)
                    || statementIsBreakTarget(loopStatement);
        } else if(statement instanceof PsiDoWhileStatement){
            final PsiDoWhileStatement loopStatement =
                    (PsiDoWhileStatement) statement;
            final PsiExpression test = loopStatement.getCondition();
            final PsiStatement body = loopStatement.getBody();
            return statementMayCompleteNormally(body) &&
                    !isBooleanConstant(test, true)
                    || statementIsBreakTarget(loopStatement);
        } else if(statement instanceof PsiSynchronizedStatement){
            final PsiCodeBlock body =
                    ((PsiSynchronizedStatement) statement).getBody();
            return codeBlockMayCompleteNormally(body);
        } else if(statement instanceof PsiBlockStatement){
            final PsiCodeBlock codeBlock =
                    ((PsiBlockStatement) statement).getCodeBlock();
            return codeBlockMayCompleteNormally(codeBlock);
        } else if(statement instanceof PsiLabeledStatement){
            final PsiLabeledStatement labeledStatement =
                    (PsiLabeledStatement) statement;
            final PsiStatement body = labeledStatement.getStatement();

            return statementMayCompleteNormally(body)
                    || statementIsBreakTarget(body);
        } else if(statement instanceof PsiIfStatement){
            final PsiIfStatement ifStatement = (PsiIfStatement) statement;
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            if(statementMayCompleteNormally(thenBranch)){
                return true;
            }
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            return elseBranch == null ||
                    statementMayCompleteNormally(elseBranch);
        } else if(statement instanceof PsiTryStatement){
            final PsiTryStatement tryStatement = (PsiTryStatement) statement;

            final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if(finallyBlock != null){
                if(!codeBlockMayCompleteNormally(finallyBlock)){
                    return false;
                }
            }
            final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if(codeBlockMayCompleteNormally(tryBlock)){
                return true;
            }
            final PsiCodeBlock[] catchBlocks = tryStatement.getCatchBlocks();
            for(final PsiCodeBlock catchBlock : catchBlocks){
                if(codeBlockMayCompleteNormally(catchBlock)){
                    return true;
                }
            }
            return false;
        } else if(statement instanceof PsiSwitchStatement){
            final PsiSwitchStatement switchStatement =
                    (PsiSwitchStatement) statement;
            if(statementIsBreakTarget(switchStatement)){
                return true;
            }
            final PsiCodeBlock body = switchStatement.getBody();
            final PsiStatement[] statements = body.getStatements();
            int lastNonLabelOffset = -1;
            if(statements != null){
                for(int i = statements.length - 1; i >= 0; i--){
                    if(!(statements[i] instanceof PsiSwitchLabelStatement)){
                        lastNonLabelOffset = i;
                        break;
                    }
                }
            }
            if(lastNonLabelOffset == -1){
                return true;    // it's all labels
            } else if(lastNonLabelOffset == statements.length - 1){
                return statementMayCompleteNormally(statements[statements
                        .length -
                        1]);
            } else{
                return true;    // the last statement is a label
            }
        } else{
            return false;
        }
    }

    private static boolean codeBlockMayCompleteNormally(PsiCodeBlock block){
        final PsiStatement[] statements = block.getStatements();
        for(final PsiStatement statement : statements){
            if(!statementMayCompleteNormally(statement)){
                return false;
            }
        }
        return true;
    }

    private static boolean isBooleanConstant(PsiExpression test, boolean val){
        if(!PsiUtil.isConstantExpression(test)){
            return false;
        }
        final boolean value =
                (Boolean) ConstantExpressionUtil.computeCastTo(test,
                                                               PsiType.BOOLEAN);
        return value == val;
    }

    private static boolean statementIsBreakTarget(PsiStatement statement){
        if(statement == null){
            return false;
        }
        final BreakTargetFinder breakFinder = new BreakTargetFinder(statement);
        statement.accept(breakFinder);
        return breakFinder.breakFound();
    }

    public static boolean statementContainsExitingBreak(PsiStatement statement){
        if(statement == null){
            return false;
        }
        final ExitingBreakFinder breakFinder = new ExitingBreakFinder();
        statement.accept(breakFinder);
        return breakFinder.breakFound();
    }

    private static class BreakTargetFinder extends PsiRecursiveElementVisitor{
        private boolean m_found = false;
        private final PsiStatement m_target;

        private BreakTargetFinder(PsiStatement target){
            super();
            m_target = target;
        }

        private boolean breakFound(){
            return m_found;
        }

        public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression){
        }

        public void visitBreakStatement(PsiBreakStatement breakStatement){
            super.visitBreakStatement(breakStatement);
            final PsiStatement exitedStatement =
                    breakStatement.findExitedStatement();
            if(exitedStatement.equals(m_target)){
                m_found = true;
            }
        }
    }

    private static class ExitingBreakFinder
            extends PsiRecursiveElementVisitor{
        private boolean m_found = false;

        private ExitingBreakFinder(){
            super();
        }

        private boolean breakFound(){
            return m_found;
        }

        public void visitReferenceExpression(PsiReferenceExpression exp){
        }

        public void visitBreakStatement(PsiBreakStatement breakStatement){
            if(breakStatement.getLabelIdentifier() != null){
                return;
            }
            m_found = true;
        }

        public void visitDoWhileStatement(PsiDoWhileStatement statement){
            // don't drill down
        }

        public void visitForStatement(PsiForStatement statement){
            // don't drill down
        }

        public void visitWhileStatement(PsiWhileStatement statement){
            // don't drill down
        }

        public void visitSwitchStatement(PsiSwitchStatement statement){
            // don't drill down
        }
    }
}
