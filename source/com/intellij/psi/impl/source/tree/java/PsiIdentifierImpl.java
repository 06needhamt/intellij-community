package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiIdentifierImpl extends LeafPsiElement implements PsiIdentifier, PsiJavaToken {
  public PsiIdentifierImpl(CharSequence text) {
    super(Constants.IDENTIFIER, text);
  }

  public IElementType getTokenType() {
    return JavaTokenType.IDENTIFIER;
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitIdentifier(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiIdentifier:" + getText();
  }
}
