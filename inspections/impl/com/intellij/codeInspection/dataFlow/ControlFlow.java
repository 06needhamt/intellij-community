/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 11, 2002
 * Time: 3:05:34 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.FlushVariableInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.HashMap;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Stack;

public class ControlFlow {
  private final ArrayList<Instruction> myInstructions = new ArrayList<Instruction>();
  private final HashMap<PsiElement,Integer> myElementToStartOffsetMap = new HashMap<PsiElement, Integer>();
  private final HashMap<PsiElement,Integer> myElementToEndOffsetMap = new HashMap<PsiElement, Integer>();
  private final Stack<PsiElement> myElementStack = new Stack<PsiElement>();
  private DfaVariableValue[] myFields;
  private DfaValueFactory myFactory;

  public ControlFlow(final DfaValueFactory factory) {
    myFactory = factory;
  }

  public Instruction[] getInstructions(){
    addInstruction(new ReturnInstruction());
    return myInstructions.toArray(new Instruction[myInstructions.size()]);
  }

  public int getInstructionCount() {
    return myInstructions.size();
  }

  public void startElement(PsiElement psiElement) {
    myElementStack.push(psiElement);
    myElementToStartOffsetMap.put(psiElement, new Integer(myInstructions.size()));
  }

  public void finishElement(PsiElement psiElement) {
    myElementToEndOffsetMap.put(psiElement, new Integer(myInstructions.size()));
  }

  public void addInstruction(Instruction instruction) {
    instruction.setIndex(myInstructions.size());
    myInstructions.add(instruction);
  }

  public void removeVariable(PsiVariable variable) {
    DfaVariableValue var = myFactory.getVarFactory().create(variable, false);
    addInstruction(new FlushVariableInstruction(var));
  }

  public int getStartOffset(PsiElement element){
    Integer value = myElementToStartOffsetMap.get(element);
    if (value == null) return -1;
    return value.intValue();
  }

  public int getEndOffset(PsiElement element){
    Integer value = myElementToEndOffsetMap.get(element);
    if (value == null) return -1;
    return value.intValue();
  }

    public DfaVariableValue[] getFields() {
    return myFields;
  }

  public void setFields(DfaVariableValue[] fields) {
    myFields = fields;
  }

  public void dump(PrintStream p) {
    p.println(toString());
  }


  public String toString() {
    StringBuilder result = new StringBuilder();
    final Instruction[] instructions = getInstructions();

    for (int i = 0; i < instructions.length; i++) {
      Instruction instruction = instructions[i];
      result.append(Integer.toString(i) + ": " + instruction.toString());
      result.append("\n");
    }
    return result.toString();
  }
}
