package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.util.Processor;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyClassStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a class declaration in source.
 */
public interface PyClass extends PsiNamedElement, PyStatement, NameDefiner, PyDocStringOwner, StubBasedPsiElement<PyClassStub>, ScopeOwner {
  @Nullable
  ASTNode getNameNode();

  @NotNull
  PyStatementList getStatementList();

  @NotNull
  PyExpression[] getSuperClassExpressions();

  @NotNull
  PsiElement[] getSuperClassElements();       

  @NotNull
  PyClass[] getSuperClasses();

  @NotNull
  PyFunction[] getMethods();

  /**
   * Finds a method with given name.
   * @param name what to look for
   * @param inherited true: search in superclasses; false: only look for methods defined in this class.
   * @return
   */
  @Nullable
  PyFunction findMethodByName(@NotNull @NonNls final String name, boolean inherited);

  /**
   * Finds either __init__ or __new__, whichever is defined for given class.
   * If __init__ is defined, it is found first. This mimics the way initialization methods
   * are searched for and called by Python when a constructor call is made.
   * Since __new__ only makes sense for new-style classes, an old-style class never finds it with this method.
   * @param inherited true: search in superclasses, too.
   * @return a method that would be called first when an instance of this class is instantiated.
   */
  @Nullable
  PyFunction findInitOrNew(boolean inherited);

  /**
   * Apply a processor to every method, looking at superclasses in method resolution order as needed.
   * @param processor what to apply
   * @param inherited true: search in superclasses, too.
   */
  boolean scanMethods(Processor<PyFunction> processor, boolean inherited);

  PyTargetExpression[] getClassAttributes();

  PyTargetExpression[] getInstanceAttributes();

  /**
   * @return true if the class is new-style and descends from 'object'.
   */
  boolean isNewStyleClass();

  /**
   * A lazy way to list ancestor classes width first, in method-resolution order (MRO).
   * @return an iterable of ancestor classes.
   */
  Iterable<PyClass> iterateAncestors();

  /**
   * @param parent
   * @return True iff this and parent are the same or parent is one of our superclasses.
   */
  boolean isSubclass(PyClass parent);

  @Nullable
  PyDecoratorList getDecoratorList();

  String getQualifiedName();

  /**
   * Returns true if the class is a top-level class (its parent is its containing file).
   *
   * @return true if the class is top-level, false otherwise.
   */
  boolean isTopLevel();
}
