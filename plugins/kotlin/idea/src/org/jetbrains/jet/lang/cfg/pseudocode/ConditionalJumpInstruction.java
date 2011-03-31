package org.jetbrains.jet.lang.cfg.pseudocode;

import org.jetbrains.jet.lang.cfg.Label;

/**
* @author abreslav
*/
public class ConditionalJumpInstruction extends AbstractJumpInstruction {
    private final boolean onTrue;
    private Instruction nextOnTrue;
    private Instruction nextOnFalse;

    public ConditionalJumpInstruction(boolean onTrue, Label targetLabel) {
        super(targetLabel);
        this.onTrue = onTrue;
    }

    public boolean onTrue() {
        return onTrue;
    }

    public Instruction getNextOnTrue() {
        return nextOnTrue;
    }

    public void setNextOnTrue(Instruction nextOnTrue) {
        this.nextOnTrue = outgoingEdgeTo(nextOnTrue);
    }

    public Instruction getNextOnFalse() {
        return nextOnFalse;
    }

    public void setNextOnFalse(Instruction nextOnFalse) {
        this.nextOnFalse = outgoingEdgeTo(nextOnFalse);
    }

    @Override
    public void accept(InstructionVisitor visitor) {
        visitor.visitConditionalJump(this);
    }

    @Override
    public String toString() {
        String instr = onTrue ? "jt" : "jf";
        return instr + "(" + getTargetLabel().getName() + ")";
    }
}
