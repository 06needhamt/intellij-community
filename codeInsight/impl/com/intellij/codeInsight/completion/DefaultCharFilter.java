/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:01:22 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.Language;

public class DefaultCharFilter implements CharFilter {
  private final PsiFile myFile;
  private boolean myWithinLiteral;
  private CharFilter myDelegate = null;

  public DefaultCharFilter(PsiFile file, int offset) {
    myFile = file;

    PsiElement psiElement = file.findElementAt(offset);
    if (psiElement == null && offset > 0) psiElement = file.findElementAt(offset - 1);

    if (myFile instanceof XmlFile) {
      boolean inJavaContext = false;

      if (psiElement != null) {
        PsiElement elementToTest = psiElement;
        if (elementToTest instanceof PsiWhiteSpace) {
          elementToTest = elementToTest.getParent(); // JSPX has whitespace with language Java
        }

        final Language language = elementToTest.getLanguage();
        if (StdLanguages.JAVA.equals(language) || language.getID().equals("JavaScript")) {
          inJavaContext = true;
        }
      }
      
      if (!inJavaContext) {
        final boolean withinTag = psiElement != null && psiElement.getParent() instanceof XmlTag;
        myDelegate = PsiUtil.isInJspFile(myFile) ? new JspCharFilter(withinTag) : new XmlCharFilter(withinTag);
      }
    } else {

      if (psiElement != null && psiElement.getParent() instanceof PsiLiteralExpression) {
        myWithinLiteral = true;
      }
    }
  }

  public int accept(char c, final String prefix) {
    if (myDelegate != null) return myDelegate.accept(c, prefix);

    if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
    switch(c){
      case '.': if (myWithinLiteral) return CharFilter.ADD_TO_PREFIX;
      case ',':
      case ';':
      case '=':
      case ' ':
      case ':':
      case '(':
        return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;

      default:
        return CharFilter.HIDE_LOOKUP;
    }
  }
}
