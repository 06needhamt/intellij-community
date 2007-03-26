package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AntBuildFileClassLoaderHolder extends ClassLoaderHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntBuildFileClassLoaderHolder");
  @NonNls private static final String USER_HOME = "user.home";
  @NonNls private static final String ANT_LIB = "/.ant/lib";

  public AntBuildFileClassLoaderHolder(AbstractProperty.AbstractPropertyContainer options) {
    super(options);
  }

  protected ClassLoader buildClasspath() {
    final ArrayList<File> files = new ArrayList<File>();
    for (final AntClasspathEntry entry : AntBuildFileImpl.ADDITIONAL_CLASSPATH.get(myOptions)) {
      entry.addFilesTo(files);
    }
    final String homeDir = System.getProperty(USER_HOME);
    if (homeDir != null && homeDir.length() > 0) {
      new AllJarsUnderDirEntry(new File(homeDir, ANT_LIB)).addFilesTo(files);
    }
    
    final AntInstallation antInstallation = AntBuildFileImpl.RUN_WITH_ANT.get(myOptions);
    final ClassLoader parentLoader = (antInstallation != null) ? antInstallation.getClassLoader() : null;
    if (parentLoader != null && files.size() == 0) {
      // no additional classpath, so it's ok to use ant installation's loader
      return parentLoader;
    }

    final List<URL> urls = new ArrayList<URL>(files.size());
    for (File file : files) {
      try {
        urls.add(file.toURI().toURL());
      }
      catch (MalformedURLException e) {
        LOG.debug(e);
      }
    }
    return new UrlClassLoader(urls, parentLoader);
  }
}