/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:46:40 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;

public abstract class Instruction {
  private int myIndex;
  private final ArrayList<DfaMemoryState> myProcessedStates;

  protected Instruction() {
    myProcessedStates = new ArrayList<DfaMemoryState>();
  }

  public abstract DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState dfaBeforeMemoryState);

  public boolean isMemoryStateProcessed(DfaMemoryState dfaMemState) {
    for (DfaMemoryState state : myProcessedStates) {
      if (dfaMemState.equals(state)) {
        return true;
      }
    }

    return false;
  }

  public boolean setMemoryStateProcessed(DfaMemoryState dfaMemState) {
    if (myProcessedStates.size() > DataFlowRunner.MAX_STATES_PER_BRANCH) return false;
    myProcessedStates.add(dfaMemState);
    return true;
  }

  public void setIndex(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  @NonNls
  public String toString() {
    return super.toString();
  }
}
