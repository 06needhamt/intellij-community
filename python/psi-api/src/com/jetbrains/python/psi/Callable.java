package com.jetbrains.python.psi;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Something that can be called, passed parameters to, and return something back.

 * @author dcheryasov
 */
public interface Callable extends PyTypedElement {

  /**
   * @return a list of parameters passed to this callable, possibly empty.
   */
  @NotNull
  PyParameterList getParameterList();

  /**
   * @return the type of returned value.
   */
  @Nullable
  PyType getReturnType(@NotNull TypeEvalContext context, @Nullable PyQualifiedExpression callSite);

  /**
   * @return a methods returns itself, non-method callables return null.
   */
  @Nullable
  PyFunction asMethod();

  /**
   * Returns the qualified name of the function.
   *
   * @return the qualified name of the function, or null for a lambda expression.
   */
  @Nullable
  String getQualifiedName();
}
