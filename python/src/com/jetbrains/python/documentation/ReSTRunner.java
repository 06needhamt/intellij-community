package com.jetbrains.python.documentation;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PySdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * User : catherine
 */
public class ReSTRunner {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.documentation.ReSTRunner");

  private ReSTRunner() {
  }

  @Nullable
  public static String formatDocstring(@NotNull Sdk sdk, String text) {
    String sdkHome = sdk.getHomePath();
    final String formatter = PythonHelpersLocator.getHelperPath("rest_formatter.py");
    ProcessOutput output = PySdkUtil.getProcessOutput(new File(sdkHome).getParent(),
                                                      new String[]{
                                                        sdkHome,
                                                        formatter,
                                                        text
                                                      },
                                                      null,
                                                      5000);
    if (output.isTimeout()) {
      LOG.info("timeout when calculating docstring");
      return null;
    }
    else if (output.getExitCode() != 0) {
      final String error = "error when calculating docstring: " + output.getStderr();
      LOG.info(error);
      return null;
    }
    return output.getStdout();
  }
}
