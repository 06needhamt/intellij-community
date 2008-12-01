package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface TemplateContextType {
  ExtensionPointName<TemplateContextType> EP_NAME = ExtensionPointName.create("com.intellij.liveTemplateContext");
  
  String getName();
  boolean isInContext(@NotNull PsiFile file, int offset);
  boolean isInContext(final FileType fileType);

  // these methods mostly exist for serialization compatibility with pre-8.0 live templates
  boolean isEnabled(TemplateContext context);

  void setEnabled(TemplateContext context, boolean value);

  @Nullable
  SyntaxHighlighter createHighlighter();
}
