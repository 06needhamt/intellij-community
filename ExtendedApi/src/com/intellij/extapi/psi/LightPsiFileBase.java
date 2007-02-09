package com.intellij.extapi.psi;

import com.intellij.psi.impl.source.LightPsiFileImpl;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.FileViewProvider;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDialect;
import org.jetbrains.annotations.Nullable;

public abstract class LightPsiFileBase extends LightPsiFileImpl {
  public LightPsiFileBase(final FileViewProvider provider, final Language language) {
    super(provider, language);
  }

  public boolean isDirectory() {
    return false;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @Nullable
  public LanguageDialect getLanguageDialect() {
    return null;
  }
}
