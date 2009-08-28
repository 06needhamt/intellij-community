/*
 * @author max
 */
package com.intellij.util;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PsiIconUtil {

  @Nullable
  public static Icon getProvidersIcon(PsiElement element, int flags) {
    final boolean dumb = DumbService.getInstance(element.getProject()).isDumb();
    for (final IconProvider iconProvider : getIconProviders()) {
      if (dumb && !(iconProvider instanceof DumbAware)) {
        continue;
      }

      final Icon icon = iconProvider.getIcon(element, flags);
      if (icon != null) return icon;
    }
    return null;
  }

  private static class IconProviderHolder {
    private static final IconProvider[] ourIconProviders = Extensions.getExtensions(IconProvider.EXTENSION_POINT_NAME);
  }

  private static IconProvider[] getIconProviders() {
    return IconProviderHolder.ourIconProviders;
  }

}