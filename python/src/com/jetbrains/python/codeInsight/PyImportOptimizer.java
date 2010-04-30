package com.jetbrains.python.codeInsight;

import com.intellij.lang.ImportOptimizer;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyImportOptimizer implements ImportOptimizer {
  public boolean supports(PsiFile file) {
    return true;
  }

  @NotNull
  public Runnable processFile(PsiFile file) {
    final PyUnresolvedReferencesInspection.Visitor visitor = new PyUnresolvedReferencesInspection.Visitor(null);
    file.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyElement(PyElement node) {
        super.visitPyElement(node);
        node.accept(visitor);
      }
    });
    return new Runnable() {
      public void run() {
        visitor.optimizeImports();
      }
    };
  }
}
