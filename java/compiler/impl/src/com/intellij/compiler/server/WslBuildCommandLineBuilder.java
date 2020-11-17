// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.server;

import com.intellij.compiler.YourKitProfilerService;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.wsl.WSLCommandLineOptions;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

class WslBuildCommandLineBuilder implements BuildCommandLineBuilder {
  private final Project myProject;
  private final @NotNull WSLDistribution myDistribution;
  private final GeneralCommandLine myCommandLine = new GeneralCommandLine();
  private final @NotNull String myWorkingDirectory;
  private final @NotNull String myHostWorkingDirectory;
  private final @Nullable String myClasspathDirectory;
  private final @Nullable Path myHostClasspathDirectory;

  private static boolean CURRENT_SNAPSHOT_COPIED = false;

  WslBuildCommandLineBuilder(@NotNull Project project, @NotNull WSLDistribution distribution, @NotNull String sdkPath) {
    myProject = project;
    myDistribution = distribution;
    myCommandLine.setExePath(sdkPath);

    String home = distribution.getUserHome();
    if (home != null) {
      String pathsSelector = PathManager.getPathsSelector();
      if (pathsSelector == null) pathsSelector = "." + ApplicationNamesInfo.getInstance().getScriptName();
      String workingDirectory = PathManager.getDefaultUnixSystemPath(home, pathsSelector) + "/" + BuildManager.SYSTEM_ROOT;
      myHostWorkingDirectory = myDistribution.getWindowsPath(workingDirectory);
      myWorkingDirectory = myHostWorkingDirectory != null ? workingDirectory : null;

      myClasspathDirectory = myWorkingDirectory + "/jps-" + ApplicationInfo.getInstance().getBuild().asString();
      myHostClasspathDirectory = Paths.get(myDistribution.getWindowsPath(myClasspathDirectory));
      if (ApplicationInfo.getInstance().getBuild().isSnapshot() && !CURRENT_SNAPSHOT_COPIED) {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        CURRENT_SNAPSHOT_COPIED = true;
        try {
          FileUtil.delete(myHostClasspathDirectory);
        }
        catch (IOException e) {
          // ignore
        }
      }
    }
    else {
      myHostWorkingDirectory = BuildManager.getInstance().getBuildSystemDirectory().toString();
      myWorkingDirectory = myDistribution.getWslPath(myHostWorkingDirectory);
      myClasspathDirectory = null;
      myHostClasspathDirectory = null;
    }
  }

  @Override
  public void addParameter(@NotNull String parameter) {
    myCommandLine.addParameter(parameter);
  }

  @Override
  public void addPathParameter(@NotNull String prefix, @NotNull String path) {
    myCommandLine.addParameter(prefix + myDistribution.getWslPath(path));
  }

  @Override
  public void addClasspathParameter(List<String> classpathInHost, List<String> classpathInTarget) {
    StringBuilder builder = new StringBuilder();
    long startTime = System.currentTimeMillis();
    for (String pathName : classpathInHost) {
      if (builder.length() > 0) {
        builder.append(":");
      }
      Path path = Paths.get(pathName);
      if (myClasspathDirectory != null && myHostClasspathDirectory != null) {
        Path targetPath = myHostClasspathDirectory.resolve(path.getFileName());
        try {
          if (!targetPath.toFile().exists()) {
            FileUtil.copyFileOrDir(path.toFile(), targetPath.toFile());
          }
          builder.append(myDistribution.getWslPath(targetPath.toString()));
          continue;
        }
        catch (IOException e) {
          // fallback to default case
        }
      }

      builder.append(myDistribution.getWslPath(pathName));
    }
    long endTime = System.currentTimeMillis();
    for (String s : classpathInTarget) {
      if (builder.length() > 0) {
        builder.append(":");
      }
      builder.append(myWorkingDirectory).append("/").append(s);
    }
    myCommandLine.addParameter(builder.toString());
  }

  @Override
  public @NotNull Path getHostWorkingDirectory() {
    return Paths.get(myHostWorkingDirectory);
  }

  @Override
  public @NotNull String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  @Override
  public InetAddress getListenAddress() {
    return myDistribution.getHostIpAddress();
  }

  @Override
  public @NotNull String getHostIp() throws ExecutionException {
    String hostIp = myDistribution.getHostIp();
    if (hostIp == null) {
      throw new ExecutionException("Failed to determine host IP for WSL JDK");
    }
    return hostIp;
  }

  @Override
  public String getYjpAgentPath(YourKitProfilerService yourKitProfilerService) {
    return myWorkingDirectory + "/" + yourKitProfilerService.getYKAgentFullNameForWSL();
  }

  @Override
  public void setCharset(Charset charset) {
    myCommandLine.setCharset(charset);
  }

  @Override
  public GeneralCommandLine buildCommandLine() throws ExecutionException {
    WSLCommandLineOptions options = new WSLCommandLineOptions();
    options.setRemoteWorkingDirectory(myWorkingDirectory);
    myDistribution.patchCommandLine(myCommandLine, myProject, options);

    return myCommandLine;
  }
}
