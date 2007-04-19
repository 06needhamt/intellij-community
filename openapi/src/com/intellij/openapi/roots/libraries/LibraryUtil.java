/**
 * @author cdr
 */
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

public class LibraryUtil {
  private LibraryUtil() {
  }

  public static boolean isClassAvailableInLibrary(final Library library, final String fqn) {
    return isClassAvailableInLibrary(library.getFiles(OrderRootType.CLASSES), fqn);
  }

  public static boolean isClassAvailableInLibrary(VirtualFile[] files, final String fqn) {
    for (VirtualFile file : files) {
      if (findInFile(file, new StringTokenizer(fqn, "."))) return true;
    }
    return false;
  }

  @Nullable
  public static Library findLibraryByClass(final String fqn, Project project) {
    if (project != null) {
      final LibraryTable projectTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
      Library library = findInTable(projectTable, fqn);
      if (library != null) {
        return library;
      }
    }
    final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable();
    return findInTable(table, fqn);
  }


  private static boolean findInFile(VirtualFile file, final StringTokenizer tokenizer) {
    if (!tokenizer.hasMoreTokens()) return true;
    @NonNls StringBuffer name = new StringBuffer(tokenizer.nextToken());
    if (!tokenizer.hasMoreTokens()) {
      name.append(".class");
    }
    final VirtualFile child = file.findChild(name.toString());
    return child != null && findInFile(child, tokenizer);
  }

  @Nullable
  private static Library findInTable(LibraryTable table, String fqn) {
    for (Library library : table.getLibraries()) {
      if (isClassAvailableInLibrary(library, fqn)) {
        return library;
      }
    }
    return null;
  }

  public static Library createLibrary(final LibraryTable libraryTable, @NonNls final String baseName) {
    String name = baseName;
    int count = 2;
    while (libraryTable.getLibraryByName(name) != null) {
      name = baseName + " (" + count++ + ")";
    }
    return libraryTable.createLibrary(name);
  }

  public static VirtualFile[] getLibraryRoots(final Project project) {
    return getLibraryRoots(project, true, true);
  }

  public static VirtualFile[] getLibraryRoots(final Project project, final boolean includeSourceFiles, final boolean includeJdk) {
    return getLibraryRoots(ModuleManager.getInstance(project).getModules(), includeSourceFiles, includeJdk);
  }

  public static VirtualFile[] getLibraryRoots(final Module[] modules, final boolean includeSourceFiles, final boolean includeJdk) {
    Set<VirtualFile> roots = new HashSet<VirtualFile>();
    for (Module module : modules) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
      for (OrderEntry entry : orderEntries) {
        if (entry instanceof LibraryOrderEntry){
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (library != null) {
            VirtualFile[] files = includeSourceFiles ? library.getFiles(OrderRootType.SOURCES) : null;
            if (files == null || files.length == 0){
              files = library.getFiles(OrderRootType.CLASSES);
            }
            roots.addAll(Arrays.asList(files));
          }
        } else if (includeJdk && entry instanceof JdkOrderEntry){
          VirtualFile[] files = includeSourceFiles ? entry.getFiles(OrderRootType.SOURCES) : null;
          if (files == null || files.length == 0){
            files = entry.getFiles(OrderRootType.CLASSES);
          }
          roots.addAll(Arrays.asList(files));
        }
      }
    }
    return roots.toArray(new VirtualFile[roots.size()]);
  }
}