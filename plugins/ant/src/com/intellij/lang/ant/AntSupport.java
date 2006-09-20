package com.intellij.lang.ant;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.lang.CompositeLanguage;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ant.psi.changes.AntChangeVisitor;
import com.intellij.lang.ant.validation.AntDuplicateTargetsInspection;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AntSupport implements ApplicationComponent, InspectionToolProvider {

  private static LanguageFileType ourFileType = null;
  private static AntLanguage ourLanguage = null;
  private static AntChangeVisitor ourChangeVisitor = null;

  public AntSupport(FileTypeManager fileTypeManager) {
    fileTypeManager.getRegisteredFileTypes();
    ((CompositeLanguage)StdLanguages.XML).registerLanguageExtension(new AntLanguageExtension());
  }

  public static AntLanguage getLanguage() {
    if (ourLanguage == null) {
      if (ourFileType == null) {
        ourFileType = new AntFileType();
      }
      ourLanguage = (AntLanguage)ourFileType.getLanguage();
    }
    return ourLanguage;
  }

  public static AntChangeVisitor getChangeVisitor() {
    if (ourChangeVisitor == null) {
      ourChangeVisitor = new AntChangeVisitor();
    }
    return ourChangeVisitor;
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "AntSupport";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static void markFileAsAntFile(final VirtualFile file, final FileViewProvider viewProvider, final boolean isAntFile) {
    Boolean oldValue = file.getUserData(AntLanguageExtension.ANT_FILE_SIGN);
    if (oldValue == null) {
      oldValue = false;
    }
    if (oldValue.booleanValue() != isAntFile) {
      file.putUserData(AntLanguageExtension.ANT_FILE_SIGN, (isAntFile) ? true : null);
      viewProvider.contentsSynchronized();
    }
  }

  public Class[] getInspectionClasses() {
    return new Class[]{AntDuplicateTargetsInspection.class};
  }
}
