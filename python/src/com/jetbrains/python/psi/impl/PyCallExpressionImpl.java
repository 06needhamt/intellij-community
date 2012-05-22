package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author yole
 */
public class PyCallExpressionImpl extends PyElementImpl implements PyCallExpression {

  public PyCallExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyCallExpression(this);
  }

  @Nullable
  public PyExpression getCallee() {
    // peel off any parens, because we may have smth like (lambda x: x+1)(2)
    PsiElement seeker = getFirstChild();
    while (seeker instanceof PyParenthesizedExpression) seeker = ((PyParenthesizedExpression)seeker).getContainedExpression();
    return seeker instanceof PyExpression ? (PyExpression) seeker : null;
  }

  public PyArgumentList getArgumentList() {
    return PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
  }

  @NotNull
  public PyExpression[] getArguments() {
    final PyArgumentList argList = getArgumentList();
    return argList != null ? argList.getArguments() : PyExpression.EMPTY_ARRAY;
  }

  @Override
  public <T extends PsiElement> T getArgument(int index, Class<T> argClass) {
    PyExpression[] args = getArguments();
    return args.length > index && argClass.isInstance(args[index]) ? argClass.cast(args[index]) : null;
  }

  @Override
  public <T extends PsiElement> T getArgument(int index, String keyword, Class<T> argClass) {
    final PyExpression argument = getKeywordArgument(keyword);
    if (argument != null) {
      return argClass.isInstance(argument) ? argClass.cast(argument) : null;
    }
    return getArgument(index, argClass);
  }

  @Override
  public PyExpression getKeywordArgument(String keyword) {
    return PyCallExpressionHelper.getKeywordArgument(this, keyword);
  }

  public void addArgument(PyExpression expression) {
    PyCallExpressionHelper.addArgument(this, expression);
  }

  public PyMarkedCallee resolveCallee(PyResolveContext resolveContext) {
    return PyCallExpressionHelper.resolveCallee(this, resolveContext);
  }

  @Override
  public Callable resolveCalleeFunction(PyResolveContext resolveContext) {
    return PyCallExpressionHelper.resolveCalleeFunction(this, resolveContext);
  }

  public PyMarkedCallee resolveCallee(PyResolveContext resolveContext, int offset) {
    return PyCallExpressionHelper.resolveCallee(this, resolveContext, offset);
  }

  public boolean isCalleeText(@NotNull String... nameCandidates) {
    return PyCallExpressionHelper.isCalleeText(this, nameCandidates);
  }

  @Override
  public String toString() {
    return "PyCallExpression: " + PyUtil.getReadableRepr(getCallee(), true); //or: getCalledFunctionReference().getReferencedName();
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    if (!TypeEvalStack.mayEvaluate(this)) {
      return null;
    }
    try {
      PyExpression callee = getCallee();
      if (callee instanceof PyReferenceExpression) {
        // hardwired special cases
        if (PyNames.SUPER.equals(callee.getText())) {
          final Maybe<PyType> superCallType = getSuperCallType(callee, context);
          if (superCallType.isDefined()) {
            return superCallType.value();
          }
        }
        // normal cases
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
        ResolveResult[] targets = ((PyReferenceExpression)callee).getReference(resolveContext).multiResolve(false);
        if (targets.length > 0) {
          PsiElement target = targets[0].getElement();
          if (target == null) {
            return null;
          }
          PyClass cls = null;
          PyFunction init = null;
          if (target instanceof PyClass) {
            cls = (PyClass)target;
            init = cls.findInitOrNew(true);
          }
          else if (target instanceof PyFunction) {
            final PyFunction f = (PyFunction)target;
            if (PyNames.INIT.equals(f.getName())) {
              init = f;
              cls = f.getContainingClass();
            }
          }
          if (init != null) {
            final PyType t = init.getReturnType(context, (PyReferenceExpression)callee);
            if (cls != null) {
              if (init.getContainingClass() != cls) {
                if (t instanceof PyCollectionType) {
                  final PyType elementType = ((PyCollectionType)t).getElementType(context);
                  return new PyCollectionTypeImpl(cls, false, elementType);
                }
                return new PyClassType(cls, false);
              }
            }
            if (t != null && !(t instanceof PyNoneType)) {
              return t;
            }
          }
          if (cls != null) {
            return new PyClassType(cls, false);
          }
          final PyType providedType = PyReferenceExpressionImpl.getReferenceTypeFromProviders(target, context, this);
          if (providedType != null) {
            return providedType;
          }
          if (target instanceof Callable) {
            final Callable callable = (Callable)target;
            PyType returnType = callable.getReturnType(context, (PyReferenceExpression)callee);
            if (returnType != null) {
              return returnType;
            }
            return new PyReturnTypeReference(callable);
          }
        }
      }
      if (callee == null) {
        return null;
      }
      else {
        final PyType type = context.getType(callee);
        if (type instanceof PyCallableType) {
          return ((PyCallableType) type).getCallType();
        }
        return null;
      }
    }
    finally {
      TypeEvalStack.evaluated(this);
    }
  }

  @NotNull
  private Maybe<PyType> getSuperCallType(PyExpression callee, TypeEvalContext context) {
    PsiElement must_be_super_init = ((PyReferenceExpression)callee).getReference().resolve();
    if (must_be_super_init instanceof PyFunction) {
      PyClass must_be_super = ((PyFunction)must_be_super_init).getContainingClass();
      if (must_be_super == PyBuiltinCache.getInstance(this).getClass(PyNames.SUPER)) {
        PyArgumentList arglist = getArgumentList();
        if (arglist != null) {
          final PyClass containingClass = PsiTreeUtil.getParentOfType(this, PyClass.class);
          PyExpression[] args = arglist.getArguments();
          if (args.length > 1) {
            PyExpression first_arg = args[0];
            if (first_arg instanceof PyReferenceExpression) {
              final PyReferenceExpression firstArgRef = (PyReferenceExpression)first_arg;
              final PyExpression qualifier = firstArgRef.getQualifier();
              if (qualifier != null && PyNames.CLASS.equals(firstArgRef.getReferencedName())) {
                final PsiReference qRef = qualifier.getReference();
                final PsiElement element = qRef == null ? null : qRef.resolve();
                if (element instanceof PyParameter) {
                  final PyParameterList parameterList = PsiTreeUtil.getParentOfType(element, PyParameterList.class);
                  if (parameterList != null && element == parameterList.getParameters()[0]) {
                    return new Maybe<PyType>(getSuperCallType(context, containingClass, args[1]));
                  }
                }
              }
              PsiElement possible_class = firstArgRef.getReference().resolve();
              if (possible_class instanceof PyClass && ((PyClass)possible_class).isNewStyleClass()) {
                final PyClass first_class = (PyClass)possible_class;
                return new Maybe<PyType>(getSuperCallType(context, first_class, args[1]));
              }
            }
          }
          else if (((PyFile)getContainingFile()).getLanguageLevel().isPy3K() && containingClass != null) {
            return new Maybe<PyType>(getSuperClassUnionType(containingClass));
          }
        }
      }
    }
    return new Maybe<PyType>();
  }

  @Nullable
  private static PyType getSuperCallType(TypeEvalContext context, PyClass firstClass, PyExpression second_arg) {
    // check 2nd argument, too; it should be an instance
    if (second_arg != null) {
      PyType second_type = context.getType(second_arg);
      if (second_type instanceof PyClassType) {
        // imitate isinstance(second_arg, possible_class)
        PyClass secondClass = ((PyClassType)second_type).getPyClass();
        if (CompletionUtil.getOriginalOrSelf(firstClass) == secondClass) {
          return getSuperClassUnionType(firstClass);
        }
        if (secondClass != null && secondClass.isSubclass(firstClass)) {
          final Iterator<PyClass> iterator = firstClass.iterateAncestorClasses().iterator();
          if (iterator.hasNext()) {
            return new PyClassType(iterator.next(), false); // super(Foo, self) has type of Foo, modulo __get__()
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static PyType getSuperClassUnionType(@NotNull PyClass pyClass) {
    // TODO: this is closer to being correct than simply taking first superclass type but still not entirely correct;
    // super can also delegate to sibling types
    // TODO handle __mro__ here
    final PyClass[] supers = pyClass.getSuperClasses();
    if (supers.length > 0) {
      if (supers.length == 1) {
        return new PyClassType(supers[0], false);
      }
      List<PyType> superTypes = new ArrayList<PyType>();
      for (PyClass aSuper : supers) {
        superTypes.add(new PyClassType(aSuper, false));
      }
      return PyUnionType.union(superTypes);
    }
    return null;
  }
}
