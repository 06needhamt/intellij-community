// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller;
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallerStore;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.Files.exists;
import static java.nio.file.Files.isDirectory;
import static java.util.Collections.emptySet;

public class JavaHomeFinderBasic {

  private final Logger log = Logger.getInstance(getClass());
  private final List<Supplier<Set<String>>> myFinders = new ArrayList<>();

  JavaHomeFinderBasic(boolean forceEmbeddedJava, String... paths) {
    myFinders.add(this::checkDefaultLocations);
    myFinders.add(this::findInPATH);
    myFinders.add(() -> findInSpecifiedPaths(paths));
    myFinders.add(this::findJavaInstalledBySdkMan);

    if (forceEmbeddedJava || Registry.is("java.detector.include.embedded", false)) {
      myFinders.add(() -> scanAll(getJavaHome(), false));
    }
  }

  private @NotNull Set<String> findInSpecifiedPaths(String[] paths) {
    return scanAll(Stream.of(paths).map(it -> Paths.get(it)).collect(Collectors.toList()), true);
  }

  protected void registerFinder(@NotNull Supplier<Set<String>> finder) {
    myFinders.add(finder);
  }

  @NotNull
  public final Set<String> findExistingJdks() {
    Set<String> result = new TreeSet<>();

    for (Supplier<Set<String>> action : myFinders) {
      try {
        result.addAll(action.get());
      }
      catch (Exception e) {
        log.warn("Failed to find Java Home. " + e.getMessage(), e);
      }
    }

    return result;
  }

  private @NotNull Set<String> findInPATH() {
    try {
      String pathVarString = EnvironmentUtil.getValue("PATH");
      if (pathVarString == null || pathVarString.isEmpty()) {
        return emptySet();
      }

      Set<Path> dirsToCheck = new HashSet<>();
      for (String p : pathVarString.split(File.pathSeparator)) {
        Path dir = Paths.get(p);
        if (!StringUtilRt.equal(dir.getFileName().toString(), "bin", SystemInfoRt.isFileSystemCaseSensitive)) {
          continue;
        }

        Path parentFile = dir.getParent();
        if (parentFile == null) {
          continue;
        }

        dirsToCheck.addAll(listPossibleJdkInstallRootsFromHomes(parentFile));
      }

      return scanAll(dirsToCheck, false);
    }
    catch (Exception e) {
      log.warn("Failed to scan PATH for JDKs. " + e.getMessage(), e);
      return emptySet();
    }
  }

  @NotNull
  private Set<String> checkDefaultLocations() {
    if (ApplicationManager.getApplication() == null) {
      return emptySet();
    }

    Set<Path> paths = new HashSet<>();
    paths.add(JdkInstaller.getInstance().defaultInstallDir());
    paths.addAll(JdkInstallerStore.getInstance().listJdkInstallHomes());

    for (Sdk jdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (!(jdk.getSdkType() instanceof JavaSdkType) || jdk.getSdkType() instanceof DependentSdkType) {
        continue;
      }

      String homePath = jdk.getHomePath();
      if (homePath == null) {
        continue;
      }

      paths.addAll(listPossibleJdkInstallRootsFromHomes(Paths.get(homePath)));
    }

    return scanAll(paths, true);
  }

  protected @NotNull Set<String> scanAll(@Nullable Path file, boolean includeNestDirs) {
    if (file == null) {
      return emptySet();
    }
    return scanAll(Collections.singleton(file), includeNestDirs);
  }

  protected @NotNull Set<String> scanAll(@NotNull Collection<Path> files, boolean includeNestDirs) {
    Set<String> result = new HashSet<>();
    for (Path root : new HashSet<>(files)) {
      scanFolder(root.toFile(), includeNestDirs, result);
    }
    return result;
  }

  private void scanFolder(@NotNull File folder, boolean includeNestDirs, @NotNull Collection<? super String> result) {
    if (JdkUtil.checkForJdk(folder)) {
      result.add(folder.getAbsolutePath());
      return;
    }

    if (!includeNestDirs) return;
    File[] files = folder.listFiles();
    if (files == null) return;

    for (File candidate : files) {
      for (File adjusted : listPossibleJdkHomesFromInstallRoot(candidate)) {
        scanFolder(adjusted, false, result);
      }
    }
  }

  @NotNull
  protected List<File> listPossibleJdkHomesFromInstallRoot(@NotNull File file) {
    return Collections.singletonList(file);
  }

  protected @NotNull List<Path> listPossibleJdkInstallRootsFromHomes(@NotNull Path file) {
    return Collections.singletonList(file);
  }

  protected static @Nullable Path getJavaHome() {
    String property = SystemProperties.getJavaHome();
    if (property == null || property.isEmpty()) {
      return null;
    }

    // actually java.home points to to jre home
    Path javaHome = Path.of(property).getParent();
    return javaHome == null || !isDirectory(javaHome) ? null : javaHome;
  }


  /**
   * Finds Java home directories installed by SDKMAN: https://github.com/sdkman
   */
  private @NotNull Set<@NotNull String> findJavaInstalledBySdkMan() {
    Path candidatesDir = findSdkManCandidatesDir();
    if (candidatesDir == null) return emptySet();
    Path javasDir = candidatesDir.resolve("java");
    if (!isDirectory(javasDir)) return emptySet();
    //noinspection UnnecessaryLocalVariable
    var homes = listJavaHomeDirsInstalledBySdkMan(javasDir);
    return homes;
  }

  @Nullable
  private static Path findSdkManCandidatesDir() {
    // first, try the special environment variable
    String candidatesPath = EnvironmentUtil.getValue("SDKMAN_CANDIDATES_DIR");
    if (candidatesPath != null) {
      Path candidatesDir = Path.of(candidatesPath);
      if (isDirectory(candidatesDir)) return candidatesDir;
    }

    // then, try to use its 'primary' variable
    String primaryPath = EnvironmentUtil.getValue("SDKMAN_DIR");
    if (primaryPath != null) {
      Path primaryDir = Path.of(primaryPath);
      if (isDirectory(primaryDir)) {
        Path candidatesDir = primaryDir.resolve("candidates");
        if (isDirectory(candidatesDir)) return candidatesDir;
      }
    }

    // finally, try the usual location in Unix or MacOS
    if (!SystemInfo.isWindows) {
      String homePath = System.getProperty("user.home");
      if (homePath != null) {
        Path homeDir = Path.of(homePath);
        Path primaryDir = homeDir.resolve(".sdkman");
        Path candidatesDir = primaryDir.resolve("candidates");
        if (isDirectory(candidatesDir)) return candidatesDir;
      }
    }

    // no chances
    return null;
  }

  private @NotNull Set<@NotNull String> listJavaHomeDirsInstalledBySdkMan(@NotNull Path javasDir) {
    var mac = SystemInfo.isMac;
    var result = new HashSet<@NotNull String>();

    try {
      var innerDirectories = Files.list(javasDir).filter(d -> isDirectory(d)).collect(Collectors.toList());
      for (Path innerDir: innerDirectories) {
        var home = innerDir;
        var releaseFile = home.resolve("release");
        if (!exists(releaseFile)) continue;
        if (mac && Files.isSymbolicLink(releaseFile) && home.getFileName().toString().contains("zulu")) {
          try {
            var realReleaseFile = releaseFile.toRealPath();
            if (!exists(realReleaseFile)) { log.warn("Failed to resolve the target file (it doesn't exist) for: " + releaseFile.toString()); continue; }
            var realHome = realReleaseFile.getParent();
            if (realHome == null) { log.warn("Failed to resolve the target file (it has no parent dir) for: " + releaseFile.toString()); continue; }
            home = realHome;
          }
          catch (IOException ioe) {
            log.warn("Failed to resolve the target file (exception) for: " + releaseFile.toString() + ": " + ioe.getMessage());
          }
        }
        result.add(home.toString());
      }
    }
    catch (IOException ioe) {
      log.warn("Unexpected exception while listing Java home directories installed by Sdkman: "+ioe.getMessage(), ioe);
      return emptySet();
    }

    return result;
  }

}
