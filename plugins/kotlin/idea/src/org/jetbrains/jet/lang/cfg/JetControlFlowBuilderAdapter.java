package org.jetbrains.jet.lang.cfg;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.psi.JetBlockExpression;
import org.jetbrains.jet.lang.psi.JetElement;
import org.jetbrains.jet.lang.psi.JetExpression;

/**
 * @author abreslav
 */
public class JetControlFlowBuilderAdapter implements JetControlFlowBuilder {
    protected JetControlFlowBuilder builder;

    public JetControlFlowBuilderAdapter(JetControlFlowBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void readNode(@NotNull JetExpression expression) {
        builder.readNode(expression);
    }

    @Override
    @NotNull
    public Label createUnboundLabel() {
        return builder.createUnboundLabel();
    }

    @Override
    public void bindLabel(@NotNull Label label) {
        builder.bindLabel(label);
    }

    @Override
    public void jump(@NotNull Label label) {
        builder.jump(label);
    }

    @Override
    public void jumpOnFalse(@NotNull Label label) {
        builder.jumpOnFalse(label);
    }

    @Override
    public void jumpOnTrue(@NotNull Label label) {
        builder.jumpOnTrue(label);
    }

    @Override
    public void nondeterministicJump(Label label) {
        builder.nondeterministicJump(label);
    }

    @Override
    public Label getEntryPoint(@NotNull JetElement labelElement) {
        return builder.getEntryPoint(labelElement);
    }

    @Override
    public Label getExitPoint(@NotNull JetElement labelElement) {
        return builder.getExitPoint(labelElement);
    }

    @Override
    public Label enterLoop(@NotNull JetExpression expression, Label loopExitPoint) {
        return builder.enterLoop(expression, loopExitPoint);
    }

    @Override
    public void exitLoop(@NotNull JetExpression expression) {
        builder.exitLoop(expression);
    }

    @Override
    @Nullable
    public JetElement getCurrentLoop() {
        return builder.getCurrentLoop();
    }

    @Override
    public void enterTryFinally(@NotNull JetBlockExpression expression) {
        builder.enterTryFinally(expression);
    }

    @Override
    public void exitTryFinally() {
        builder.exitTryFinally();
    }

    @Override
    public void enterSubroutine(@NotNull JetElement subroutine, boolean isFunctionLiteral) {
        builder.enterSubroutine(subroutine, isFunctionLiteral);
    }

    @Override
    public void exitSubroutine(@NotNull JetElement subroutine, boolean functionLiteral) {
        builder.exitSubroutine(subroutine, functionLiteral);
    }

    @Override
    @Nullable
    public JetElement getCurrentSubroutine() {
        return builder.getCurrentSubroutine();
    }

    @Override
    public void returnValue(@NotNull JetElement subroutine) {
        builder.returnValue(subroutine);
    }

    @Override
    public void returnNoValue(@NotNull JetElement subroutine) {
        builder.returnNoValue(subroutine);
    }

    @Override
    public void unsupported(JetElement element) {
        builder.unsupported(element);
    }

    @Override
    public void writeNode(@NotNull JetElement assignment, @NotNull JetElement lValue) {
        builder.writeNode(assignment, lValue);
    }
}
