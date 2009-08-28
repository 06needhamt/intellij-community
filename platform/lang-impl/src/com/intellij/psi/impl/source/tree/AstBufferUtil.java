/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;

public class AstBufferUtil {
  private AstBufferUtil() {}

  public static int toBuffer(ASTNode element, char[] buffer, int offset) {
    return toBuffer(element, buffer, offset, null);
  }

  public static int toBuffer(ASTNode element, char[] buffer, int offset, TokenSet skipTypes) {
    if (element instanceof ForeignLeafPsiElement || skipTypes != null && skipTypes.contains(element.getElementType())) return offset;
    if (element instanceof LeafElement) {
      return ((LeafElement)element).copyTo(buffer, offset);
    }

    if (element instanceof LazyParseableElement) {
      LazyParseableElement lpe = (LazyParseableElement)element;
      int lpeResult = lpe.copyTo(buffer, offset);
      if (lpeResult > 0) return lpeResult;
    }

    int curOffset = offset;
    for (TreeElement child = (TreeElement)element.getFirstChildNode(); child != null; child = child.getTreeNext()) {
      curOffset = toBuffer(child, buffer, curOffset, skipTypes);
    }
    return curOffset;
  }
}
