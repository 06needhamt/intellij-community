package com.jetbrains.python.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.FP;
import com.jetbrains.python.toolbox.RepeatIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class PyAssignmentStatementImpl extends PyElementImpl implements PyAssignmentStatement {
  private PyExpression[] myTargets;

  public PyAssignmentStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAssignmentStatement(this);
  }

  public PyExpression[] getTargets() {
    if (myTargets == null) {
      myTargets = calcTargets();
    }
    return myTargets;
  }

  private PyExpression[] calcTargets() {
    final ASTNode[] eqSigns = getNode().getChildren(TokenSet.create(PyTokenTypes.EQ));
    if (eqSigns.length == 0) {
      return PyExpression.EMPTY_ARRAY;
    }
    final ASTNode lastEq = eqSigns[eqSigns.length - 1];
    List<PyExpression> candidates = new ArrayList<PyExpression>();
    ASTNode node = getNode().getFirstChildNode();
    while (node != null && node != lastEq) {
      final PsiElement psi = node.getPsi();
      if (psi instanceof PyExpression) {
        addCandidate(candidates, (PyExpression)psi);
      }
      node = node.getTreeNext();
    }
    List<PyExpression> targets = new ArrayList<PyExpression>();
    for (PyExpression expr : candidates) { // only filter out targets
      if (expr instanceof PyTargetExpression ||
          expr instanceof PyReferenceExpression ||
          expr instanceof PySubscriptionExpression ||
          expr instanceof PySliceExpression) {
        targets.add(expr);
      }
    }
    return targets.toArray(new PyExpression[targets.size()]);
  }

  private static void addCandidate(List<PyExpression> candidates, PyExpression psi) {
    if (psi instanceof PyParenthesizedExpression) {
      addCandidate(candidates, ((PyParenthesizedExpression)psi).getContainedExpression());
    }
    else if (psi instanceof PySequenceExpression) {
      final PyExpression[] pyExpressions = ((PySequenceExpression)psi).getElements();
      for (PyExpression pyExpression : pyExpressions) {
        addCandidate(candidates, pyExpression);
      }
    }
    else if (psi instanceof PyStarExpression) {
      final PyExpression expression = ((PyStarExpression)psi).getExpression();
      if (expression != null) {
        addCandidate(candidates, expression);
      }
    }
    else {
      candidates.add(psi);
    }
  }

  /**
   * @return rightmost expression in statement, which is supposedly the assigned value, or null.
   */
  @Nullable
  public PyExpression getAssignedValue() {
    PsiElement child = getLastChild();
    while (child != null && !(child instanceof PyExpression)) {
      if (child instanceof PsiErrorElement) return null; // incomplete assignment operator can't be analyzed properly, bail out.
      child = child.getPrevSibling();
    }
    return (PyExpression)child;
  }

  @NotNull
  public List<Pair<PyExpression, PyExpression>> getTargetsToValuesMapping() {
    List<Pair<PyExpression, PyExpression>> ret = new SmartList<Pair<PyExpression, PyExpression>>();
    if (!PsiTreeUtil.hasErrorElements(this)) { // no parse errors
      PyExpression[] constituents = PsiTreeUtil.getChildrenOfType(this, PyExpression.class); // "a = b = c" -> [a, b, c]
      if (constituents != null && constituents.length > 1) {
        PyExpression rhs = constituents[constituents.length - 1]; // last
        List<PyExpression> lhses = Lists.newArrayList(constituents);
        if (lhses.size()>0) lhses.remove(lhses.size()-1); // copy all but last; most often it's one element.
        for (PyExpression lhs : lhses) mapToValues(lhs, rhs, ret);
      }
    }
    return ret;
  }

  @Nullable
  public PyExpression getLeftHandSideExpression() {
    PsiElement child = getFirstChild();
    while (child != null && !(child instanceof PyExpression)) {
      if (child instanceof PsiErrorElement) return null; // incomplete assignment operator can't be analyzed properly, bail out.
      child = child.getPrevSibling();
    }
    return (PyExpression)child;
  }

  private static void mapToValues(PyExpression lhs, PyExpression rhs, List<Pair<PyExpression, PyExpression>> map) {
    // cast for convenience
    PySequenceExpression lhs_tuple = null;
    PyExpression lhs_one = null;
    if (lhs instanceof PySequenceExpression) lhs_tuple = (PySequenceExpression)lhs;
    else if (lhs != null) lhs_one = lhs;
    
    PySequenceExpression rhs_tuple = null;
    PyExpression rhs_one = null;
    if (rhs instanceof PySequenceExpression) rhs_tuple = (PySequenceExpression)rhs;
    else if (rhs != null) rhs_one = rhs;
    //
    if (lhs_one != null) { // single LHS, single RHS (direct mapping) or multiple RHS (packing)
       map.add(new Pair<PyExpression, PyExpression>(lhs_one, rhs));
    }
    else if (lhs_tuple != null && rhs_one != null) { // multiple LHS, single RHS: unpacking
      //for (PyExpression tuple_elt : lhs_tuple.getElements()) map.add(new Pair<PyExpression, PyExpression>(tuple_elt, rhs_one));
      map.addAll(FP.zipList(Arrays.asList(lhs_tuple.getElements()), new RepeatIterable<PyExpression>(rhs_one)));
    }
    else if (lhs_tuple != null && rhs_tuple != null) { // multiple both sides: piecewise mapping
      map.addAll(FP.zipList(Arrays.asList(lhs_tuple.getElements()), Arrays.asList(rhs_tuple.getElements()), null, null));
    }
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    return new ArrayList<PyElement>(PyUtil.flattenedParensAndStars(getTargets()));
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return true; // a = a+1 resolves 'a' outside itself.
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myTargets = null;
  }
}
