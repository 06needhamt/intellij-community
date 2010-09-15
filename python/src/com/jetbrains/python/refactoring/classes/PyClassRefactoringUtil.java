package com.jetbrains.python.refactoring.classes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.actions.AddImportHelper;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dennis.Ushakov
 */
public class PyClassRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(PyClassRefactoringUtil.class.getName());

  private PyClassRefactoringUtil() {}

  public static void moveSuperclasses(PyClass clazz, Set<String> superClasses, PyClass superClass) {
    if (superClasses.size() == 0) return;
    final Project project = clazz.getProject();
    final List<PyExpression> toAdd = removeAndGetSuperClasses(clazz, superClasses);
    addSuperclasses(project, superClass, toAdd, superClasses);
  }

  public static void addSuperclasses(Project project, PyClass superClass,
                                     @Nullable Collection<PyExpression> superClassesAsPsi,
                                     Collection<String> superClassesAsStrings) {
    if (superClassesAsStrings.size() == 0) return;
    PyArgumentList argList = superClass.getSuperClassExpressionList();
    if (argList != null) {
      if (superClassesAsPsi != null) {
        for (PyExpression element : superClassesAsPsi) {
          argList.addArgument(element);
        }
      }
      else {
        for (String s : superClassesAsStrings) {
          final PyExpression expr = PyElementGenerator.getInstance(project).createExpressionFromText(s);
          argList.addArgument(expr);
        }
      }
    } else {
      addSuperclasses(project, superClass, superClassesAsStrings);
    }
  }

  public static List<PyExpression> removeAndGetSuperClasses(PyClass clazz, Set<String> superClasses) {
    if (superClasses.size() == 0) return Collections.emptyList();
    final List<PyExpression> toAdd = new ArrayList<PyExpression>();
    final PyExpression[] elements = clazz.getSuperClassExpressions();
    for (PyExpression element : elements) {
      if (superClasses.contains(element.getText())) {
        toAdd.add(element);
        PyUtil.removeListNode(element);
      }
    }
    return toAdd;
  }

  public static void addSuperclasses(Project project, PyClass superClass, Collection<String> superClasses) {
    if (superClasses.size() == 0) return;
    final StringBuilder builder = new StringBuilder("(");
    boolean hasChanges = false;
    for (String element : superClasses) {
      if (builder.length() > 1) builder.append(",");
      if (!alreadyHasSuperClass(superClass, element)) {
        builder.append(element);
        hasChanges = true;
      }
    }
    builder.append(")");
    if (!hasChanges) return;
    
    final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(superClass.getName() + "temp", PythonFileType.INSTANCE, builder.toString());
    final PsiElement expression = file.getFirstChild().getFirstChild();
    PsiElement colon = superClass.getFirstChild();
    while (colon != null && !colon.getText().equals(":")) {
      colon = colon.getNextSibling();
    }
    LOG.assertTrue(colon != null && expression != null);
    PyPsiUtils.addBeforeInParent(colon, expression);
  }

  private static boolean alreadyHasSuperClass(PyClass superClass, String className) {
    for (PyClass aClass : superClass.getSuperClasses()) {
      if (Comparing.strEqual(aClass.getName(), className)) {
        return true;
      }
    }
    return false;
  }

  public static void moveMethods(List<PyFunction> methods, PyClass superClass) {
    if (methods.size() == 0) return;
    rememberNamedReferences(methods);
    final PyElement[] elements = methods.toArray(new PyElement[methods.size()]);
    addMethods(superClass, elements, true);
    removeMethodsWithComments(elements);
  }

  private static void removeMethodsWithComments(PyElement[] elements) {
    for (PyElement element : elements) {
      final Set<PsiElement> comments = PyUtil.getComments(element);
      if (comments.size() > 0) {
        PyPsiUtils.removeElements(comments.toArray(new PsiElement[comments.size()]));
      }
    }
    PyPsiUtils.removeElements(elements);
  }

  public static void insertPassIfNeeded(PyClass clazz) {
    final PyStatementList statements = clazz.getStatementList();
    if (statements.getStatements().length == 0) {
      statements.add(PyElementGenerator.getInstance(clazz.getProject()).createFromText(LanguageLevel.getDefault(), PyPassStatement.class, "pass"));
    }
  }

  public static void addMethods(final PyClass superClass, final PyElement[] elements, final boolean up) {
    if (elements.length == 0) return;
    final PyStatementList statements = superClass.getStatementList();
    for (PyElement newStatement : elements) {
      if (up && newStatement instanceof PyFunction) {
        final String name = newStatement.getName();
        if (name != null && superClass.findMethodByName(name, false) != null) {
          continue;
        }
      }
      if (newStatement instanceof PyExpressionStatement && newStatement.getFirstChild() instanceof PyStringLiteralExpression) continue;
      final PsiElement anchor = statements.add(newStatement);
      restoreReferences((PyElement)anchor);
      final Set<PsiElement> comments = PyUtil.getComments(newStatement);
      for (PsiElement comment : comments) {
        statements.addBefore(comment, anchor);
      }
    }
    PyPsiUtils.removeRedundantPass(statements);
  }

  private static void restoreReferences(PyElement newStatement) {
    newStatement.acceptChildren(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
        restoreReference(node);
      }
    });
  }

  private static void restoreReference(final PyReferenceExpression node) {
    PsiNamedElement target = node.getCopyableUserData(ENCODED_IMPORT);
    if (target instanceof PsiDirectory) {
      target = (PsiNamedElement)PyUtil.turnDirIntoInit(target);
    }
    if (target == null) return;
    if (PyBuiltinCache.getInstance(target).hasInBuiltins(target)) return;
    if (PsiTreeUtil.isAncestor(node.getContainingFile(), target, false)) return;
    AddImportHelper.addImport(target, node.getContainingFile(), node);
    node.putCopyableUserData(ENCODED_IMPORT, null);
  }

  public static void insertImport(PyClass target, Collection<PyClass> newClasses) {
    for (PyClass newClass : newClasses) {
      insertImport(target, newClass);
    }
  }

  private static void insertImport(PyClass target, PyClass newClass) {
    if (PyBuiltinCache.getInstance(newClass).hasInBuiltins(newClass)) return;
    final PsiFile newFile = newClass.getContainingFile();
    final VirtualFile vFile = newFile.getVirtualFile();
    assert vFile != null;
    final PsiFile file = target.getContainingFile();
    if (newFile == file) return;
    final String importableName = ResolveImportUtil.findShortestImportableName(target, vFile);
    if (!PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT || newClass instanceof PyFile) {
      if (newClass instanceof PyFile) {
        AddImportHelper.addImportStatement(file, importableName, null);
      } else {
        final String name = newClass.getName();
        AddImportHelper.addImportStatement(file, importableName + "." + name, null);
      }
    } else {
      AddImportHelper.addImportFrom(file, importableName, newClass.getName());
    }
  }

  private static void rememberNamedReferences(final List<PyFunction> methods) {
    for (PyFunction method : methods) {
      method.acceptChildren(new PyRecursiveElementVisitor() {
        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
          super.visitPyReferenceExpression(node);
          rememberReference(node);
        }
      });
    }
  }


  private static final Key<PsiNamedElement> ENCODED_IMPORT = Key.create("PyEncodedImport");
  private static void rememberReference(PyReferenceExpression node) {
    // we will remember reference in deepest node
    if (node.getQualifier() instanceof PyReferenceExpression) return;

    final PsiPolyVariantReference ref = node.getReference();
    final PsiElement target = ref.resolve();
    if (target instanceof PsiNamedElement) {
      node.putCopyableUserData(ENCODED_IMPORT, (PsiNamedElement)target);
    }
  }
}
