package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class ResourceBundleReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
  private String myBundleName;

  public ResourceBundleReference(final PsiElement element) {
    this(element, false);
  }

  public ResourceBundleReference(final PsiElement element, boolean soft) {
    super(element, soft);
    myBundleName = getValue();
  }

  @Nullable public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }

  @NotNull public ResolveResult[] multiResolve(final boolean incompleteCode) {
    PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(myElement.getProject());
    final Module module = ModuleUtil.findModuleForPsiElement(myElement);
    if (module == null) {
      return ResolveResult.EMPTY_ARRAY;
    }
    List<PropertiesFile> propertiesFiles = referenceManager.findPropertiesFiles(module, myBundleName);
    final ResolveResult[] result = new ResolveResult[propertiesFiles.size()];
    for(int i=0; i<propertiesFiles.size(); i++) {
      PropertiesFile file = propertiesFiles.get(i);
      result[i] = new PsiElementResolveResult(file);
    }
    return result;
  }

  public String getCanonicalText() {
    return myBundleName;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    if (newElementName.endsWith(PropertiesFileType.DOT_DEFAULT_EXTENSION)) {
      newElementName = newElementName.substring(0, newElementName.lastIndexOf(PropertiesFileType.DOT_DEFAULT_EXTENSION));
    }

    final int index = myBundleName.lastIndexOf('.');
    if (index != -1) {
      newElementName = myBundleName.substring(0, index) + "." + newElementName;
    }

    return super.handleElementRename(newElementName);
  }

  public PsiElement bindToElement(@NotNull final PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PropertiesFile)) {
      throw new IncorrectOperationException();
    }
    final String name = PropertiesUtil.getFullName((PropertiesFile)element);
    return super.handleElementRename(name);
  }


  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PropertiesFile) {
      final String name = PropertiesUtil.getFullName((PropertiesFile)element);
      if (name != null && name.equals(myBundleName)) {
        return true;
      }
    }
    return false;
  }

  public Object[] getVariants() {
    PropertiesReferenceManager referenceManager = PropertiesReferenceManager.getInstance(getElement().getProject());
    final Module module = ModuleUtil.findModuleForPsiElement(myElement);
    return referenceManager.getPropertyFileBaseNames(module);
  }
}
