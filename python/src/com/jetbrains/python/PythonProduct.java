package com.jetbrains.python;

import com.intellij.openapi.application.ApplicationNamesInfo;

/**
 * @author yole
 */
public class PythonProduct {
  private PythonProduct() {
  }
  public static boolean isPyCharm() {
    return ApplicationNamesInfo.getInstance().getProductName().startsWith("PyCharm");
  }
}
