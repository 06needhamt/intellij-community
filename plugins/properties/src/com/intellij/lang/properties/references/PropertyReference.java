package com.intellij.lang.properties.references;

import com.intellij.codeInsight.daemon.QuickFixProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
 */
public class PropertyReference extends PropertyReferenceBase implements QuickFixProvider, LocalQuickFixProvider {
  @Nullable private final String myBundleName;

  public PropertyReference(@NotNull final String key, @NotNull final PsiElement element, final String bundleName, final boolean soft, final TextRange range) {
    super(key, soft, element, range);
    myBundleName = bundleName;
  }

  public PropertyReference(@NotNull String key, @NotNull PsiElement element, @Nullable final String bundleName, final boolean soft) {
    super(key, soft, element);
    myBundleName = bundleName;
  }

  @Nullable
  protected List<PropertiesFile> getPropertiesFiles() {
    if (myBundleName == null) {
      return null;
    }
    return I18nUtil.propertiesFilesByBundleName(myBundleName, myElement);
  }

  public void registerQuickfix(HighlightInfo info, PsiReference reference) {
    List<PropertiesFile> propertiesFiles = I18nUtil.propertiesFilesByBundleName(myBundleName, reference.getElement());
    CreatePropertyFix fix = new CreatePropertyFix(myElement, myKey, propertiesFiles);
    QuickFixAction.registerQuickFixAction(info, fix);
  }

  public LocalQuickFix[] getQuickFixes() {
    List<PropertiesFile> propertiesFiles = I18nUtil.propertiesFilesByBundleName(myBundleName, getElement());
    CreatePropertyFix fix = new CreatePropertyFix(myElement, myKey, propertiesFiles);
    return new LocalQuickFix[] {fix};
  }
}
