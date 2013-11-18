// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.json.psi.JsonStringLiteral;
import com.jetbrains.json.psi.JsonVisitor;
import org.jetbrains.annotations.NotNull;

public class JsonStringLiteralImpl extends JsonLiteralImpl implements JsonStringLiteral {

  public JsonStringLiteralImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitStringLiteral(this);
    else super.accept(visitor);
  }

}
