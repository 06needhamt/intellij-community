package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2003
 * Time: 22:10:20
 * To change this template use Options | File Templates.
 */
public class MatchUtils {
  public static final String SPECIAL_CHARS = "*(){}[]^$\\.-|";

  public static final boolean compareWithNoDifferenceToPackage(final String typeImage, final String typeImage2) {
    return typeImage2.endsWith(typeImage) && (
      typeImage.length() == typeImage2.length() ||
      typeImage2.charAt(typeImage2.length()-typeImage.length()-1)=='.' // package separator
    );
  }

  public static boolean within(PsiElement element, int start, int end) {
    if (element == null) return false;

    int elementOffset = element.getTextOffset();
    if (element instanceof PsiNamedElement) {
      PsiElement el;
      // for method or class its start is just connection of the nameelement, so
      // change it accordingly
      for(el = element.getFirstChild();
          el!=null && !(el instanceof PsiModifierList);
          el = el.getNextSibling()
         );
      if (el!=null) elementOffset = el.getTextOffset();
    }

    return (elementOffset >= start) &&
      (end >= elementOffset + element.getTextLength());
  }

  public static PsiElement getReferencedElement(final PsiElement element) {
    if (element instanceof PsiReference) {
      return ((PsiReference)element).resolve();
    }

    if (element instanceof PsiTypeElement) {
      PsiType type = ((PsiTypeElement)element).getType();

      if (type instanceof PsiArrayType) {
        type = ((PsiArrayType)type).getComponentType();
      }
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolve();
      }
      return null;
    }
    return element;
  }
}
