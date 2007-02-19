package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
*/
public class PostHighlightingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public PostHighlightingPassFactory(Project project, TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL,}, null, true, Pass.POST_UPDATE_ALL);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "PostHighlightingPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = GeneralHighlightingPassFactory.calculateRangeToProcessForSyntaxPass(editor);
    if (textRange == null) return null;
    int startOffset = textRange.getStartOffset();
    int endOffset = textRange.getEndOffset();
    return new PostHighlightingPass(myProject, file, editor, startOffset, endOffset);
  }
}
