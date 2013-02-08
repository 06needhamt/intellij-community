package com.intellij.structuralsearch.extenders;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.css.CssFileType;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class CssStructuralSearchProfile extends StructuralSearchProfileBase {
  @NotNull
  @Override
  protected String[] getVarPrefixes() {
    return new String[]{"aaaaaaaaa"};
  }

  @NotNull
  @Override
  protected LanguageFileType getFileType() {
    return CssFileType.INSTANCE;
  }

  @Nullable
  @Override
  public String getContext(@NotNull String pattern, @Nullable Language language, String contextName) {
    return pattern.indexOf('{') < 0
           ? ".c { $$PATTERN_PLACEHOLDER$$ }"
           : super.getContext(pattern, language, contextName);
  }
}
