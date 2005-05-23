package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.verbose.ConditionalUtils;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ErrorUtil;
import org.jetbrains.annotations.NotNull;

public class TrivialIfInspection extends StatementInspection{
    private final TrivialIfFix fix = new TrivialIfFix();

    public String getID(){
        return "RedundantIfStatement";
    }

    public String getDisplayName(){
        return "Redundant 'if' statement";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new TrivialIfVisitor();
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "#ref statement can be simplified #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class TrivialIfFix extends InspectionGadgetsFix{
        public String getName(){
            return "Simplify";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement ifKeywordElement = descriptor.getPsiElement();
            final PsiIfStatement statement =
                    (PsiIfStatement) ifKeywordElement.getParent();
            if(isSimplifiableAssignment(statement)){
                replaceSimplifiableAssignment(statement);
            } else if(isSimplifiableReturn(statement)){
                repaceSimplifiableReturn(statement);
            } else if(isSimplifiableImplicitReturn(statement)){
                replaceSimplifiableImplicitReturn(statement);
            } else if(isSimplifiableAssignmentNegated(statement)){
                replaceSimplifiableAssignmentNegated(statement);
            } else if(isSimplifiableReturnNegated(statement)){
                repaceSimplifiableReturnNegated(statement);
            } else if(isSimplifiableImplicitReturnNegated(statement)){
                replaceSimplifiableImplicitReturnNegated(statement);
            } else if(isSimplifiableImplicitAssignment(statement)){
                replaceSimplifiableImplicitAssignment(statement);
            } else if(isSimplifiableImplicitAssignmentNegated(statement)){
                replaceSimplifiableImplicitAssignmentNegated(statement);
            }
        }

        private void replaceSimplifiableImplicitReturn(PsiIfStatement statement)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();
            final String conditionText = condition.getText();
            final PsiElement nextStatement =
                    PsiTreeUtil.skipSiblingsForward(statement,
                                                    new Class[]{
                                                        PsiWhiteSpace.class});
            final String newStatement = "return " + conditionText + ';';
            replaceStatement(statement, newStatement);
            assert nextStatement != null;
            deleteElement(nextStatement);
        }

        private void repaceSimplifiableReturn(PsiIfStatement statement)
                                                                        throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();
            final String conditionText = condition.getText();
            final String newStatement = "return " + conditionText + ';';
            replaceStatement(statement, newStatement);
        }

        private void replaceSimplifiableAssignment(PsiIfStatement statement)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();
            final String conditionText = condition.getText();
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) assignmentStatement.getExpression();
            final PsiJavaToken operator =
                    assignmentExpression.getOperationSign();
            final String operand = operator.getText();
            final PsiExpression lhs = assignmentExpression.getLExpression();
            final String lhsText = lhs.getText();
            replaceStatement(statement,
                             lhsText + operand + conditionText + ';');
        }

        private void replaceSimplifiableImplicitAssignment(PsiIfStatement statement)
                throws IncorrectOperationException{
            final PsiElement prevStatement =
                    PsiTreeUtil.skipSiblingsBackward(statement,
                                                     new Class[]{
                                                         PsiWhiteSpace.class});
            if(prevStatement == null){
                return;
            }
            final PsiExpression condition = statement.getCondition();
            final String conditionText = condition.getText();
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) assignmentStatement.getExpression();
            final PsiJavaToken operator =
                    assignmentExpression.getOperationSign();
            final String operand = operator.getText();
            final PsiExpression lhs = assignmentExpression.getLExpression();
            final String lhsText = lhs.getText();
            replaceStatement(statement,
                             lhsText + operand + conditionText + ';');
                deleteElement(prevStatement);
        }

        private void replaceSimplifiableImplicitAssignmentNegated(PsiIfStatement statement)
                throws IncorrectOperationException{
            final PsiElement prevStatement =
                    PsiTreeUtil.skipSiblingsBackward(statement,
                                                     new Class[]{
                                                         PsiWhiteSpace.class});

            final PsiExpression condition = statement.getCondition();
            final String conditionText =
                    BoolUtils.getNegatedExpressionText(condition);
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) assignmentStatement.getExpression();
            final PsiJavaToken operator =
                    assignmentExpression.getOperationSign();
            final String operand = operator.getText();
            final PsiExpression lhs = assignmentExpression.getLExpression();
            final String lhsText = lhs.getText();
            replaceStatement(statement,
                             lhsText + operand + conditionText + ';');
            assert prevStatement!=null;
            deleteElement(prevStatement);
        }

        private void replaceSimplifiableImplicitReturnNegated(PsiIfStatement statement)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();

            final String conditionText =
                    BoolUtils.getNegatedExpressionText(condition);
            final PsiElement nextStatement =
                    PsiTreeUtil.skipSiblingsForward(statement,
                                                    new Class[]{
                                                        PsiWhiteSpace.class});
            if(nextStatement == null){
                return;
            }
            final String newStatement = "return " + conditionText + ';';
            replaceStatement(statement, newStatement);
           deleteElement(nextStatement);
        }

        private void repaceSimplifiableReturnNegated(PsiIfStatement statement)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();
            final String conditionText =
                    BoolUtils.getNegatedExpressionText(condition);
            final String newStatement = "return " + conditionText + ';';
            replaceStatement(statement, newStatement);
        }

        private void replaceSimplifiableAssignmentNegated(PsiIfStatement statement)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();
            final String conditionText =
                    BoolUtils.getNegatedExpressionText(condition);
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) assignmentStatement.getExpression();
            final PsiJavaToken operator =
                    assignmentExpression.getOperationSign();
            final String operand = operator.getText();
            final PsiExpression lhs = assignmentExpression.getLExpression();
            final String lhsText = lhs.getText();
            replaceStatement(statement,
                             lhsText + operand + conditionText + ';');
        }
    }

    private static class TrivialIfVisitor extends StatementInspectionVisitor{

        public void visitIfStatement(@NotNull PsiIfStatement ifStatement){
            super.visitIfStatement(ifStatement);
            final PsiExpression condition = ifStatement.getCondition();
            if(condition == null){
                return;
            }
            if(ErrorUtil.containsError(ifStatement)){
                return;
            }
            if(isSimplifiableAssignment(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }

            if(isSimplifiableReturn(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }

            if(isSimplifiableImplicitReturn(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }
            if(isSimplifiableAssignmentNegated(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }

            if(isSimplifiableReturnNegated(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }

            if(isSimplifiableImplicitReturnNegated(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }
            if(isSimplifiableImplicitAssignment(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }

            if(isSimplifiableImplicitAssignmentNegated(ifStatement)){
                registerStatementError(ifStatement);
            }
        }
    }

    public static boolean isSimplifiableImplicitReturn(PsiIfStatement ifStatement){
        if(ifStatement.getElseBranch() != null){
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(ifStatement,
                                                new Class[]{
                                                    PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }

        final PsiStatement elseBranch = (PsiStatement) nextStatement;
        return ConditionalUtils.isReturn(thenBranch, "true")
                && ConditionalUtils.isReturn(elseBranch, "false");
    }

    public static boolean isSimplifiableImplicitReturnNegated(PsiIfStatement ifStatement){
        if(ifStatement.getElseBranch() != null){
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);

        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(ifStatement,
                                                new Class[]{
                                                    PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        final PsiStatement elseBranch = (PsiStatement) nextStatement;
        return ConditionalUtils.isReturn(thenBranch, "false")
                && ConditionalUtils.isReturn(elseBranch, "true");
    }

    public static boolean isSimplifiableReturn(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        return ConditionalUtils.isReturn(thenBranch, "true")
                && ConditionalUtils.isReturn(elseBranch, "false");
    }

    public static boolean isSimplifiableReturnNegated(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        return ConditionalUtils.isReturn(thenBranch, "false")
                && ConditionalUtils.isReturn(elseBranch, "true");
    }

    public static boolean isSimplifiableAssignment(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "true") &&
                ConditionalUtils.isAssignment(elseBranch, "false")){
            final PsiAssignmentExpression thenExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) thenBranch).getExpression();
            final PsiAssignmentExpression elseExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) elseBranch).getExpression();
            final PsiJavaToken thenSign = thenExpression.getOperationSign();
            final PsiJavaToken elseSign = elseExpression.getOperationSign();
            if(!thenSign.getTokenType().equals(elseSign.getTokenType())){
                return false;
            }
            final PsiExpression thenLhs = thenExpression.getLExpression();
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                               elseLhs);
        } else{
            return false;
        }
    }

    public static boolean isSimplifiableAssignmentNegated(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "false") &&
                ConditionalUtils.isAssignment(elseBranch, "true")){
            final PsiAssignmentExpression thenExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) thenBranch).getExpression();
            final PsiAssignmentExpression elseExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) elseBranch).getExpression();
            final PsiJavaToken thenSign = thenExpression.getOperationSign();
            final PsiJavaToken elseSign = elseExpression.getOperationSign();
            if(!thenSign.getTokenType().equals(elseSign.getTokenType())){
                return false;
            }
            final PsiExpression thenLhs = thenExpression.getLExpression();
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                               elseLhs);
        } else{
            return false;
        }
    }

    public static boolean isSimplifiableImplicitAssignment(PsiIfStatement ifStatement){
        if(ifStatement.getElseBranch() != null){
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsBackward(ifStatement,
                                                 new Class[]{
                                                     PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        PsiStatement elseBranch = (PsiStatement) nextStatement;

        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "true") &&
                ConditionalUtils.isAssignment(elseBranch, "false")){
            final PsiAssignmentExpression thenExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) thenBranch).getExpression();
            final PsiAssignmentExpression elseExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) elseBranch).getExpression();
            final PsiJavaToken thenSign = thenExpression.getOperationSign();
            final PsiJavaToken elseSign = elseExpression.getOperationSign();
            if(!thenSign.getTokenType().equals(elseSign.getTokenType())){
                return false;
            }
            final PsiExpression thenLhs = thenExpression.getLExpression();
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                               elseLhs);
        } else{
            return false;
        }
    }

    public static boolean isSimplifiableImplicitAssignmentNegated(PsiIfStatement ifStatement){
        if(ifStatement.getElseBranch() != null){
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsBackward(ifStatement,
                                                 new Class[]{
                                                     PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        PsiStatement elseBranch = (PsiStatement) nextStatement;

        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "false") &&
                ConditionalUtils.isAssignment(elseBranch, "true")){
            final PsiAssignmentExpression thenExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) thenBranch).getExpression();
            final PsiAssignmentExpression elseExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) elseBranch).getExpression();
            final PsiJavaToken thenSign = thenExpression.getOperationSign();
            final PsiJavaToken elseSign = elseExpression.getOperationSign();
            if(!thenSign.getTokenType().equals(elseSign.getTokenType())){
                return false;
            }
            final PsiExpression thenLhs = thenExpression.getLExpression();
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return EquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                               elseLhs);
        } else{
            return false;
        }
    }

}
