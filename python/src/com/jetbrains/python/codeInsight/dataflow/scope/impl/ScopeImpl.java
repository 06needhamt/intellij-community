package com.jetbrains.python.codeInsight.dataflow.scope.impl;

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInsight.dataflow.DFALimitExceededException;
import com.intellij.codeInsight.dataflow.map.DFAMap;
import com.intellij.codeInsight.dataflow.map.DFAMapEngine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.PyReachingDefsDfaInstance;
import com.jetbrains.python.codeInsight.dataflow.PyReachingDefsSemilattice;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeVariable;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author oleg
 */
public class ScopeImpl implements Scope {
  private volatile Instruction[] myFlow;
  private volatile List<DFAMap<ScopeVariable>> myCachedScopeVariables;
  private Set<String> myGlobals;
  private Set<String> myNonlocals;
  private final ScopeOwner myFlowOwner;
  private Set<String> myAllNames;
  private Map<String, PsiNamedElement> myNamedElements;
  private List<NameDefiner> myNameDefiners;  // declarations which declare unknown set of names, such as 'from ... import *'
  private static final Logger LOG = Logger.getInstance(ScopeImpl.class.getName());

  public ScopeImpl(final ScopeOwner flowOwner) {
    myFlowOwner = flowOwner;
  }

  private synchronized void computeFlow() {
    if (myFlow == null) {
      myFlow = ControlFlowCache.getControlFlow(myFlowOwner).getInstructions();
    }
  }

  public ScopeVariable getDeclaredVariable(@NotNull final PsiElement anchorElement,
                                           @NotNull final String name) throws DFALimitExceededException {
    computeScopeVariables();
    for (int i = 0; i < myFlow.length; i++) {
      Instruction instruction = myFlow[i];
      final PsiElement element = instruction.getElement();
      if (element == anchorElement) {
        return myCachedScopeVariables.get(i).get(name);
      }
    }
    return null;
  }

  private synchronized List<DFAMap<ScopeVariable>> computeScopeVariables() throws DFALimitExceededException {
    computeFlow();
    if (myCachedScopeVariables == null) {
      final PyReachingDefsDfaInstance dfaInstance = new PyReachingDefsDfaInstance();
      final PyReachingDefsSemilattice semilattice = new PyReachingDefsSemilattice();
      final DFAMapEngine<ScopeVariable> engine = new DFAMapEngine<ScopeVariable>(myFlow, dfaInstance, semilattice);
      myCachedScopeVariables = engine.performDFA();
    }
    return myCachedScopeVariables;
  }

  public boolean isGlobal(final String name) {
    if (myGlobals == null){
      myGlobals = computeGlobals(myFlowOwner);
    }
    return myGlobals.contains(name);
  }

  public boolean isNonlocal(final String name) {
    if (myNonlocals == null){
      myNonlocals = computeNonlocals(myFlowOwner);
    }
    return myNonlocals.contains(name);
  }

  public boolean containsDeclaration(final String name) {
    if (myAllNames == null){
      myAllNames = computeAllNames();
    }
    return myAllNames.contains(name) && !isNonlocal(name);
  }

  @NotNull
  @Override
  public List<NameDefiner> getNameDefiners() {
    if (myNamedElements == null) {
      collectDeclarations();
    }
    return myNameDefiners;
  }

  @Nullable
  @Override
  public PsiNamedElement getNamedElement(String name) {
    if (myNamedElements == null) {
      collectDeclarations();
    }
    return myNamedElements.get(name);
  }

  private void collectDeclarations() {
    final Map<String, PsiNamedElement> namedElements = new HashMap<String, PsiNamedElement>();
    final List<NameDefiner> nameDefiners = new ArrayList<NameDefiner>();
    myFlowOwner.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyTargetExpression(PyTargetExpression node) {
        final PsiElement parent = node.getParent();
        if (!(parent instanceof PyImportElement)) {
          super.visitPyTargetExpression(node);
        }
      }

      @Override
      public void visitPyElement(PyElement node) {
        if (node instanceof PsiNamedElement) {
          namedElements.put(node.getName(), (PsiNamedElement)node);
        }
        if (node instanceof NameDefiner && !(node instanceof PsiNamedElement)) {
          nameDefiners.add((NameDefiner)node);
        }
        if (!(node instanceof ScopeOwner)) {
          super.visitPyElement(node);
        }
      }
    });
    myNamedElements = namedElements;
    myNameDefiners = nameDefiners;
  }

  private Set<String> computeAllNames() {
    computeFlow();
    final Set<String> names = new HashSet<String>();
    for (Instruction instruction : myFlow) {
      if (instruction instanceof ReadWriteInstruction && ((ReadWriteInstruction)instruction).getAccess().isWriteAccess()){
        names.add(((ReadWriteInstruction)instruction).getName());
      }
    }
    return names;
  }

  private static Set<String> computeGlobals(final PsiElement owner) {
    final Set<String> names = new HashSet<String>();
    owner.accept(new PyRecursiveElementVisitor(){
      @Override
      public void visitPyGlobalStatement(final PyGlobalStatement node) {
        for (PyTargetExpression expression : node.getGlobals()) {
          names.add(expression.getReferencedName());
        }
      }
    });
    return names;
  }

  private static Set<String> computeNonlocals(final ScopeOwner owner) {
    final Set<String> names = new HashSet<String>();
    owner.accept(new PyRecursiveElementVisitor(){
      @Override
      public void visitPyNonlocalStatement(final PyNonlocalStatement node) {
        if (ScopeUtil.getScopeOwner(node) == owner) {
          for (PyTargetExpression expression : node.getVariables()) {
            names.add(expression.getReferencedName());
          }
        }
      }
    });
    return names;
  }
}
