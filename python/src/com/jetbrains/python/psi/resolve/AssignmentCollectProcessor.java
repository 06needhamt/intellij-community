package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AssignmentCollectProcessor implements PsiScopeProcessor {
  /**
   * Collects all assignments in context above given element, if they match given naming pattern.
   * Used to track creation of attributes by assignment (e.g in constructor).
   */
  private final PyQualifiedName myQualifier;
  private final List<PyExpression> myResult;
  private final Set<String> mySeenNames;

  /**
   * Creates an instance to collect assignments of attributes to the object identified by 'qualifier'.
   * E.g. if qualifier = {"foo", "bar"} then assignments like "foo.bar.baz = ..." will be considered.
   * The collection continues up to the point of latest redefinition of the object identified by 'qualifier',
   * that is, up to the point of something like "foo.bar = ..." or "foo = ...".
   *
   * @param qualifier qualifying names, outermost first; must not be empty.
   */
  public AssignmentCollectProcessor(@NotNull PyQualifiedName qualifier) {
    assert qualifier.getComponentCount() > 0;
    myQualifier = qualifier;
    myResult = new ArrayList<PyExpression>();
    mySeenNames = new HashSet<String>();
  }

  public boolean execute(final PsiElement element, final ResolveState state) {
    if (element instanceof PyAssignmentStatement) {
      final PyAssignmentStatement assignment = (PyAssignmentStatement)element;
      for (PyExpression ex : assignment.getTargets()) {
        if (ex instanceof PyTargetExpression) {
          final PyTargetExpression target = (PyTargetExpression)ex;
          List<PyExpression> qualsExpr = PyResolveUtil.unwindQualifiers(target);
          PyQualifiedName qualifiedName = PyQualifiedName.fromReferenceChain(qualsExpr);
          if (qualifiedName != null) {
            if (qualifiedName.getComponentCount() == myQualifier.getComponentCount() + 1 && qualifiedName.matchesPrefix(myQualifier)) {
              // a new attribute follows last qualifier; collect it.
              PyExpression last_elt = qualsExpr.get(qualsExpr.size() - 1); // last item is the outermost, new, attribute.
              String last_elt_name = last_elt.getName();
              if (!mySeenNames.contains(last_elt_name)) { // no dupes, only remember the latest
                myResult.add(last_elt);
                mySeenNames.add(last_elt_name);
              }
            }
            else if (qualifiedName.getComponentCount() < myQualifier.getComponentCount() + 1 && myQualifier.matchesPrefix(qualifiedName)) {
              // qualifier(s) get redefined; collect no more.
              return false;
            }
          }
        }

      }
    }
    return true; // nothing interesting found, continue
  }

  /**
   * @return a collection of expressions (parts of assignment expressions) where new attributes were defined. E.g. for "a.b.c = 1",
   *         the expression for 'c' is in the result.
   */
  @NotNull
  public Collection<PyExpression> getResult() {
    return myResult;
  }

  public <T> T getHint(final Key<T> hintKey) {
    return null;
  }

  public void handleEvent(final Event event, final Object associated) {
    // empty
  }

}
