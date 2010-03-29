package com.jetbrains.python;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonFoldingBuilder implements FoldingBuilder, DumbAware {
  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
    List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
    appendDescriptors(node, descriptors);
    return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
  }

  private static void appendDescriptors(ASTNode node, List<FoldingDescriptor> descriptors) {
    if (node.getElementType() == PyElementTypes.STATEMENT_LIST) {
      IElementType elType = node.getTreeParent().getElementType();
      if (elType == PyElementTypes.FUNCTION_DECLARATION || elType == PyElementTypes.CLASS_DECLARATION) {
        ASTNode colon = node.getTreeParent().findChildByType(PyTokenTypes.COLON);
        if (colon != null && colon.getStartOffset() + 1 < colon.getTextRange().getEndOffset()) {
          descriptors
            .add(new FoldingDescriptor(node, new TextRange(colon.getStartOffset() + 1, node.getStartOffset() + node.getTextLength())));
        }
        else {
          TextRange range = node.getTextRange();
          if (range.getStartOffset() < range.getEndOffset() - 1) { // only for ranges at leas 1 char wide
            descriptors.add(new FoldingDescriptor(node, range));
          }
        }
      }
    }

    ASTNode child = node.getFirstChildNode();
    while (child != null) {
      appendDescriptors(child, descriptors);
      child = child.getTreeNext();
    }
  }

  public String getPlaceholderText(@NotNull ASTNode node) {
    return "...";
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return false;
  }
}
