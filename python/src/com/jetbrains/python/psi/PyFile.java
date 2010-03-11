package com.jetbrains.python.psi;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PyFile extends PyElement, PsiFile, PyDocStringOwner, ScopeOwner, NameDefiner {
  
  Key<Boolean> KEY_IS_DIRECTORY = Key.create("Dir impersonated by __init__.py");
  Key<Boolean> KEY_EXCLUDE_BUILTINS = Key.create("Don't include builtins to processDeclaration results");

  List<PyStatement> getStatements();

  List<PyClass> getTopLevelClasses();

  List<PyFunction> getTopLevelFunctions();

  List<PyTargetExpression> getTopLevelAttributes();

  /**
   * Looks for a name exported by this file, preferably in an efficient way.
   * TODO[yole] this behaves differently in stub-based and AST-based mode: in stub-based mode, it returns the import element for
   * an imported name, in AST-based - the actual element referenced by the import
   *
   * @param name what to find
   * @return found element, or null.
   */
  @Nullable
  PsiElement findExportedName(String name);

  /**
  @return an URL of file, maybe bogus if virtual file is not present.
  */
  @NotNull
  String getUrl();

  @Nullable
  PyFunction findTopLevelFunction(String name);

  @Nullable
  PyClass findTopLevelClass(String name);

  LanguageLevel getLanguageLevel();

  List<PyFromImportStatement> getFromImports();
}
