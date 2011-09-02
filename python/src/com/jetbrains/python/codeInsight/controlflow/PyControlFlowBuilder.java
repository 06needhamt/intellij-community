package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementNavigator;
import com.jetbrains.python.psi.impl.PyConstantExpressionEvaluator;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class PyControlFlowBuilder extends PyRecursiveElementVisitor {

  public static final TokenSet CALL_OR_REF_EXPR = TokenSet.create(PyElementTypes.CALL_EXPRESSION, PyElementTypes.REFERENCE_EXPRESSION);
  public static final String SELF_ASSERT_RAISES = "self.assertRaises";
  private final ControlFlowBuilder myBuilder = new ControlFlowBuilder();
  private List<Pair<PsiElement, Instruction>> myPendindBackup = null;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//// Control flow builder staff
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public ControlFlow buildControlFlow(@NotNull final ScopeOwner owner) {
    return myBuilder.build(this, owner);
  }

  @Override
  public void visitPyFunction(final PyFunction node) {
    // Create node and stop here
    myBuilder.startNode(node);
    final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, node, node.getName());
    myBuilder.addNode(instruction);
    myBuilder.checkPending(instruction);
  }

  @Override
  public void visitPyClass(final PyClass node) {
    // Create node and stop here
    myBuilder.startNode(node);
    final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, node, node.getName());
    myBuilder.addNode(instruction);
    myBuilder.checkPending(instruction);
  }

  @Override
  public void visitPyStatement(final PyStatement node) {
    myBuilder.startNode(node);
    super.visitPyStatement(node);
  }

  @Override
  public void visitPyCallExpression(final PyCallExpression node) {
    final PyExpression callee = node.getCallee();
    // Flow abrupted
    if (callee != null && ("sys.exit".equals(PyUtil.getReadableRepr(callee, true)) ||
                           "fail".equals(callee.getName()))) {
      callee.accept(this);
      for (PyExpression expression : node.getArguments()) {
        expression.accept(this);
      }
      abruptFlow(node);
    }
    else {
      super.visitPyCallExpression(node);
    }
    if (node.isCalleeText(PyNames.ASSERT_IS_INSTANCE)) {
      final PyTypeAssertionEvaluator assertionEvaluator = new PyTypeAssertionEvaluator();
      node.accept(assertionEvaluator);
      InstructionBuilder.addAssertInstructions(myBuilder, assertionEvaluator);
    }
  }

  @Override
  public void visitPySubscriptionExpression(PySubscriptionExpression node) {
    myBuilder.startNode(node);
    node.getOperand().accept(this);
    final PyExpression expression = node.getIndexExpression();
    if (expression != null) {
      expression.accept(this);
    }
  }

  @Override
  public void visitPyReferenceExpression(final PyReferenceExpression node) {
    final PyExpression qualifier = node.getQualifier();
    if (qualifier != null) {
      qualifier.accept(this);
      if (!isSelf(qualifier)) {
        return;
      }
    }
    if (PyImportStatementNavigator.getImportStatementByElement(node) != null) {
      return;
    }

    final ReadWriteInstruction.ACCESS access = PyAugAssignmentStatementNavigator.getStatementByTarget(node) != null
                                               ? ReadWriteInstruction.ACCESS.READWRITE
                                               : ReadWriteInstruction.ACCESS.READ;
    final ReadWriteInstruction readWriteInstruction = ReadWriteInstruction.newInstruction(myBuilder, node, node.getName(), access);
    myBuilder.addNode(readWriteInstruction);
    myBuilder.checkPending(readWriteInstruction);
  }

  public static boolean isSelf(PsiElement qualifier) {
    PyFunction func = PsiTreeUtil.getParentOfType(qualifier, PyFunction.class);
    if (func == null || PsiTreeUtil.getParentOfType(func, PyClass.class) == null) {
      return false;
    }
    final PyParameter[] params = func.getParameterList().getParameters();
    if (params.length == 0) {
      return false;
    }
    final PyNamedParameter named = params[0].getAsNamed();
    if (named == null) {
      return false;
    }
    final String name = named.getName();
    return name != null && name.equals(qualifier.getText());
  }

  @Override
  public void visitPyAssignmentStatement(final PyAssignmentStatement node) {
    myBuilder.startNode(node);
    final PyExpression value = node.getAssignedValue();
    if (value != null) {
      value.accept(this);
    }
    for (PyExpression expression : node.getTargets()) {
      expression.accept(this);
    }
  }

  @Override
  public void visitPyAugAssignmentStatement(final PyAugAssignmentStatement node) {
    myBuilder.startNode(node);
    final PyExpression value = node.getValue();
    if (value != null) {
      value.accept(this);
    }
    node.getTarget().accept(this);
  }

  @Override
  public void visitPyTargetExpression(final PyTargetExpression node) {
    final PsiElement[] children = node.getChildren();
    // Case of non qualified reference
    if (children.length == 0 || (children.length == 1 && isSelf(children[0]))) {
      final ReadWriteInstruction.ACCESS access = node.getParent() instanceof PySliceExpression
                                                 ? ReadWriteInstruction.ACCESS.READ : ReadWriteInstruction.ACCESS.WRITE;
      final ReadWriteInstruction instruction = ReadWriteInstruction.newInstruction(myBuilder, node, node.getName(), access);
      myBuilder.addNode(instruction);
      myBuilder.checkPending(instruction);
    }
    else {
      for (PsiElement child : children) {
        child.accept(this);
      }
    }
  }

  @Override
  public void visitPyNamedParameter(final PyNamedParameter node) {
    final PyExpression defaultValue = node.getDefaultValue();
    if (defaultValue != null) {
      defaultValue.accept(this);
    }
    final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, node, node.getName());
    myBuilder.addNode(instruction);
    myBuilder.checkPending(instruction);
  }

  @Override
  public void visitPyImportStatement(final PyImportStatement node) {
    visitPyImportStatementBase(node);
  }

  @Override
  public void visitPyFromImportStatement(PyFromImportStatement node) {
    visitPyImportStatementBase(node);
  }

  private void visitPyImportStatementBase(PyImportStatementBase node) {
    myBuilder.startNode(node);
    for (PyImportElement importElement : node.getImportElements()) {
      final ReadWriteInstruction instruction = ReadWriteInstruction.write(myBuilder, importElement, importElement.getVisibleName());
      if (instruction != null) {
        myBuilder.addNode(instruction);
        myBuilder.checkPending(instruction);
      }
    }
  }

  private Instruction getPrevInstruction(final PyElement condition) {
    final Ref<Instruction> head = new Ref<Instruction>(myBuilder.prevInstruction);
    myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
      public void process(final PsiElement pendingScope, final Instruction instruction) {
        if (pendingScope != null && PsiTreeUtil.isAncestor(condition, pendingScope, false)) {
          head.set(instruction);
        }
        else {
          myBuilder.addPendingEdge(pendingScope, instruction);
        }
      }
    });
    return head.get();
  }

  @Override
  public void visitPyIfStatement(final PyIfStatement node) {
    myBuilder.startNode(node);
    final PyIfPart ifPart = node.getIfPart();
    PyExpression condition = ifPart.getCondition();
    PyTypeAssertionEvaluator assertionEvaluator = new PyTypeAssertionEvaluator();
    if (condition != null) {
      condition.accept(this);
      condition.accept(assertionEvaluator);
    }
    // Set the head as the last instruction of condition
    PyElement lastCondition = condition;
    Instruction lastBranchingPoint = getPrevInstruction(condition);
    myBuilder.prevInstruction = lastBranchingPoint;
    final PyStatementList thenStatements = ifPart.getStatementList();
    if (thenStatements != null) {
      myBuilder.startConditionalNode(thenStatements, condition, true);
      InstructionBuilder.addAssertInstructions(myBuilder, assertionEvaluator);
      thenStatements.accept(this);
      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          if (pendingScope != null && PsiTreeUtil.isAncestor(thenStatements, pendingScope, false)) {
            myBuilder.addPendingEdge(node, instruction);
          }
          else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    for (final PyIfPart part : node.getElifParts()) {
      // Set the head as the false branch
      myBuilder.prevInstruction = lastBranchingPoint;
      myBuilder.startConditionalNode(part, lastCondition, false);
      condition = part.getCondition();
      assertionEvaluator = new PyTypeAssertionEvaluator();
      if (condition != null) {
        lastCondition = condition;
        lastBranchingPoint = getPrevInstruction(lastCondition);
        condition.accept(this);
        condition.accept(assertionEvaluator);
      }
      // Set the head as the last instruction of condition
      myBuilder.prevInstruction = getPrevInstruction(lastCondition);
      myBuilder.startConditionalNode(part, lastCondition, true);
      final PyStatementList statementList = part.getStatementList();
      if (statementList != null) {
        InstructionBuilder.addAssertInstructions(myBuilder, assertionEvaluator);
        statementList.accept(this);
      }
      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          if (pendingScope != null && PsiTreeUtil.isAncestor(part, pendingScope, false)) {
            myBuilder.addPendingEdge(node, instruction);
          }
          else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    final PyElsePart elseBranch = node.getElsePart();
    if (elseBranch != null) {
      // Set the head as the false branch
      myBuilder.prevInstruction = lastBranchingPoint;
      myBuilder.startConditionalNode(elseBranch, lastCondition, false);
      elseBranch.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    } else {
      myBuilder.addPendingEdge(node, lastBranchingPoint);
    }
  }

  @Override
  public void visitPyWhileStatement(final PyWhileStatement node) {
    final Instruction instruction = myBuilder.startNode(node);
    final PyWhilePart whilePart = node.getWhilePart();
    final PyExpression condition = whilePart.getCondition();
    if (condition != null) {
      condition.accept(this);
    }
    myBuilder.prevInstruction = getPrevInstruction(condition);

    // if condition was false
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart == null) {
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }

    final PyStatementList statementList = whilePart.getStatementList();
    if (statementList != null) {
      myBuilder.startConditionalNode(statementList, condition, true);
      statementList.accept(this);
    }
    if (myBuilder.prevInstruction != null) {
      myBuilder.addEdge(myBuilder.prevInstruction, instruction); //loop
    }
    // else part
    if (elsePart != null) {
      myBuilder.startConditionalNode(statementList, condition, false);
      elsePart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    myBuilder.flowAbrupted();
    myBuilder.checkPending(instruction); //check for breaks targeted here
  }

  @Override
  public void visitPyForStatement(final PyForStatement node) {
    myBuilder.startNode(node);
    final PyForPart forPart = node.getForPart();
    final PyExpression source = forPart.getSource();
    if (source != null) {
      source.accept(this);
    }
    final Instruction head = myBuilder.prevInstruction;
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart == null) {
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }

    final PyStatementList list = forPart.getStatementList();
    if (list != null) {
      Instruction bodyInstruction = myBuilder.startNode(list);
      final PyExpression target = forPart.getTarget();
      if (target != null) {
        target.accept(this);
      }

      list.accept(this);

      if (myBuilder.prevInstruction != null) {
        myBuilder.addEdge(myBuilder.prevInstruction, bodyInstruction);  //loop
        myBuilder.addPendingEdge(node, myBuilder.prevInstruction); // exit
      }
      final Ref<Instruction> bodyInstRef = new Ref<Instruction>(bodyInstruction);
      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          if (pendingScope != null && PsiTreeUtil.isAncestor(list, pendingScope, false)) {
            myBuilder.addEdge(instruction, bodyInstRef.get());  //loop
            myBuilder.addPendingEdge(node, instruction); // exit
          }
          else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });
    }
    myBuilder.prevInstruction = head;
    if (elsePart != null) {
      elsePart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
    }
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyBreakStatement(final PyBreakStatement node) {
    myBuilder.startNode(node);
    final PyLoopStatement loop = node.getLoopStatement();
    if (loop != null) {
      myBuilder.addPendingEdge(loop, myBuilder.prevInstruction);
    }
    else {
      myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
    }
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyContinueStatement(final PyContinueStatement node) {
    myBuilder.startNode(node);
    final PyLoopStatement loop = node.getLoopStatement();
    if (loop != null) {
      final Instruction instruction = myBuilder.findInstructionByElement(loop);
      if (instruction != null) {
        myBuilder.addEdge(myBuilder.prevInstruction, instruction);
      }
      else {
        myBuilder.addPendingEdge(null, instruction);
      }
    }
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyRaiseStatement(final PyRaiseStatement node) {
    myBuilder.startNode(node);
    final PyExpression[] expressions = node.getExpressions();
    for (PyExpression expression : expressions) {
      expression.accept(this);
    }

    myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
      public void process(final PsiElement pendingScope, final Instruction instruction) {
        final PsiElement pendingElement = instruction.getElement();
        if (pendingElement != null && PsiTreeUtil.isAncestor(node, pendingElement, false)) {
          myBuilder.addEdge(null, instruction);
        }
        else {
          myBuilder.addPendingEdge(pendingScope, instruction);
        }
      }
    });
    myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
    myBuilder.flowAbrupted();
  }

  @Override
  public void visitPyReturnStatement(final PyReturnStatement node) {
    myBuilder.startNode(node);
    final PyExpression expression = node.getExpression();
    if (expression != null) {
      expression.accept(this);
    }
    abruptFlow(node);
  }

  @Override
  public void visitPyTryExceptStatement(final PyTryExceptStatement node) {
    myBuilder.startNode(node);

    // Process try part
    final PyTryPart tryPart = node.getTryPart();
    myBuilder.startNode(tryPart);
    tryPart.accept(this);

    // Goto else part after execution, or exit
    final PyElsePart elsePart = node.getElsePart();
    if (elsePart != null) {
      myBuilder.startNode(elsePart);
      elsePart.accept(this);
    }
    myBuilder.addPendingEdge(node, myBuilder.prevInstruction);

    // Process except parts
    final ArrayList<Instruction> exceptInstructions = new ArrayList<Instruction>();
    storeAndClearPending();
    for (PyExceptPart exceptPart : node.getExceptParts()) {
      myBuilder.flowAbrupted();
      final Instruction exceptInstrcution = myBuilder.startNode(exceptPart);
      exceptPart.accept(this);
      myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
      exceptInstructions.add(exceptInstrcution);
    }
    restorePending();

    final List<Instruction> normalExits = new ArrayList<Instruction>();
    final PyFinallyPart finallyPart = node.getFinallyPart();
    final Instruction finallyFailInstruction;

    // Store pending normal exit instructions from try-except-else parts
    if (finallyPart != null) {
      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          final PsiElement pendingElement = instruction.getElement();
          if (pendingElement != null) {
            final boolean isPending = PsiTreeUtil.isAncestor(node, pendingElement, false) &&
                                      !PsiTreeUtil.isAncestor(finallyPart, pendingElement, false);
            if (isPending && pendingScope != null) {
              normalExits.add(instruction);
            }
            else {
              myBuilder.addPendingEdge(pendingScope, instruction);
            }
          }
        }
      });
    }

    // Finally-fail part handling
    if (finallyPart != null) {
      myBuilder.flowAbrupted();
      finallyFailInstruction = myBuilder.startNode(finallyPart);
      finallyPart.accept(this);
      myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
      myBuilder.flowAbrupted();
    } else {
      finallyFailInstruction = null;
    }

    // Create exception edges
    for (Instruction instruction : myBuilder.instructions) {
      final PsiElement e = instruction.getElement();
      if (e == null || !canRaiseExceptions(instruction)) {
        continue;
      }
      // All instructions inside the try part have edges to except and finally parts
      if (PsiTreeUtil.getParentOfType(e, PyTryPart.class, false) == tryPart) {
        for (Instruction inst : exceptInstructions) {
          myBuilder.addEdge(instruction, inst);
        }
        if (finallyPart != null) {
          myBuilder.addEdge(instruction, finallyFailInstruction);
        }
      }
      if (finallyPart != null) {
        // All instructions inside except parts have edges to the finally part
        for (PyExceptPart exceptPart : node.getExceptParts()) {
          if (PsiTreeUtil.isAncestor(exceptPart, e, false)) {
            myBuilder.addEdge(instruction, finallyFailInstruction);
          }
        }
        // All instructions inside the else part have edges to the finally part
        if (PsiTreeUtil.isAncestor(elsePart, e, false)) {
          myBuilder.addEdge(instruction, finallyFailInstruction);
        }
      }
    }

    if (finallyPart != null) {
      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        @Override
        public void process(PsiElement pendingScope, Instruction instruction) {
          final PsiElement e = instruction.getElement();
          if (e != null) {
            // Change the scope of pending edges from finally-fail part to point to the last instruction
            if (PsiTreeUtil.isAncestor(finallyPart, e, false)) {
              myBuilder.addPendingEdge(null, instruction);
            }
            // Connect pending fail edges to the finally-fail part
            else if (pendingScope == null && PsiTreeUtil.isAncestor(node, e, false)) {
              myBuilder.addEdge(instruction, finallyFailInstruction);
            }
            else {
              myBuilder.addPendingEdge(pendingScope, instruction);
            }
          }
        }
      });

      // Duplicate CFG for finally (-fail and -success) only if there are some successfull exits from the
      // try part. Otherwise a single CFG for finally provides the correct control flow
      final Instruction finallyInstruction;
      if (!normalExits.isEmpty()) {
        // Finally-success part handling
        storeAndClearPending();
        myBuilder.flowAbrupted();
        Instruction finallySuccessInstruction = myBuilder.startNode(finallyPart);
        finallyPart.accept(this);
        restorePending();
        finallyInstruction = finallySuccessInstruction;
      }
      else {
        finallyInstruction = finallyFailInstruction;
      }

      // Connect normal exits from try and else parts to the finally part
      for (Instruction instr : normalExits) {
        myBuilder.addEdge(instr, finallyInstruction);
      }
    }
  }

  private void storeAndClearPending() {
    myPendindBackup = myBuilder.pending;
    myBuilder.pending = new ArrayList<Pair<PsiElement, Instruction>>();
  }

  private void restorePending() {
    for (Pair<PsiElement, Instruction> pair : myPendindBackup) {
      myBuilder.addPendingEdge(pair.first, pair.second);
    }
  }

  @Override
  public void visitPyComprehensionElement(final PyComprehensionElement node) {
    PyExpression prevCondition = null;
    myBuilder.startNode(node);
    List<Instruction> iterators = new ArrayList<Instruction>();

    for (ComprehensionComponent component : node.getComponents()) {
      if (component instanceof ComprhForComponent) {
        final ComprhForComponent c = (ComprhForComponent) component;
        final PyExpression iteratedList = c.getIteratedList();
        final PyExpression iteratorVariable = c.getIteratorVariable();
        if (prevCondition != null) {
          myBuilder.startConditionalNode(iteratedList, prevCondition, true);
          prevCondition = null;
        }
        else {
          myBuilder.startNode(iteratedList);
        }
        iteratedList.accept(this);

        // for-loop continue and exit
        for (Instruction i : iterators) {
          myBuilder.addEdge(myBuilder.prevInstruction, i);
        }
        myBuilder.addPendingEdge(node, myBuilder.prevInstruction);

        final Instruction iterator = myBuilder.startNode(iteratorVariable);
        iteratorVariable.accept(this);

        // Inner "for" and "if" constructs will be linked to all outer iterators
        iterators.add(iterator);
      }
      else if (component instanceof ComprhIfComponent) {
        final ComprhIfComponent c = (ComprhIfComponent) component;
        final PyExpression condition = c.getTest();
        if (condition == null) {
          continue;
        }
        if (prevCondition != null) {
          myBuilder.startConditionalNode(condition, prevCondition, true);
        }
        else {
          myBuilder.startNode(condition);
        }
        condition.accept(this);

        // Condition is true for nested "for" and "if" constructs, next startNode() should create a conditional node
        prevCondition = condition;

        // for-loop continue and exit
        for (Instruction i : iterators) {
          myBuilder.addEdge(myBuilder.prevInstruction, i);
        }
        myBuilder.addPendingEdge(node, myBuilder.prevInstruction);
      }
    }

    final PyExpression result = node.getResultExpression();
    if (result != null) {
      if (prevCondition != null) {
        myBuilder.startConditionalNode(result, prevCondition, true);
      }
      else {
        myBuilder.startNode(result);
      }
      result.accept(this);

      // for-loop continue
      for (Instruction i : iterators) {
        myBuilder.addEdge(myBuilder.prevInstruction, i);
      }
    }
  }

  public void visitPyAssertStatement(final PyAssertStatement node) {
    super.visitPyAssertStatement(node);
    final PyExpression[] args = node.getArguments();
    // assert False
    if (args.length == 1 && PyConstantExpressionEvaluator.evaluate(args[0]) == Boolean.FALSE) {
      abruptFlow(node);
      return;
    }
    PyTypeAssertionEvaluator evaluator = new PyTypeAssertionEvaluator();
    node.acceptChildren(evaluator);
    InstructionBuilder.addAssertInstructions(myBuilder, evaluator);
  }

  @Override
  public void visitPyLambdaExpression(final PyLambdaExpression node) {
    myBuilder.startNode(node);
  }

  @Override
  public void visitPyWithStatement(final PyWithStatement node) {
    boolean withSelfAssertRaises = false;
    final PyWithItem[] items = node.getWithItems();
    if (items.length == 1){
      final PyWithItem item = items[0];
      final ASTNode callNode = item.getNode().findChildByType(CALL_OR_REF_EXPR);
      if (callNode != null) {
        final PsiElement element = callNode.getPsi();
        if (element instanceof PyCallExpression) {
          withSelfAssertRaises = ((PyCallExpression)element).isCalleeText(SELF_ASSERT_RAISES);
        }
        if (element instanceof PyReferenceExpression){
          withSelfAssertRaises = SELF_ASSERT_RAISES.equals(element.getText());
        }
      }
    }
    super.visitPyWithStatement(node);
    if (withSelfAssertRaises){
      myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
        public void process(final PsiElement pendingScope, final Instruction instruction) {
          final PsiElement element = instruction.getElement();
          if (element == null){
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
          else if (PsiTreeUtil.getParentOfType(element, PyRaiseStatement.class) != null){
            myBuilder.addPendingEdge(node, instruction);
          }
          else {
            myBuilder.addPendingEdge(pendingScope, instruction);
          }
        }
      });
    }
  }

  private void abruptFlow(final PsiElement node) {
    // Here we process pending instructions!!!
    myBuilder.processPending(new ControlFlowBuilder.PendingProcessor() {
      public void process(final PsiElement pendingScope, final Instruction instruction) {
        if (pendingScope != null && PsiTreeUtil.isAncestor(node, pendingScope, false)) {
          myBuilder.addPendingEdge(null, instruction);
        }
        else {
          myBuilder.addPendingEdge(pendingScope, instruction);
        }
      }
    });
    myBuilder.addPendingEdge(null, myBuilder.prevInstruction);
    myBuilder.flowAbrupted();
  }

  private static boolean canRaiseExceptions(final Instruction instruction) {
    if (instruction instanceof ReadWriteInstruction) {
      return true;
    }
    return !PsiTreeUtil.instanceOf(instruction.getElement(),
                                   PyStatementList.class);
  }
}

