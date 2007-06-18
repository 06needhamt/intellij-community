package com.intellij.lang.ant.psi.impl.reference.providers;

import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntFileReferenceSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AntFileReferenceProvider extends GenericReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    AntStructuredElement antElement = (AntStructuredElement)element;
    final List<String> referenceAttributes = antElement.getFileReferenceAttributes();
    if (referenceAttributes.isEmpty()) {
      return PsiReference.EMPTY_ARRAY;
    }
    final List<PsiReference> refList = new ArrayList<PsiReference>();
    for (String attrib : referenceAttributes) {
      final XmlAttribute attr = antElement.getSourceElement().getAttribute(attrib, null);
      if (attr == null) {
        continue;
      }
      final XmlAttributeValue xmlAttributeValue = attr.getValueElement();
      if (xmlAttributeValue == null) {
        continue;
      }
      final String attrValue = attr.getValue();
      if (attrValue == null || attrValue.length() == 0 || isSingleSlash(attrValue) || attrValue.indexOf("@{") >= 0) {
        continue;
      }
      final AntFileReferenceSet refSet = new AntFileReferenceSet(antElement, xmlAttributeValue, this);
      refList.addAll(Arrays.asList(refSet.getAllReferences()));
    }
    return refList.toArray(new PsiReference[refList.size()]);
  }

  private static boolean isSingleSlash(final String attrValue) {
    return "/".equals(attrValue) || "\\".equals(attrValue);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str,
                                              PsiElement position,
                                              ReferenceType type,
                                              int offsetInPosition) {
    return getReferencesByElement(position);
  }
}
