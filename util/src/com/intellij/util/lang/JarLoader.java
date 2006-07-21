package com.intellij.util.lang;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import sun.misc.Resource;

import java.io.*;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class JarLoader extends Loader {
  private URL myURL;
  private final boolean myCanLockJar;
  private ZipFile myZipFile;
  private Set<String> myPackages = null;
  @NonNls private static final String JAR_PROTOCOL = "jar";
  @NonNls private static final String FILE_PROTOCOL = "file";

  JarLoader(URL url, boolean canLockJar) throws IOException {
    super(new URL(JAR_PROTOCOL, "", -1, url + "!/"));
    myURL = url;
    myCanLockJar = canLockJar;
  }

  private ZipFile getZipFile() throws IOException {
    if (myZipFile != null) return myZipFile;
    if (myCanLockJar) {
      myZipFile = _getZipFile();
      return myZipFile;
    }

    return _getZipFile();
  }

  private ZipFile _getZipFile() throws IOException {
    if (FILE_PROTOCOL.equals(myURL.getProtocol())) {
      String s = FileUtil.unquote(myURL.getFile());
      if (!(new File(s)).exists()) {
        throw new FileNotFoundException(s);
      }
      else {
        return new ZipFile(s);
      }
    }

    return null;
  }

  private void initPackageCache() throws IOException {
    myPackages = new HashSet<String>();
    myPackages.add("");

    final ZipFile zipFile = getZipFile();
    final Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry zipEntry = entries.nextElement();
      final String name = zipEntry.getName();
      final int i = name.lastIndexOf("/");
      String packageName = i > 0 ? name.substring(0, i) : "";
      myPackages.add(packageName);
    }

    releaseZipFile(zipFile);
  }

  private void releaseZipFile(final ZipFile zipFile) throws IOException {
    if (!myCanLockJar) {
      zipFile.close();
    }
  }

  Resource getResource(String name, boolean flag) {
    try {
      if (myPackages == null) {
        initPackageCache();
      }

      String packageName = getPackageName(name);
      if (!myPackages.contains(packageName)) return null;

      final ZipFile file = getZipFile();
      if (file == null) return null;

      try {
        ZipEntry entry = file.getEntry(name);
        if (entry != null) return new MyResource(name, new URL(getBaseURL(), name));
      }
      finally {
        releaseZipFile(file);
      }
    }
    catch (Exception e) {
      return null;
    }

    return null;
  }

  private static String getPackageName(final String name) {
    final int i = name.lastIndexOf("/");
    if (i < 0) return "";
    return name.substring(0, i);
  }

  private class MyResource extends Resource {
    private final String myName;
    private final URL myUrl;

    public MyResource(String name, URL url) {
      myName = name;
      myUrl = url;
    }

    public String getName() {
      return myName;
    }

    public URL getURL() {
      return myUrl;
    }

    public URL getCodeSourceURL() {
      return myURL;
    }

    public InputStream getInputStream() throws IOException {
      final ZipFile file = getZipFile();
      if (file == null) return null;

      try {
        final ZipEntry entry = file.getEntry(myName);
        if (entry == null) {
          releaseZipFile(file);
          return null;
        }
        final InputStream inputStream = new BufferedInputStream(file.getInputStream(entry));
        return new FilterInputStream(inputStream) {
          public void close() throws IOException {
            super.close();
            releaseZipFile(file);
          }
        };
      }
      catch (IOException e) {
        releaseZipFile(file);
        return null;
      }
    }

    public int getContentLength() {
      try {
        final ZipFile file = getZipFile();
        if (file == null) return -1;

        try {
          final ZipEntry entry = file.getEntry(myName);
          if (entry == null) return -1;

          return (int)entry.getSize();
        }
        finally {
          releaseZipFile(file);
        }
      }
      catch (IOException e) {
        return -1;
      }
    }
  }
}
