
package com.intellij.psi.impl;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class PsiElementBase extends ElementBase implements PsiElement,NavigationItem {
  public PsiElement getFirstChild() {
    PsiElement[] children = getChildren();
    if (children.length == 0) return null;
    return children[0];
  }

  public PsiElement getLastChild() {
    PsiElement[] children = getChildren();
    if (children.length == 0) return null;
    return children[children.length - 1];
  }

  public PsiElement getNextSibling() {
    return SharedPsiElementImplUtil.getNextSibling(this);
  }

  public PsiElement getPrevSibling() {
    return SharedPsiElementImplUtil.getPrevSibling(this);
  }

  public void acceptChildren(PsiElementVisitor visitor) {
    PsiElement[] children = getChildren();
    for (PsiElement child : children) {
      child.accept(visitor);
    }
  }

  public PsiReference getReference() {
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRangeBefore(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public boolean textContains(char c) {
    return getText().indexOf(c) >= 0;
  }

  public boolean processDeclarations(PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastParent,
                                     PsiElement place) {
    return true;
  }

  public PsiElement getContext() {
    return ResolveUtil.getContext(this);
  }

  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  public final GlobalSearchScope getResolveScope() {
    return ((PsiManagerImpl)getManager()).getFileManager().getResolveScope(this);
  }

  @NotNull
  public SearchScope getUseScope() {
    return getManager().getSearchHelper().getUseScope(this);
  }

  public ItemPresentation getPresentation() {
    return null;
  }

  public void navigate(boolean requestFocus) {
    EditSourceUtil.getDescriptor(this).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(this);
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  public FileStatus getFileStatus() {
    return FileStatus.NOT_CHANGED;
  }

  public String getName() {
    return this instanceof PsiNamedElement ? ((PsiNamedElement)this).getName() : null;
  }

  public Project getProject() {
    final PsiManager manager = getManager();
    return manager != null ? manager.getProject() : null;
  }
}
