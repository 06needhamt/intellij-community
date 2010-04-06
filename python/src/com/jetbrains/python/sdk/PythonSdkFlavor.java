package com.jetbrains.python.sdk;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public abstract class PythonSdkFlavor {
  public List<String> suggestHomePaths() {
    return Collections.emptyList();
  }

  /**
   * Checks if the path is the name of a Python intepreter of this flavor.
   *
   * @param path path to check.
   * @return true if paths points to a valid home.
   */
  public boolean isValidSdkHome(String path) {
    File file = new File(path);
    return file.isFile() && FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("python");
  }

  public static List<PythonSdkFlavor> getApplicableFlavors() {
    List<PythonSdkFlavor> result = new ArrayList<PythonSdkFlavor>();
    if (SystemInfo.isWindows) {
      result.add(WinPythonSdkFlavor.INSTANCE);
    }
    else if (SystemInfo.isMac) {
      result.add(MacPythonSdkFlavor.INSTANCE);
    }
    else if (SystemInfo.isUnix) {
      result.add(UnixPythonSdkFlavor.INSTANCE);
    }
    result.add(JythonSdkFlavor.INSTANCE);
    return result;
  }

  public String getVersionString(String sdkHome) {
    return getVersionFromOutput(sdkHome, "-V", "(Python \\S+).*");
  }

  protected static String getVersionFromOutput(String sdkHome, String version_opt, String version_regexp) {
    Pattern pattern = Pattern.compile(version_regexp);
    String run_dir = new File(sdkHome).getParent();
    final ProcessOutput process_output = SdkUtil.getProcessOutput(run_dir, new String[]{sdkHome, version_opt});
    if (process_output.getExitCode() != 0) {
      throw new RuntimeException(process_output.getStderr() + " (exit code " + process_output.getExitCode() + ")");
    }
    return SdkUtil.getFirstMatch(process_output.getStderrLines(), pattern);
  }

}
