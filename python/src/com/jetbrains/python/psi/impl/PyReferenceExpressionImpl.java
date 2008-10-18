/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 10:19:01
 * To change this template use File | Settings | File Templates.
 */
public class PyReferenceExpressionImpl extends PyElementImpl implements PyReferenceExpression {
  public PyReferenceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PsiElement getElement() {
    return this;
  }

  @NotNull
  public PsiReference[] getReferences() {
    List<PsiReference> refs = new ArrayList<PsiReference>(Arrays.asList(super.getReferences()));
    refs.add(this);
    return refs.toArray(new PsiReference[refs.size()]);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyReferenceExpression(this);
  }

  @PsiCached
  public
  @Nullable
  PyExpression getQualifier() {
    final ASTNode[] nodes = getNode().getChildren(PyElementTypes.EXPRESSIONS);
    return (PyExpression)(nodes.length == 1 ? nodes[0].getPsi() : null);
  }

  public TextRange getRangeInElement() {
    final ASTNode nameElement = getNameElement();
    final int startOffset = nameElement != null ? nameElement.getStartOffset() : getNode().getTextRange().getEndOffset();
    return new TextRange(startOffset - getNode().getStartOffset(), getTextLength());
  }

  @PsiCached
  public
  @Nullable
  String getReferencedName() {
    final ASTNode nameElement = getNameElement();
    return nameElement != null ? nameElement.getText() : null;
  }

  @PsiCached
  private
  @Nullable
  ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
  }

  @Nullable
  @Override
  public String getName() {
    return getReferencedName();
  }


  /**
   * Resolves reference to the most obvious point.
   * Imported module names: to module file (or directory for a qualifier). 
   * Other identifiers: to most recent definition before this reference.
  **/
  public
  @Nullable
  PsiElement resolve() {
    final String referencedName = getReferencedName();
    if (referencedName == null) return null;

    if (PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class) != null) {
      PsiElement target = ResolveImportUtil.resolveImportReference(this);
      if (target instanceof PsiDirectory) {
        final PsiDirectory dir = (PsiDirectory)target;
        final PsiFile file = dir.findFile(ResolveImportUtil.INIT_PY);
        if (file != null) {
          target = file; // ResolveImportUtil will extract directory part as needed.
          file.putCopyableUserData(PyFile.KEY_IS_DIRECTORY, Boolean.TRUE);
          /* NOTE: can't return anything but a PyFile or PsiFileImpl.isPsiUpToDate() would fail.
          This is because isPsiUpToDate() relies on identity of objects returned by FileViewProvider.getPsi().
          If we ever need to exactly tell a dir from __init__.py, that logic has to change. 
          */ 
        }
        else return null; // dir without __init__.py does not resolve
      }
      return target;
    }

    final PyExpression qualifier = getQualifier();
    if (qualifier != null) {
      PyType qualifierType = qualifier.getType();
      if (qualifierType != null) {
        if (qualifier instanceof PyQualifiedExpression) {
          // enrich the type info with any fields assigned nearby
          List<PyQualifiedExpression> qualifier_path = PyResolveUtil.unwindQualifiers((PyQualifiedExpression)qualifier);
          if (qualifier_path != null) {
            PyResolveUtil.AssignmentCollectProcessor<PyQualifiedExpression> proc =
              new PyResolveUtil.AssignmentCollectProcessor<PyQualifiedExpression>(qualifier_path)
            ;
            PyResolveUtil.treeCrawlUp(proc, qualifier);
            for (PyExpression ex : proc.getResult()) {
              if (referencedName.equals(ex.getName())) return ex;
            }
          }
        }
        // resolve within the type proper
        return qualifierType.resolveMember(referencedName);
      }
      return null;
    }

    // here we have an unqualified expr. it may be defined:
    // ...in current file
    PsiElement ret = PyResolveUtil.treeCrawlUp(new PyResolveUtil.ResolveProcessor(referencedName), this);
    if ((ret != null) && (ret instanceof PyClass)) {
      // is it a case of the bizarre "class Foo(Foo)" construct?
      PyClass cls = (PyClass)ret;
      for (PyExpression base_expr : cls.getSuperClassExpressions()){
        if (base_expr == this) return null; // cannot resolve us, the base class ref, to the class being defined
      }
    }
    if (ret == null) {
      // ...as a part of current module
      PyType otype = PyBuiltinCache.getInstance(this.getProject()).getObjectType(); // "object" as a closest kin to "module"
      if (otype != null) ret = otype.resolveMember(getName());
    }
    if (ret == null) {
      // ...as a builtin symbol
      PyFile bfile = PyBuiltinCache.getInstance(this.getProject()).getBuiltinsFile();
      ret = PyResolveUtil.treeCrawlUp(new PyResolveUtil.ResolveProcessor(referencedName), true, bfile);
    }
    if (ret == null) {
      ret = PyResolveUtil.resolveOffContext(this);
    }
    return ret;
  }

  /**
   * Resolves reference to possible referred elements.
   * First element is always what resolve() would return.
   * Imported module names: to module file, or {directory, '___init__.py}' for a qualifier.
   * @todo Local identifiers: a list of definitions in the most recent compound statement 
   * (e.g. <code>if X: a = 1; else: a = 2</code> has two definitions of <code>a</code>.).
   * @todo Identifiers not found locally: similar definitions in imported files and builtins.
   * @see com.intellij.psi.PsiPolyVariantReference#multiResolve(boolean)
  **/
  @NotNull
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final String referencedName = getReferencedName();
    if (referencedName == null) return ResolveResult.EMPTY_ARRAY;
    
    // crude logic right here to see it work
    
    PsiElement target = resolve();
    if (target == null) return ResolveResult.EMPTY_ARRAY;
    
    List<ResolveResult> ret = new ArrayList<ResolveResult>();
    ret.add(new PsiElementResolveResult(target));
    
    if (target instanceof PsiDirectory) {
      final PsiDirectory dir = (PsiDirectory)target;
      final PsiFile file = dir.findFile(ResolveImportUtil.INIT_PY);
      if (file != null) {
        ret.add(0, new PsiElementResolveResult(file));
      }
    }
    
    return ret.toArray(new ResolveResult[ret.size()]);
    /*
    if (getQualifier() != null) {
      return ResolveResult.EMPTY_ARRAY; // TODO?
    }

    PyResolveUtil.MultiResolveProcessor processor = new PyResolveUtil.MultiResolveProcessor(referencedName);
    PyResolveUtil.treeWalkUp(processor, this, this, this);
    return processor.getResults();
    */
  }

  public String getCanonicalText() {
    return null;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    ASTNode nameElement = getNameElement();
    if (nameElement != null) {
      final ASTNode newNameElement = getLanguage().getElementGenerator().createNameIdentifier(getProject(), newElementName);
      getNode().replaceChild(nameElement, newNameElement);
    }
    return this;
  }

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiNamedElement) {
      if (Comparing.equal(getReferencedName(), ((PsiNamedElement)element).getName())) {
        return resolve() == element;
      }
    }
    return false;
  }

  public Object[] getVariants() {
    final PyExpression qualifier = getQualifier();
    if (qualifier != null) {
      PyType qualifierType = qualifier.getType();
      if (qualifierType != null) {
        return qualifierType.getCompletionVariants(this);
      }
      return new Object[0];
    }

    if (PsiTreeUtil.getParentOfType(this, PyImportElement.class, PyFromImportStatement.class) != null) {
      // complete to possible modules
      return ResolveImportUtil.suggestImportVariants(this);
    }

    // include our own names
    final PyResolveUtil.VariantsProcessor processor = new PyResolveUtil.VariantsProcessor();
    PyResolveUtil.treeCrawlUp(processor, this); // names from here
    // scan all "import *" and include names provided by them
    PyResolveUtil.CollectProcessor<PyStarImportElement> collect_proc;
    collect_proc = new PyResolveUtil.CollectProcessor<PyStarImportElement>(PyStarImportElement.class);
    PyResolveUtil.treeCrawlUp(collect_proc, this);
    List<PyStarImportElement> stars = collect_proc.getResult();
    for (PyStarImportElement star_elt : stars) {
      final PyFromImportStatement from_import_stmt = (PyFromImportStatement)star_elt.getParent();
      if (from_import_stmt != null) {
        final PyReferenceExpression import_src = from_import_stmt.getImportSource();
        if (import_src != null) {
          processor.setNotice(" | " + import_src.getName());
          PyResolveUtil.treeCrawlUp(processor, true, import_src.resolve()); // names from that module
        }
      }
    }
    // include builtin names
    processor.setNotice(" | __builtin__");
    PyResolveUtil.treeCrawlUp(processor, true, PyBuiltinCache.getInstance(getProject()).getBuiltinsFile()); // names from __builtin__
    return processor.getResult();
  }

  public boolean isSoft() {
    return false;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    // in statements, process only the section in which the original expression was located
    PsiElement parent = getParent();
    if (parent instanceof PyStatement && lastParent == null && PsiTreeUtil.isAncestor(parent, place, true)) {
      return true;
    }

    // never resolve to references within the same assignment statement
    if (getParent() instanceof PyAssignmentStatement) {
      PsiElement placeParent = place.getParent();
      while (placeParent != null && placeParent instanceof PyExpression) {
        placeParent = placeParent.getParent();
      }
      if (placeParent == getParent()) {
        return true;
      }
    }

    if (this == place) {
      return true;
    }
    return processor.execute(this, substitutor);
  }

  public HighlightSeverity getUnresolvedHighlightSeverity() {
    if (isBuiltInConstant()) return null;
    final PyExpression qualifier = getQualifier();
    if (qualifier == null) {
      return HighlightSeverity.ERROR;
    }
    if (qualifier.getType() != null) {
      return HighlightSeverity.WARNING;
    }
    return null;
  }

  private boolean isBuiltInConstant() {
    // TODO: generalize
    String name = getReferencedName();
    return PyNames.NONE.equals(name) || "True".equals(name) || "False".equals(name);
  }

  @Nullable
  public String getUnresolvedDescription() {
    return null;
  }

  @Override
  public String toString() {
    return "PyReferenceExpression: " + getReferencedName();
  }

  public PyType getType() {
    if (getQualifier() == null) {
      String name = getReferencedName();
      if (PyNames.NONE.equals(name)) {
        return PyNoneType.INSTANCE;
      }
    }
    PsiElement target = resolve();
    if (target == this) {
      return null;
    }
    return getTypeFromTarget(target);
  }

  @Nullable
  public static PyType getTypeFromTarget(final PsiElement target) {
    if (target instanceof PyFile) {
      return new PyModuleType((PyFile) target);
    }
    if (target instanceof PyExpression) {
      return ((PyExpression) target).getType();
    }
    if (target instanceof PyClass) {
      return new PyClassType((PyClass) target, true);
    }
    if (target instanceof PsiDirectory) {
      PsiFile file = ((PsiDirectory)target).findFile(ResolveImportUtil.INIT_PY);
      if (file != null) return getTypeFromTarget(file);
    }
    return getReferenceTypeFromProviders(target);
  }

  @Nullable
  public static PyType getReferenceTypeFromProviders(final PsiElement target) {
    for(PyTypeProvider provider: Extensions.getExtensions(PyTypeProvider.EP_NAME)) {
      final PyType result = provider.getReferenceType(target);
      if (result != null) return result;
    }

    return null;
  }

}
