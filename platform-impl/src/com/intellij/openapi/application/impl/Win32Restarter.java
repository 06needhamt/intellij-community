package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.ui.Messages;
import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;

import java.io.IOException;

/**
 * @author yole
 */
public class Win32Restarter {
  private Win32Restarter() {
  }

  public static void restart() {
    Kernel32 kernel32 = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
    WString cline = kernel32.GetCommandLineW();
    int pid = kernel32.GetCurrentProcessId();

    try {
      Runtime.getRuntime().exec("restarter " + Integer.toString(pid) + " " + cline);
    }
    catch (IOException ex) {
      Messages.showMessageDialog("Restart failed: " + ex.getMessage(), "Restart", Messages.getErrorIcon());
      return;
    }
    try {
      Thread.sleep(500);
    }
    catch (InterruptedException e1) {
      // ignore
    }
    ((ApplicationEx)ApplicationManager.getApplication()).exit(true);
  }

  private interface Kernel32 extends StdCallLibrary {
    WString GetCommandLineW();
    int GetCurrentProcessId();
  }
}
