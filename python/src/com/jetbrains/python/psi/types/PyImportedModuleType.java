package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyImportedModuleType implements PyType {
  @NotNull private PyImportedModule myImportedModule;

  public PyImportedModuleType(@NotNull PyImportedModule importedModule) {
    myImportedModule = importedModule;
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(String name,
                                                          PyExpression location,
                                                          AccessDirection direction,
                                                          PyResolveContext resolveContext) {
    final PyFile file = myImportedModule.resolve();
    if (file != null) {
      return new PyModuleType(file).resolveMember(name, location, direction, resolveContext);
    }
    return null;
  }

  public Object[] getCompletionVariants(String completionPrefix, PyExpression location, ProcessingContext context) {
    List<LookupElement> result = new ArrayList<LookupElement>();
    final List<PyImportElement> importTargets = myImportedModule.getContainingFile().getImportTargets();
    final int imported = myImportedModule.getImportedPrefix().getComponentCount();
    for (PyImportElement importTarget : importTargets) {
      final PyQualifiedName qName = importTarget.getImportedQName();
      if (qName != null && qName.matchesPrefix(myImportedModule.getImportedPrefix())) {
        final List<String> components = qName.getComponents();
        if (components.size() > imported) {
          String module = components.get(imported);
          result.add(LookupElementBuilder.create(module));
        }
      }
    }
    return result.toArray(new Object[result.size()]);
  }

  public String getName() {
    return "imported module " + myImportedModule.getImportedPrefix().toString();
  }

  @Override
  public boolean isBuiltin(TypeEvalContext context) {
    return false;  // no module can be imported from builtins
  }

  @NotNull
  public PyImportedModule getImportedModule() {
    return myImportedModule;
  }
}
