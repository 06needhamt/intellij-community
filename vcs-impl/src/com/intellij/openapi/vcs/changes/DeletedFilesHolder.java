package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.FilePath;

import java.util.*;

/**
 * @author max
 */
public class DeletedFilesHolder implements FileHolder {
  private final Map<String, LocallyDeletedChange> myFiles = new HashMap<String, LocallyDeletedChange>();

  public void cleanAll() {
    myFiles.clear();
  }
  
  public void takeFrom(final DeletedFilesHolder holder) {
    myFiles.clear();
    myFiles.putAll(holder.myFiles);
  }

  public void cleanScope(final VcsDirtyScope scope) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final List<LocallyDeletedChange> currentFiles = new ArrayList<LocallyDeletedChange>(myFiles.values());
        for (LocallyDeletedChange change : currentFiles) {
          if (scope.belongsTo(change.getPath())) {
            myFiles.remove(change.getPresentableUrl());
          }
        }
      }
    });
  }

  public HolderType getType() {
    return HolderType.DELETED;
  }

  public void addFile(final LocallyDeletedChange change) {
    myFiles.put(change.getPresentableUrl(), change);
  }

  public List<LocallyDeletedChange> getFiles() {
    return new ArrayList<LocallyDeletedChange>(myFiles.values());
  }

  public boolean isContainedInLocallyDeleted(final FilePath filePath) {
    final String url = filePath.getPresentableUrl();
    return myFiles.containsKey(url);
  }

  public DeletedFilesHolder copy() {
    final DeletedFilesHolder copyHolder = new DeletedFilesHolder();
    copyHolder.myFiles.putAll(myFiles);
    return copyHolder;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DeletedFilesHolder that = (DeletedFilesHolder)o;

    if (!myFiles.equals(that.myFiles)) return false;

    return true;
  }

  public int hashCode() {
    return myFiles.hashCode();
  }
}
