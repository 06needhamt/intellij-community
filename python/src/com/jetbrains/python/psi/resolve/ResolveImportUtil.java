package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.psi.FutureFeature.ABSOLUTE_IMPORT;

/**
 * @author dcheryasov
 */
public class ResolveImportUtil {
  private ResolveImportUtil() {
  }

  private static final ThreadLocal<Set<String>> ourBeingImported = new ThreadLocal<Set<String>>() {
    @Override
    protected Set<String> initialValue() {
      return new HashSet<String>();
    }
  };

  public static boolean isAbsoluteImportEnabledFor(PsiElement foothold) {
    if (foothold != null) {
      PsiFile file = foothold.getContainingFile();
      if (file instanceof PyFile) {
        final PyFile pyFile = (PyFile)file;
        if (pyFile.getLanguageLevel().isPy3K()) {
          return true;
        }
        return pyFile.hasImportFromFuture(ABSOLUTE_IMPORT);
      }
    }
    // if the relevant import is below the foothold, it is either legal or we've detected the offending statement already
    return false;
  }


  /**
   * Finds a directory that many levels above a given file, making sure that every level has an __init__.py.
   *
   * @param base  file that works as a reference.
   * @param depth must be positive, 1 means the dir that contains base, 2 is one dir above, etc.
   * @return found directory, or null.
   */
  @Nullable
  public static PsiDirectory stepBackFrom(PsiFile base, int depth) {
    if (depth == 0) {
      return base.getContainingDirectory();
    }
    PsiDirectory result;
    if (base != null) {
      base = base.getOriginalFile(); // just to make sure
      result = base.getContainingDirectory();
      int count = 1;
      while (result != null && result.findFile(PyNames.INIT_DOT_PY) != null) {
        if (count >= depth) return result;
        result = result.getParentDirectory();
        count += 1;
      }
    }
    return null;
  }

  @Nullable
  public static PsiElement resolveImportElement(PyImportElement import_element) {
    return resolveImportElement(import_element, import_element.getImportedQName());
  }

  @Nullable
  public static PsiElement resolveImportElement(PyImportElement importElement, final PyQualifiedName qName) {
    final List<RatedResolveResult> resultList = RatedResolveResult.sorted(multiResolveImportElement(importElement, qName));
    return resultList.size() > 0 ? resultList.get(0).getElement() : null;
  }

  @NotNull
  private static List<RatedResolveResult> multiResolveImportElement(PyImportElement importElement, final PyQualifiedName qName) {
    final PyStatement importStatement = importElement.getContainingImportStatement();
    if (importStatement instanceof PyFromImportStatement) {
      return resolveNameInFromImport(importElement, qName, (PyFromImportStatement)importStatement);
    }
    else { // "import foo"
      return resolveNameInImportStatement(importElement, qName);
    }
  }

  public static List<RatedResolveResult> resolveNameInImportStatement(PyImportElement importElement, PyQualifiedName qName) {
    if (qName == null) {
      return Collections.emptyList();
    }
    final PsiFile file = importElement.getContainingFile().getOriginalFile();
    boolean absolute_import_enabled = isAbsoluteImportEnabledFor(importElement);
    final List<PsiFileSystemItem> modules = resolveModule(qName, file, absolute_import_enabled, 0);
    if (modules.size() > 0) {
      return rateResults(modules);
    }

    // in-python resolution failed
    final PsiElement result = resolveForeignImport(importElement, qName, null);
    return ResolveResultList.to(result);
  }

  public static List<RatedResolveResult> resolveNameInFromImport(PyImportElement importElement, PyQualifiedName qName,
                                                                 PyFromImportStatement importStatement) {
    if (qName == null) {
      return Collections.emptyList();
    }
    PsiFile file = importElement.getContainingFile().getOriginalFile();
    String name = qName.getComponents().get(0);

    final List<PsiFileSystemItem> candidates = importStatement.resolveImportSourceCandidates();
    List<PsiElement> resultList = new ArrayList<PsiElement>();
    for (PsiElement candidate : candidates) {
      if (!candidate.isValid()) {
        throw new PsiInvalidElementAccessException(candidate, "Got an invalid candidate from resolveImportSourceCandidates(): " + candidate.getClass());
      }
      if (candidate instanceof PsiDirectory) {
        candidate = PyUtil.getPackageElement((PsiDirectory)candidate);
      }
      PsiElement result = resolveChild(candidate, name, file, false, true);
      if (result != null) {
        if (!result.isValid()) {
          throw new PsiInvalidElementAccessException(result, "Got an invalid candidate from resolveChild(): " + result.getClass());
        }
        resultList.add(result);
      }
    }
    if (!resultList.isEmpty()) {
      return rateResults(resultList);

    }
    final PsiElement result = resolveForeignImport(importElement, qName, importStatement.getImportSourceQName());
    return ResolveResultList.to(result);
  }

  @NotNull
  public static List<PsiElement> resolveFromOrForeignImport(PyFromImportStatement fromImportStatement, PyQualifiedName qname) {
    final List<PsiFileSystemItem> results = resolveFromImportStatementSource(fromImportStatement, qname);
    if (results.isEmpty() && qname != null && qname.getComponentCount() > 0) {
      final PyQualifiedName importedQName = PyQualifiedName.fromComponents(qname.getLastComponent());
      final PyQualifiedName containingQName = qname.removeLastComponent();
      final PsiElement result = resolveForeignImport(fromImportStatement, importedQName, containingQName);
      return result != null ? Collections.singletonList(result) : Collections.<PsiElement>emptyList();
    }
    return new ArrayList<PsiElement>(results);
  }

  @NotNull
  public static List<PsiFileSystemItem> resolveFromImportStatementSource(PyFromImportStatement from_import_statement, PyQualifiedName qName) {
    boolean absolute_import_enabled = isAbsoluteImportEnabledFor(from_import_statement);
    PsiFile file = from_import_statement.getContainingFile();
    return resolveModule(qName, file, absolute_import_enabled, from_import_statement.getRelativeLevel());
  }

  @NotNull
  public static List<PsiFileSystemItem> resolveFromOrForeignImportStatementSource(@NotNull PyFromImportStatement fromImportStatement,
                                                                                  @Nullable PyQualifiedName qName) {
    final List<PsiFileSystemItem> results = resolveFromImportStatementSource(fromImportStatement, qName);
    if (!results.isEmpty()) {
      return results;
    }
    final PsiElement result = qName != null ? resolveForeignImport(fromImportStatement, qName, null) : null;
    return result instanceof PsiFileSystemItem ? Collections.singletonList((PsiFileSystemItem)result)
                                               : Collections.<PsiFileSystemItem>emptyList();
  }

  /**
   * Resolves a module reference in a general case.
   *
   * @param qualifiedName     qualified name of the module reference to resolve
   * @param sourceFile        where that reference resides; serves as PSI foothold to determine module, project, etc.
   * @param importIsAbsolute  if false, try old python 2.x's "relative first, absolute next" approach.
   * @param relativeLevel     if > 0, step back from sourceFile and resolve from there (even if importIsAbsolute is false!).
   * @return list of possible candidates
   */
  @NotNull
  public static List<PsiFileSystemItem> resolveModule(@Nullable PyQualifiedName qualifiedName, PsiFile sourceFile,
                                                      boolean importIsAbsolute, int relativeLevel) {
    if (qualifiedName == null || sourceFile == null) {
      return Collections.emptyList();
    }
    final String marker = StringUtil.join(qualifiedName.getComponents(), ".") + "#" + Integer.toString(relativeLevel);
    final Set<String> beingImported = ourBeingImported.get();
    if (beingImported.contains(marker)) {
      return Collections.emptyList(); // break endless loop in import
    }
    try {
      beingImported.add(marker);
      final QualifiedNameResolver visitor = new QualifiedNameResolver(qualifiedName).fromElement(sourceFile);
      if (relativeLevel > 0) {
        // "from ...module import"
        visitor.withRelative(relativeLevel).withoutRoots();
      }
      else {
        // "from module import"
        if (!importIsAbsolute) {
          visitor.withRelative(0);
        }
      }
      List<PsiFileSystemItem> results = visitor.resultsAsList();
      if (results.isEmpty() && relativeLevel == 0 && !importIsAbsolute) {
        results = resolveRelativeImportAsAbsolute(sourceFile, qualifiedName);
      }
      return results;
    }
    finally {
      beingImported.remove(marker);
    }
  }

  /**
   * Try to resolve relative import as absolute in roots, not in its parent directory.
   *
   * This may be useful for resolving to child skeleton modules located in other directories.
   *
   * @param foothold        foothold file.
   * @param qualifiedName   relative import name.
   * @return                list of resolved elements.
   */
  @NotNull
  private static List<PsiFileSystemItem> resolveRelativeImportAsAbsolute(@NotNull PsiFile foothold,
                                                                         @NotNull PyQualifiedName qualifiedName) {
    final PsiDirectory containingDirectory = foothold.getContainingDirectory();
    if (containingDirectory != null) {
      final PyQualifiedName containingPath = findCanonicalImportPath(containingDirectory, null);
      if (containingPath != null && containingPath.getComponentCount() > 0) {
        final PyQualifiedName absolutePath = containingPath.append(qualifiedName.toString());
        final QualifiedNameResolver absoluteVisitor = new QualifiedNameResolver(absolutePath).fromElement(foothold);
        return absoluteVisitor.resultsAsList();
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public static PsiElement resolveModuleInRoots(@NotNull PyQualifiedName moduleQualifiedName, @Nullable PsiElement foothold) {
    if (foothold == null) return null;
    QualifiedNameResolver visitor = new QualifiedNameResolver(moduleQualifiedName).fromElement(foothold);
    return visitor.firstResult();
  }

  @Nullable
  private static PythonPathCache getPathCache(PsiElement foothold) {
    PythonPathCache cache = null;
    final Module module = ModuleUtil.findModuleForPsiElement(foothold);
    if (module != null) {
      cache = PythonModulePathCache.getInstance(module);
    }
    else {
      final Sdk sdk = PyBuiltinCache.findSdkForFile(foothold.getContainingFile());
      if (sdk != null) {
        cache = PythonSdkPathCache.getInstance(foothold.getProject(), sdk);
      }
    }
    return cache;
  }

  @Nullable
  private static PsiElement resolveForeignImport(@NotNull final PyElement importElement,
                                                 @NotNull final PyQualifiedName importText,
                                                 @Nullable final PyQualifiedName importFrom) {
    for (PyImportResolver resolver : Extensions.getExtensions(PyImportResolver.EP_NAME)) {
      PsiElement result = resolver.resolveImportReference(importElement, importText, importFrom);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /**
   * Tries to find referencedName under the parent element. Used to resolve any names that look imported.
   * Parent might happen to be a PyFile(__init__.py), then it is treated <i>both</i> as a file and as ist base dir.
   *
   * @param parent          element under which to look for referenced name; if null, null is returned.
   * @param referencedName  which name to look for.
   * @param containingFile  where we're in.
   * @param fileOnly        if true, considers only a PsiFile child as a valid result; non-file hits are ignored.
   * @param checkForPackage if true, directories are returned only if they contain __init__.py
   * @return the element the referencedName resolves to, or null.
   * @todo: Honor module's __all__ value.
   * @todo: Honor package's __path__ value (hard).
   */
  @Nullable
  public static PsiElement resolveChild(@Nullable final PsiElement parent, @NotNull final String referencedName,
                                        @Nullable final PsiFile containingFile, boolean fileOnly, boolean checkForPackage) {
    PsiDirectory dir = null;
    PsiElement ret = null;
    PsiElement possible_ret = null;
    if (parent instanceof PyFileImpl) {
      if (PyNames.INIT_DOT_PY.equals(((PyFile)parent).getName())) {
        // gobject does weird things like '_gobject = sys.modules['gobject._gobject'], so it's preferable to look at
        // files before looking at names exported from __init__.py
        dir = ((PyFile)parent).getContainingDirectory();
        possible_ret = resolveInDirectory(referencedName, containingFile, dir, fileOnly, checkForPackage);
      }

      // OTOH, quite often a module named foo exports a class or function named foo, which is used as a fallback
      // by a module one level higher (e.g. curses.set_key). Prefer it to submodule if possible.
      PsiElement elementNamed = ((PyFile)parent).getElementNamed(referencedName);
      if (!fileOnly || PyUtil.instanceOf(elementNamed, PsiFile.class, PsiDirectory.class)) {
        ret = elementNamed;
      }
      if (ret != null && !PyUtil.instanceOf(ret, PsiFile.class, PsiDirectory.class) &&
          PsiTreeUtil.getStubOrPsiParentOfType(ret, PyExceptPart.class) == null) {
        return ret;
      }

      if (possible_ret != null) return possible_ret;
    }
    else if (parent instanceof PsiDirectory) {
      dir = (PsiDirectory)parent;
    }
    else if (parent instanceof PsiDirectoryContainer) {
      final PsiDirectoryContainer container = (PsiDirectoryContainer)parent;
      for (PsiDirectory childDir : container.getDirectories()) {
        final PsiElement result = resolveInDirectory(referencedName, containingFile, childDir, fileOnly, checkForPackage);
        //if (fileOnly && ! (result instanceof PsiFile) && ! (result instanceof PsiDirectory)) return null;
        if (result != null) return result;
      }
    }
    if (dir != null) {
      final PsiElement result = resolveInDirectory(referencedName, containingFile, dir, fileOnly, checkForPackage);
      //if (fileOnly && ! (result instanceof PsiFile) && ! (result instanceof PsiDirectory)) return null;
      if (result != null) {
        return result;
      }
      if (parent instanceof PsiFile) {
        final List<PsiFileSystemItem> items = resolveRelativeImportAsAbsolute((PsiFile)parent,
                                                                              PyQualifiedName.fromComponents(referencedName));
        if (!items.isEmpty()) {
          return items.get(0);
        }
      }
    }
    return ret;
  }

  @Nullable
  private static PsiElement resolveInDirectory(final String referencedName, @Nullable final PsiFile containingFile,
                                               final PsiDirectory dir, boolean isFileOnly, boolean checkForPackage) {
    if (referencedName == null) return null;

    final PsiDirectory subdir = dir.findSubdirectory(referencedName);
    if (subdir != null && (!checkForPackage || PyUtil.isPackage(subdir))) {
      return subdir;
    }

    final PsiFile module = findPyFileInDir(dir, referencedName);
    if (module != null) return module;

    if (!isFileOnly) {
      // not a subdir, not a file; could be a name in parent/__init__.py
      final PsiFile initPy = dir.findFile(PyNames.INIT_DOT_PY);
      if (initPy == containingFile) return null; // don't dive into the file we're in
      if (initPy instanceof PyFile) {
        final PsiElement element = ((PyFile)initPy).getElementNamed(referencedName);
        if (element != null && !element.isValid()) {
          throw new PsiInvalidElementAccessException(element);
        }
        return element;
      }
    }
    return null;
  }

  @Nullable
  private static PsiFile findPyFileInDir(PsiDirectory dir, String referencedName) {
    final PsiFile file = dir.findFile(referencedName + PyNames.DOT_PY);
    // findFile() does case-insensitive search, and we need exactly matching case (see PY-381)
    if (file != null && FileUtil.getNameWithoutExtension(file.getName()).equals(referencedName)) {
      return file;
    }
    return null;
  }

  public static ResolveResultList rateResults(List<? extends PsiElement> targets) {
    ResolveResultList ret = new ResolveResultList();
    for (PsiElement target : targets) {
      if (target instanceof PsiDirectory) {
        target = PyUtil.getPackageElement((PsiDirectory)target);
      }
      if (target != null) {   // Ignore non-package dirs, worthless
        int rate = RatedResolveResult.RATE_HIGH;
        if (target instanceof PyFile) {
          VirtualFile vFile = ((PyFile)target).getVirtualFile();
          if (vFile != null && vFile.getLength() > 0) {
            rate += 100;
          }
        }
        ret.poke(target, rate);
      }
    }
    return ret;
  }

  /**
   * Tries to find roots that contain given vfile, and among them the root that contains at the smallest depth.
   */
  private static class PathChoosingVisitor implements RootVisitor {

    private final VirtualFile myVFile;
    private List<String> myResult;

    private PathChoosingVisitor(VirtualFile file) {
      if (!file.isDirectory() && file.getName().equals(PyNames.INIT_DOT_PY)) {
        myVFile = file.getParent();
      }
      else {
        myVFile = file;
      }
    }

    public boolean visitRoot(VirtualFile root, Module module, Sdk sdk) {
      final String relativePath = VfsUtilCore.getRelativePath(myVFile, root, '/');
      if (relativePath != null) {
        List<String> result = StringUtil.split(relativePath, "/");
        if (myResult == null || result.size() < myResult.size()) {
          if (result.size() > 0) {
            result.set(result.size() - 1, FileUtil.getNameWithoutExtension(result.get(result.size() - 1)));
          }
          for (String component : result) {
            if (!PyNames.isIdentifier(component)) {
              return true;
            }
          }
          myResult = result;
        }
      }
      return myResult == null || myResult.size() > 0;
    }

    @Nullable
    public PyQualifiedName getResult() {
      return myResult != null ? PyQualifiedName.fromComponents(myResult) : null;
    }
  }

  /**
   * Looks for a way to import given file.
   *
   * @param foothold an element in the file to import to (maybe the file itself); used to determine module, roots, etc.
   * @param vfile    file which importable name we want to find.
   * @return a possibly qualified name under which the file may be imported, or null. If there's more than one way (overlapping roots),
   *         the name with fewest qualifiers is selected.
   */
  @Nullable
  public static String findShortestImportableName(PsiElement foothold, @NotNull VirtualFile vfile) {
    final PyQualifiedName qName = findShortestImportableQName(foothold, vfile);
    return qName == null ? null : qName.toString();
  }

  @Nullable
  public static PyQualifiedName findShortestImportableQName(@Nullable PsiFileSystemItem fsItem) {
    VirtualFile vFile = fsItem != null ? fsItem.getVirtualFile() : null;
    return vFile != null ? findShortestImportableQName(fsItem, vFile) : null;
  }

  @Nullable
  public static PyQualifiedName findShortestImportableQName(@NotNull PsiElement foothold, @NotNull VirtualFile vfile) {
    final PythonPathCache cache = getPathCache(foothold);
    final PyQualifiedName name = cache != null ? cache.getName(vfile) : null;
    if (name != null) {
      return name;
    }
    PathChoosingVisitor visitor = new PathChoosingVisitor(vfile);
    RootVisitorHost.visitRoots(foothold, visitor);
    final PyQualifiedName result = visitor.getResult();
    if (cache != null) {
      cache.putName(vfile, result);
    }
    return result;
  }

  @Nullable
  public static String findShortestImportableName(Module module, @NotNull VirtualFile vfile) {
    final PythonPathCache cache = PythonModulePathCache.getInstance(module);
    final PyQualifiedName name = cache.getName(vfile);
    if (name != null) {
      return name.toString();
    }
    PathChoosingVisitor visitor = new PathChoosingVisitor(vfile);
    RootVisitorHost.visitRoots(module, false, visitor);
    final PyQualifiedName result = visitor.getResult();
    cache.putName(vfile, result);
    return result == null ? null : result.toString();
  }

  /**
   * Returns the name through which the specified symbol should be imported. This can be different from the qualified name of the
   * symbol (the place where a symbol is defined). For example, Python 2.7 unittest defines TestCase in unittest.case module
   * but it should be imported directly from unittest.
   *
   * @param symbol   the symbol to be imported
   * @param foothold the location where the import statement would be added
   * @return the qualified name, or null if it wasn't possible to calculate one
   */
  @Nullable
  public static PyQualifiedName findCanonicalImportPath(@NotNull PsiElement symbol, @Nullable PsiElement foothold) {
    PsiFileSystemItem srcfile = symbol instanceof PsiFileSystemItem ? (PsiFileSystemItem)symbol : symbol.getContainingFile();
    if (srcfile == null) {
      return null;
    }
    VirtualFile virtualFile = srcfile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    if (srcfile instanceof PsiFile && symbol instanceof PsiNamedElement && !(symbol instanceof PsiFileSystemItem)) {
      PsiElement toplevel = symbol;
      if (symbol instanceof PyFunction) {
        final PyClass containingClass = ((PyFunction)symbol).getContainingClass();
        if (containingClass != null) {
          toplevel = containingClass;
        }
      }
      PsiDirectory dir = ((PsiFile)srcfile).getContainingDirectory();
      while (dir != null) {
        PsiFile initPy = dir.findFile(PyNames.INIT_DOT_PY);
        if (initPy == null) {
          break;
        }
        if (initPy instanceof PyFile && toplevel.equals(((PyFile)initPy).getElementNamed(((PsiNamedElement)toplevel).getName()))) {
          virtualFile = dir.getVirtualFile();
        }
        dir = dir.getParentDirectory();
      }
    }
    final PyQualifiedName qname = findShortestImportableQName(foothold != null ? foothold : symbol, virtualFile);
    if (qname != null) {
      final PyQualifiedName restored = restoreStdlibCanonicalPath(qname);
      if (restored != null) {
        return restored;
      }
    }
    return qname;
  }

  @Nullable
  public static PyQualifiedName restoreStdlibCanonicalPath(PyQualifiedName qname) {
    if (qname.getComponentCount() > 0) {
      final List<String> components = qname.getComponents();
      final String head = components.get(0);
      if (head.equals("_abcoll") || head.equals("_collections")) {
        components.set(0, "collections");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("posix") || head.equals("nt")) {
        components.set(0, "os");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("_functools")) {
        components.set(0, "functools");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("_struct")) {
        components.set(0, "struct");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("_io") || head.equals("_pyio") || head.equals("_fileio")) {
        components.set(0, "io");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("_datetime")) {
        components.set(0, "datetime");
        return PyQualifiedName.fromComponents(components);
      }
      else if (head.equals("ntpath") || head.equals("posixpath") || head.equals("path")) {
        final List<String> result = new ArrayList<String>();
        result.add("os");
        components.set(0, "path");
        result.addAll(components);
        return PyQualifiedName.fromComponents(result);
      }
    }
    return null;
  }

  public static enum PointInImport {
    /**
     * The reference is not inside an import statement.
     */
    NONE,

    /**
     * The reference is inside import and refers to a module
     */
    AS_MODULE,

    /**
     * The reference is inside import and refers to a name imported from a module
     */
    AS_NAME
  }

  /**
   * @param element what we test (identifier, reference, import element, etc)
   * @return the how the element relates to an enclosing import statement, if any
   */
  public static PointInImport getPointInImport(@NotNull PsiElement element) {
    PsiElement parent = PsiTreeUtil.getNonStrictParentOfType(
      element,
      PyImportElement.class, PyFromImportStatement.class
    );
    if (parent instanceof PyFromImportStatement) {
      return PointInImport.AS_MODULE; // from foo ...
    }
    if (parent instanceof PyImportElement) {
      PsiElement statement = parent.getParent();
      if (statement instanceof PyImportStatement) {
        return PointInImport.AS_MODULE; // import foo,...
      }
      else if (statement instanceof PyFromImportStatement) {
        return PointInImport.AS_NAME;
      }
    }
    return PointInImport.NONE;
  }
}
