package com.intellij.psi.impl.cache.impl;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public class CompositeCacheManager implements CacheManager{
  private List<CacheManager> myManagers = new ArrayList<CacheManager>();

  public void addCacheManager(CacheManager manager) {
    myManagers.add(manager);
  }

  public void initialize() {
    for (CacheManager cacheManager : myManagers) {
      cacheManager.initialize();
    }
  }

  public void dispose() {
    for (CacheManager cacheManager : myManagers) {
      cacheManager.dispose();
    }
  }

  public CacheUpdater[] getCacheUpdaters() {
    List<CacheUpdater> updaters = new ArrayList<CacheUpdater>();
    for (CacheManager cacheManager : myManagers) {
      updaters.addAll(Arrays.asList(cacheManager.getCacheUpdaters()));
    }
    return updaters.toArray(new CacheUpdater[updaters.size()]);
  }

  public PsiFile[] getFilesWithWord(String word, short occurenceMask, GlobalSearchScope scope, final boolean caseSensitively) {
    List<PsiFile> files = new ArrayList<PsiFile>();
    for (CacheManager cacheManager : myManagers) {
      files.addAll(Arrays.asList(cacheManager.getFilesWithWord(word, occurenceMask, scope, caseSensitively)));
    }
    return files.toArray(new PsiFile[files.size()]);
  }

  public PsiFile[] getFilesWithTodoItems() {
    List<PsiFile> files = new ArrayList<PsiFile>();
    for (CacheManager cacheManager : myManagers) {
      files.addAll(Arrays.asList(cacheManager.getFilesWithTodoItems()));
    }
    return files.toArray(new PsiFile[files.size()]);
  }

  public int getTodoCount(VirtualFile file, final IndexPatternProvider patternProvider) {
    int count = 0;
    for (CacheManager cacheManager : myManagers) {
      count += cacheManager.getTodoCount(file, patternProvider);
    }
    return count;
  }

  public int getTodoCount(VirtualFile file, IndexPattern pattern) {
    int count = 0;
    for (CacheManager cacheManager : myManagers) {
      count += cacheManager.getTodoCount(file, pattern);
    }
    return count;
  }

  public void addOrInvalidateFile(VirtualFile file) {
    for (CacheManager cacheManager : myManagers) {
      cacheManager.addOrInvalidateFile(file);
    }
  }
}
