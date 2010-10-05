package com.jetbrains.python.psi.search;

import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyOverridingMethodsSearchExecutor implements QueryExecutor<PyFunction, PyOverridingMethodsSearch.SearchParameters> {
  public boolean execute(@NotNull final PyOverridingMethodsSearch.SearchParameters queryParameters, @NotNull final Processor<PyFunction> consumer) {
    final PyFunction baseMethod = queryParameters.getFunction();
    PyClass containingClass = baseMethod.getContainingClass();
    return PyClassInheritorsSearch.search(containingClass, queryParameters.isCheckDeep()).forEach(new Processor<PyClass>() {
      public boolean process(final PyClass pyClass) {
        PyFunction overridingMethod = pyClass.findMethodByName(baseMethod.getName(), false);
        //noinspection SimplifiableIfStatement
        if (overridingMethod != null) {
          return consumer.process(overridingMethod);
        }
        return true;
      }
    });
  }
}
