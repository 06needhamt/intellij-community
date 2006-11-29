package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author cdr
*/
public class VisibleHighlightingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.VisibleHighlightingPassFactory");

  public VisibleHighlightingPassFactory(Project project,  TextEditorHighlightingPassRegistrar highlightingPassRegistrar) {
    super(project);
    highlightingPassRegistrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_FOLDING,}, null, false, Pass.UPDATE_VISIBLE);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "VisibleHighlightingPassFactory";
  }

  @Nullable
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull final Editor editor) {
    TextRange textRange = calculateRangeToProcess(editor);
    return new GeneralHighlightingPass(file.getProject(), file, editor.getDocument(), textRange.getStartOffset(), textRange.getEndOffset(), false);
  }

  private static TextRange calculateRangeToProcess(Editor editor) {
    Document document = editor.getDocument();

    int part = FileStatusMap.NORMAL_HIGHLIGHTERS;

    PsiElement dirtyScope = DaemonCodeAnalyzer.getInstance(editor.getProject()).getFileStatusMap().getFileDirtyScope(document, part);
    int startOffset;
    int endOffset;
    if (dirtyScope != null && dirtyScope.isValid()) {
        PsiFile file = dirtyScope.getContainingFile();
        if (file.getTextLength() != document.getTextLength()) {
          LOG.error("Length wrong! dirtyScope:" + dirtyScope,
                    "file length:" + file.getTextLength(),
                    "document length:" + document.getTextLength(),
                    "file stamp:" + file.getModificationStamp(),
                    "document stamp:" + document.getModificationStamp(),
                    "file text     :" + file.getText(),
                    "document text:" + document.getText());
        }
        if (LOG.isDebugEnabled()) {
          LOG.debug("Dirty block optimization works");
        }
        TextRange range = dirtyScope.getTextRange();
        startOffset = range.getStartOffset();
        endOffset = range.getEndOffset();
    }
    else {
      /*
      if (LOG.isDebugEnabled()) {
        LOG.debug("Do not update highlighters - highlighters are up to date");
      }
      */
      startOffset = Integer.MAX_VALUE;
      endOffset = Integer.MIN_VALUE;
    }
    Rectangle rect = editor.getScrollingModel().getVisibleArea();
    LogicalPosition startPosition = editor.xyToLogicalPosition(new Point(rect.x, rect.y));

    int visibleStart = editor.logicalPositionToOffset(startPosition);
    if (visibleStart > startOffset) {
      startOffset = visibleStart;
    }
    LogicalPosition endPosition = editor.xyToLogicalPosition(new Point(rect.x + rect.width, rect.y + rect.height));

    int visibleEnd = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0));
    if (visibleEnd < endOffset) {
      endOffset = visibleEnd;
    }

    return new TextRange(startOffset, endOffset);
  }
}
