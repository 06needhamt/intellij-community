package com.intellij.psi.impl.source.tree;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPlainText;
import com.intellij.util.CharTable;

public class PsiPlainTextImpl extends OwnBufferLeafPsiElement implements PsiPlainText {
  protected PsiPlainTextImpl(CharSequence buffer, int startOffset, int endOffset, final CharTable table) {
    super(PLAIN_TEXT, buffer, startOffset, endOffset, table);
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitPlainText(this);
  }

  public String toString(){
    return "PsiPlainText";
  }
}