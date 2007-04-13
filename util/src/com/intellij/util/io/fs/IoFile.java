package com.intellij.util.io.fs;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;

class IoFile implements IFile {
  private final File myFile;


  public IoFile(@NotNull final File file) {
    myFile = file;
  }

  public boolean exists() {
    return myFile.exists();
  }

  public byte[] loadBytes() throws IOException {
    return FileUtil.loadFileBytes(myFile);
  }

  public InputStream openStream() throws FileNotFoundException {
    return new FileInputStream(myFile);
  }

  public boolean delete() {
    return myFile.delete();
  }

  public void renameTo(final IFile newFile) throws IOException {
    FileUtil.rename(myFile, ((IoFile)newFile).myFile);
  }

  public void createParentDirs() {
    FileUtil.createParentDirs(myFile);
  }

  public IFile getParentFile() {
    return new IoFile(myFile.getParentFile());
  }

  public String getName() {
    return myFile.getName();
  }

  public String getPath() {
    return myFile.getPath();
  }

  public String getCanonicalPath() {
    if (SystemInfo.isFileSystemCaseSensitive) {
      return myFile.getAbsolutePath(); // fixes problem with symlinks under Unix (however does not under Windows!)
    }
    else {
      try {
        return myFile.getCanonicalPath();
      }
      catch (IOException e) {
        return null;
      }
    }
  }

  public long length() {
    return myFile.length();
  }

  public IFile getChild(final String childName) {
    return new IoFile(new File(myFile, childName)); 
  }

  public boolean isDirectory() {
    return myFile.isDirectory();
  }
}
