package com.intellij.lang.ant;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.xml.events.XmlChange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

public class AntLanguageExtension implements LanguageExtension {

  public static Key<Boolean> ANT_FILE_SIGN = new Key<Boolean>("FORCED ANT FILE");

  public boolean isRelevantForFile(final PsiFile psi) {
    if (psi instanceof XmlFile) {
      final XmlFile xmlFile = (XmlFile)psi;
      final XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null && AntFileImpl.PROJECT_TAG.equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
          if (tag.getAttributeValue(AntFileImpl.DEFAULT_ATTR) != null) {
            return true;
          }
          VirtualFile vFile = xmlFile.getVirtualFile();
          if (vFile == null) {
            final PsiFile origFile = xmlFile.getOriginalFile();
            if (origFile != null) {
              vFile = origFile.getVirtualFile();
            }
          }
          if (vFile != null && vFile.getUserData(ANT_FILE_SIGN) != null) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public Language getLanguage() {
    return AntSupport.getLanguage();
  }

  public void updateByChange(final XmlChange xmlChange) {
    xmlChange.accept(AntSupport.getChangeVisitor());
  }
}
