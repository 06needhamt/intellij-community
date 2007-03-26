package com.intellij.localvcs.integration;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.localvcs.Entry;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.*;

// todo no need to be CacheUpdater
public class Updater implements CacheUpdater {
  private ILocalVcs myVcs;
  private FileFilter myFilter;
  private VirtualFile[] myVfsRoots;
  private Set<VirtualFile> myFilesToCreate = new HashSet<VirtualFile>();
  private Set<VirtualFile> myFilesToUpdate = new HashSet<VirtualFile>();

  public Updater(ILocalVcs vcs, FileFilter filter, VirtualFile... roots) {
    myVcs = vcs;
    myFilter = filter;
    myVfsRoots = selectSortParentlessRoots(roots);
  }

  private VirtualFile[] selectSortParentlessRoots(VirtualFile... roots) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile r : roots) {
      if (parentIsNotUnderContentRoot(r)) result.add(r);
    }
    sortRoots(result);
    return result.toArray(new VirtualFile[0]);
  }

  private void sortRoots(List<VirtualFile> roots) {
    Collections.sort(roots, new Comparator<VirtualFile>() {
      public int compare(VirtualFile a, VirtualFile b) {
        boolean ancestor = VfsUtil.isAncestor(a, b, false);
        return ancestor ? -1 : 1;
      }
    });
  }

  private boolean parentIsNotUnderContentRoot(VirtualFile r) {
    VirtualFile p = r.getParent();
    return p == null || !myFilter.isUnderContentRoot(p);
  }

  public VirtualFile[] queryNeededFiles() {
    myVcs.beginChangeSet();

    deleteObsoleteRoots();
    createAndUpdateRoots();

    List<VirtualFile> result = new ArrayList<VirtualFile>(myFilesToCreate);
    result.addAll(myFilesToUpdate);
    return result.toArray(new VirtualFile[0]);
  }

  public void processFile(FileContent c) {
    // todo catch possible exceptions here.
    try {
      VirtualFile f = c.getVirtualFile();
      if (myFilesToCreate.contains(f)) {
        myVcs.createFile(f.getPath(), c.getPhysicalBytes(), f.getTimeStamp());
      }
      else {
        myVcs.changeFileContent(f.getPath(), c.getPhysicalBytes(), f.getTimeStamp());
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void updatingDone() {
    myVcs.endChangeSet(null);
  }

  public void canceled() {
    throw new RuntimeException();
  }

  private void deleteObsoleteRoots() {
    List<Entry> obsolete = new ArrayList<Entry>();
    for (Entry r : myVcs.getRoots()) {
      if (!hasVfsRoot(r)) obsolete.add(r);
    }
    for (Entry e : obsolete) myVcs.delete(e.getPath());
  }

  private boolean hasVfsRoot(Entry e) {
    for (VirtualFile f : myVfsRoots) {
      if (e.pathEquals(f.getPath())) return true;
    }
    return false;
  }

  private boolean hasVcsRoot(VirtualFile f) {
    for (Entry e : myVcs.getRoots()) {
      if (e.pathEquals(f.getPath())) return true;
    }
    return false;
  }

  private void createAndUpdateRoots() {
    for (VirtualFile r : myVfsRoots) {
      if (!hasVcsRoot(r)) {
        createRecursively(r);
      }
      else {
        updateRecursively(myVcs.getEntry(r.getPath()), r);
      }
    }
  }

  private void updateRecursively(Entry entry, VirtualFile dir) {
    for (VirtualFile f : dir.getChildren()) {
      if (notAllowed(f)) continue;

      Entry e = entry.findChild(f.getName());
      if (e == null) {
        createRecursively(f);
      }
      else if (notTheSameKind(e, f)) {
        myVcs.delete(e.getPath());
        createRecursively(f);
      }
      else {
        if (f.isDirectory()) {
          updateRecursively(e, f);
        }
        else {
          if (!e.getName().equals(f.getName())) {
            myVcs.rename(e.getPath(), f.getName());
          }
          if (e.isOutdated(f.getTimeStamp())) {
            myFilesToUpdate.add(f);
          }
        }
      }
    }
    deleteObsoleteFiles(entry, dir);
  }

  private boolean notTheSameKind(Entry e, VirtualFile f) {
    return e.isDirectory() != f.isDirectory();
  }

  private void deleteObsoleteFiles(Entry entry, VirtualFile dir) {
    List<Entry> obsolete = new ArrayList<Entry>();
    for (Entry e : entry.getChildren()) {
      VirtualFile f = dir.findChild(e.getName());
      if (f == null || notAllowed(f)) {
        obsolete.add(e);
      }
    }
    for (Entry e : obsolete) myVcs.delete(e.getPath());
  }

  private void createRecursively(VirtualFile fileOrDir) {
    if (notAllowed(fileOrDir)) return;

    if (fileOrDir.isDirectory()) {
      myVcs.createDirectory(fileOrDir.getPath());
      for (VirtualFile f : fileOrDir.getChildren()) {
        createRecursively(f);
      }
    }
    else {
      myFilesToCreate.add(fileOrDir);
    }
  }

  private boolean notAllowed(VirtualFile f) {
    return !myFilter.isAllowedAndUnderContentRoot(f);
  }
}
