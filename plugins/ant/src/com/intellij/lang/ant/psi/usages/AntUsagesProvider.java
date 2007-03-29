package com.intellij.lang.ant.psi.usages;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.HelpID;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AntUsagesProvider implements FindUsagesProvider {

  @Nullable
  public WordsScanner getWordsScanner() {
    return null;
  }

  public boolean canFindUsagesFor(@NotNull PsiElement element) {
    if (!(element instanceof AntStructuredElement)) return false;
    AntStructuredElement se = (AntStructuredElement)element;
    return se.hasNameElement() || se.hasIdElement();
  }

  @Nullable
  public String getHelpId(@NotNull PsiElement element) {
    return HelpID.FIND_OTHER_USAGES;
  }

  @NotNull
  public String getType(@NotNull PsiElement element) {
    return ((AntStructuredElement)element).getSourceElement().getName();
  }

  @NotNull
  public String getDescriptiveName(@NotNull PsiElement element) {
    if( element instanceof AntFile) {
      return ((AntFile)element).getName();
    }
    final AntElement antElement = (AntElement)element;
    final String name = antElement.getName();
    if (name != null) return name;
    return ((AntStructuredElement)antElement).getSourceElement().getName();
  }

  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }
}
