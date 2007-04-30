package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntRefIdReference;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AntRefIdReferenceProvider extends GenericReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    if (!(element instanceof AntStructuredElement)) {
      return PsiReference.EMPTY_ARRAY;
    }
    final AntStructuredElement se = (AntStructuredElement)element;
    final List<PsiReference> refs = new ArrayList<PsiReference>();
    for (XmlAttribute attr : se.getSourceElement().getAttributes()) {
      if (!isRefAttribute(se, attr.getName())) {
        continue;
      }
      final XmlAttributeValue valueElement = attr.getValueElement();
      if (valueElement == null) {
        continue;
      }
      final int offsetInPosition = valueElement.getTextRange().getStartOffset() - se.getTextRange().getStartOffset() + 1;
      final String attrValue = attr.getValue();
      if (attrValue == null || attrValue.indexOf("@{") >= 0) {
        continue;
      }
      refs.add(new AntRefIdReference(this, se, attrValue, new TextRange(offsetInPosition, offsetInPosition + attrValue.length()), attr));
    }
    return refs.toArray(new PsiReference[refs.size()]);
  }

  private static boolean isRefAttribute(AntStructuredElement element, final String attribName) {
    if ("refid".equals(attribName)) {
      return true;
    }
    final AntTypeDefinition typeDef = element.getTypeDefinition();
    return typeDef != null? AntAttributeType.ID_REFERENCE == typeDef.getAttributeType(attribName) : false;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }
}