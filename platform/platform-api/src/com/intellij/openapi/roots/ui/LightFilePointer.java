package com.intellij.openapi.roots.ui;

import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class LightFilePointer  {
  private final String myUrl;
  private VirtualFile myFile;

  public LightFilePointer(String url) {
    myUrl = url;
  }

  public VirtualFile getFile() {
    refreshFile();
    return myFile;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getFileName() {
    if (myFile != null) {
      return myFile.getName();
    }
    else {
      int index = myUrl.lastIndexOf('/');
      return index >= 0 ? myUrl.substring(index + 1) : myUrl;
    }
  }

  @NotNull
  public String getPresentableUrl() {
    VirtualFile file = getFile();
    if (file != null) return file.getPresentableUrl();
    return toPresentableUrl(myUrl);
  }

  public static String toPresentableUrl(String url) {
    String path = VirtualFileManager.extractPath(url);
    if (path.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return path.replace('/', File.separatorChar);
  }

  public boolean isValid() {
    return getFile() != null;
  }

  private void refreshFile() {
    if (myFile != null && myFile.isValid()) return;
    VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(myUrl);
    myFile = virtualFile != null && virtualFile.isValid() ? virtualFile : null;
  }
}
