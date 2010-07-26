package com.jetbrains.python.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public enum LanguageLevel {
  PYTHON24(24, false, true, false, false),
  PYTHON25(25, false, true, false, false),
  PYTHON26(26, true, true, false, false),
  PYTHON27(27, true, true, true, false),
  PYTHON30(30, true, false, false, true),
  PYTHON31(31, true, false, true, true);

  public static LanguageLevel getDefault() {
    return PYTHON26;
  }

  private final int myVersion;
  private final boolean myHasWithStatement;
  private final boolean myHasPrintStatement;
  private final boolean mySupportsSetLiterals;
  private final boolean myIsPy3K;

  LanguageLevel(int version, boolean hasWithStatement, boolean hasPrintStatement, boolean supportsSetLiterals, boolean isPy3K) {
    myVersion = version;
    myHasWithStatement = hasWithStatement;
    myHasPrintStatement = hasPrintStatement;
    mySupportsSetLiterals = supportsSetLiterals;
    myIsPy3K = isPy3K;
  }

  public boolean hasWithStatement() {
    return myHasWithStatement;
  }

  public boolean hasPrintStatement() {
    return myHasPrintStatement;
  }

  public boolean supportsSetLiterals() {
    return mySupportsSetLiterals;
  }

  public boolean isPy3K() {
    return myIsPy3K;
  }

  public boolean isOlderThan(LanguageLevel other) {
    return myVersion < other.myVersion;
  }

  public boolean isAtLeast(LanguageLevel other) {
    return myVersion >= other.myVersion;
  }

  public static LanguageLevel fromPythonVersion(String pythonVersion) {
    if (pythonVersion.startsWith("2.7")) {
      return PYTHON27;
    }
    if (pythonVersion.startsWith("2.6")) {
      return PYTHON26;
    }
    if (pythonVersion.startsWith("2.5")) {
      return PYTHON25;
    }
    if (pythonVersion.startsWith("3.0")) {
      return PYTHON30;
    }
    if (pythonVersion.startsWith("3.1") || pythonVersion.startsWith("3.2")) {
      return PYTHON31;
    }
    return PYTHON24;
  }

  public static final Key<LanguageLevel> KEY = new Key<LanguageLevel>("python.language.level");

  @NotNull
  public static LanguageLevel forFile(@NotNull VirtualFile virtualFile) {
    // Most of the cases should be handled by this one, PyLanguageLevelPusher pushes folders only
    final VirtualFile folder = virtualFile.getParent();
    if (folder != null) {
      final LanguageLevel level = folder.getUserData(KEY);
      if (level != null) return level;
    }
    else {
      // However this allows us to setup language level per file manually
      // in case when it is LightVirtualFile
      final LanguageLevel level = virtualFile.getUserData(KEY);
      if (level != null) return level;      

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final LanguageLevel languageLevel = PythonLanguageLevelPusher.FORCE_LANGUAGE_LEVEL;
        if (languageLevel != null) {
          return languageLevel;
        }
      }
    }

    return getDefault();
  }

  @Override
  public String toString() {
    return myVersion / 10 + "." + myVersion % 10;
  }
}
