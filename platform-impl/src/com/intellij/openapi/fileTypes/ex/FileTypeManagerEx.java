package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.options.SchemesManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public abstract class FileTypeManagerEx extends FileTypeManager{
  public static FileTypeManagerEx getInstanceEx(){
    return (FileTypeManagerEx) getInstance();
  }

  public abstract void registerFileType(FileType fileType);
  public abstract void unregisterFileType(FileType fileType);

//  public abstract String getIgnoredFilesList();
//  public abstract void setIgnoredFilesList(String list);
  public abstract boolean isIgnoredFilesListEqualToCurrent(String list);

  @NotNull public abstract String getExtension(String fileName);

  public abstract void fireFileTypesChanged();

  public abstract void fireBeforeFileTypesChanged();

  public abstract SchemesManager<FileType, AbstractFileType> getSchemesManager();
}
