package com.jetbrains.python.psi.resolve;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.jetbrains.django.facet.DjangoFacetType;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Resolves the specified qualified name in the specified context (module, all modules or a file) to a file or directory.
 *
 * @author yole
 */
public class QualifiedNameResolverImpl implements RootVisitor, QualifiedNameResolver {
  boolean myCheckForPackage = true;
  @Nullable private Module myModule;
  private PsiFile myFootholdFile;
  private final @NotNull PyQualifiedName myQualifiedName;
  @NotNull PsiManager myPsiManager;
  final Set<PsiFileSystemItem> results = Sets.newLinkedHashSet();
  private boolean myAcceptRootAsTopLevelPackage;
  private boolean myVisitAllModules = false;
  private int myRelativeLevel = -1;
  private boolean myWithoutRoots;
  private Sdk myWithSdk;

  public QualifiedNameResolverImpl(@NotNull String qNameString) {
    myQualifiedName = PyQualifiedName.fromDottedString(qNameString);
  }

  public QualifiedNameResolverImpl(@NotNull PyQualifiedName qName) {
    myQualifiedName = qName;
  }

  @Override
  public QualifiedNameResolver fromElement(@NotNull PsiElement foothold) {
    if (foothold instanceof PsiDirectory) {
      myFootholdFile = (PsiFile)PyUtil.turnDirIntoInit(foothold);
    }
    else {
      myFootholdFile = foothold.getContainingFile().getOriginalFile();
    }
    myPsiManager = foothold.getManager();
    setModule(ModuleUtilCore.findModuleForPsiElement(foothold));
    if (PydevConsoleRunner.isInPydevConsole(foothold)) {
      withAllModules();
    }
    return this;
  }

  @Override
  public QualifiedNameResolver fromModule(@NotNull Module module) {
    setModule(module);
    myPsiManager = PsiManager.getInstance(module.getProject());
    return this;
  }

  @Override
  public QualifiedNameResolver fromSdk(@NotNull Project project, @NotNull Sdk sdk) {
    myPsiManager = PsiManager.getInstance(project);
    myWithSdk = sdk;
    return this;
  }

  private void setModule(@Nullable Module module) {
    myModule = module;
    if (module != null && FacetManager.getInstance(module).getFacetByType(DjangoFacetType.ID) != null) {
      myAcceptRootAsTopLevelPackage = true;
    }
  }

  @Override
  public QualifiedNameResolver withAllModules() {
    myVisitAllModules = true;
    return this;
  }

  /**
   * Specifies that we need to look for the name in the specified SDK (instead of the SDK assigned to the module, if any).
   *
   * @param sdk the SDK in which the name should be searched.
   * @return this
   */
  @Override
  public QualifiedNameResolver withSdk(Sdk sdk) {
    myWithSdk = sdk;
    return this;
  }

  /**
   * Specifies whether we should attempt to resolve imports relative to the current file.
   * 
   * @param relativeLevel if >= 0, we try to resolve at the specified number of levels above the current file.
   * @return this
   */
  @Override
  public QualifiedNameResolver withRelative(int relativeLevel) {
    myRelativeLevel = relativeLevel;
    return this;
  }

  /**
   * Specifies that we should only try to resolve relative to the current file, not in roots.
   *
   * @return this
   */
  @Override
  public QualifiedNameResolver withoutRoots() {
    myWithoutRoots = true;
    return this;
  }

  /**
   * Specifies that we're looking for a file in a directory hierarchy, not a module in the Python package hierarchy
   * (so we don't need to check for existence of __init__.py)
   *
   * @return
   */
  @Override
  public QualifiedNameResolver withPlainDirectories() {
    myCheckForPackage = false;
    return this;
  }
  
  public boolean visitRoot(final VirtualFile root, @Nullable Module module, @Nullable Sdk sdk) {
    if (!root.isValid()) {
      return true;
    }
    PsiFileSystemItem resolveResult = resolveInRoot(root);
    if (resolveResult != null) {
      results.add(resolveResult);
    }

    if (myAcceptRootAsTopLevelPackage && myQualifiedName.matchesPrefix(PyQualifiedName.fromDottedString(root.getName()))) {
      resolveResult = resolveInRoot(root.getParent());
      if (resolveResult != null) {
        results.add(resolveResult);
      }
    }

    return true;
  }

  @Override
  @NotNull
  public List<PsiFileSystemItem> resultsAsList() {
    if (myFootholdFile != null && !myFootholdFile.isValid()) {
      return Collections.emptyList();
    }

    if (myRelativeLevel >= 0) {
      assert myFootholdFile != null;
      PsiDirectory dir = myFootholdFile.getContainingDirectory();
      if (myRelativeLevel > 0) {
        dir = ResolveImportUtil.stepBackFrom(myFootholdFile, myRelativeLevel);
      }

      PsiFileSystemItem module = resolveModuleAt(dir);
      if (module != null) {
        results.add(module);
      }
    }

    if (!myWithoutRoots) {
      results.addAll(resolveInRoots());
    }

    return Lists.newArrayList(results);
  }

  private List<PsiFileSystemItem> resolveInRoots() {
    PythonPathCache cache = findMyCache();
    if (cache != null) {
      final List<PsiFileSystemItem> cachedResults = cache.get(myQualifiedName);
      if (cachedResults != null) {
        return cachedResults;
      }
    }

    if (myVisitAllModules) {
      for (Module mod : ModuleManager.getInstance(myPsiManager.getProject()).getModules()) {
        RootVisitorHost.visitRoots(mod, false, this);
      }
    }
    else if (myModule != null) {
      final boolean otherSdk = withOtherSdk();
      RootVisitorHost.visitRoots(myModule, otherSdk, this);
      if (otherSdk) {
        RootVisitorHost.visitSdkRoots(myWithSdk, this);
      }
    }
    else if (myFootholdFile != null) {
      RootVisitorHost.visitSdkRoots(myFootholdFile, this);
    }
    else if (myWithSdk != null) {
      RootVisitorHost.visitSdkRoots(myWithSdk, this);
    }
    else {
      throw new IllegalStateException();
    }

    final ArrayList<PsiFileSystemItem> resultList = Lists.newArrayList(results);
    if (cache != null) {
      cache.put(myQualifiedName, resultList);
    }
    return resultList;
  }

  @Override
  @Nullable
  public PsiFileSystemItem firstResult() {
    final List<PsiFileSystemItem> results = resultsAsList();
    return results.size() > 0 ? results.get(0) : null;
  }

  @Override
  @NotNull
  public <T extends PsiElement> List<T> resultsOfType(Class<T> clazz) {
    List<T> result = new ArrayList<T>();
    for (PsiElement element : resultsAsList()) {
      if (clazz.isInstance(element)) {
        //noinspection unchecked
        result.add((T) element);
      }
    }
    return result;
  } 

  @Override
  @Nullable
  public <T extends PsiElement> T firstResultOfType(Class<T> clazz) {
    final List<T> list = resultsOfType(clazz);
    return list.size() > 0 ? list.get(0) : null;
  } 

  private boolean withOtherSdk() {
    return myWithSdk != null && myWithSdk != PythonSdkType.findPythonSdk(myModule);
  }

  @Nullable
  private PythonPathCache findMyCache() {
    if (myVisitAllModules) {
      return null;
    }
    if (myModule != null) {
      return withOtherSdk() ? null : PythonModulePathCache.getInstance(myModule);
    }
    if (myFootholdFile != null) {
      final Sdk sdk = PyBuiltinCache.findSdkForNonModuleFile(myFootholdFile);
      if (sdk != null) {
        return PythonSdkPathCache.getInstance(myPsiManager.getProject(), sdk);
      }
    }
    return null;
  }

  @Nullable
  private PsiFileSystemItem resolveInRoot(VirtualFile root) {
    if (!root.isDirectory()) {
      // if we have added a file as a root, it's unlikely that we'll be able to resolve anything under it in 'files only' resolve mode
      return null;
    }
    return resolveModuleAt(myPsiManager.findDirectory(root));
  }

  /**
   * Searches for a module at given directory, unwinding qualifiers and traversing directories as needed.
   *
   * @param directory where to start from; top qualifier will be searched for here.
   */
  @Nullable
  private PsiFileSystemItem resolveModuleAt(@Nullable PsiDirectory directory) {
    // prerequisites
    if (directory == null || !directory.isValid()) return null;

    PsiFileSystemItem seeker = directory;
    for (String name : myQualifiedName.getComponents()) {
      if (name == null) {
        return null;
      }
      seeker = (PsiFileSystemItem) ResolveImportUtil.resolveChild(seeker, name, myFootholdFile, true, myCheckForPackage);
    }
    return seeker;
  }

}
