package com.intellij.lang.ant.validation;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntGenericReference;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

public class AntAnnotator implements Annotator {

  public void annotate(PsiElement psiElement, AnnotationHolder holder) {
    AntElement element = (AntElement)psiElement;
    if (element instanceof AntStructuredElement) {
      final AntStructuredElement se = (AntStructuredElement)element;
      AntTypeDefinition def = se.getTypeDefinition();
      final String name = se.getName();
      if (def == null) {
        holder.createErrorAnnotation(se, AntBundle.getMessage("undefined.element", name));
      }
      else {
        checkValidAttributes(se, def, holder);
        final AntElement parent = se.getAntParent();
        if (parent instanceof AntStructuredElement) {
          final AntStructuredElement pe = (AntStructuredElement)parent;
          final AntTypeDefinition parentDef = pe.getTypeDefinition();
          if (parentDef != null && parentDef.getNestedClassName(def.getTypeId()) == null) {
            final TextRange textRange = new TextRange(0, name.length()).shiftRight(se.getSourceElement().getTextOffset());
            holder.createErrorAnnotation(textRange, AntBundle.getMessage("nested.element.is.not.allowed.here", name));
          }
        }
      }
    }
    checkReferences(element, holder);
  }

  private static void checkValidAttributes(AntStructuredElement se, AntTypeDefinition def, AnnotationHolder holder) {
    final XmlTag sourceElement = se.getSourceElement();
    for (XmlAttribute attr : sourceElement.getAttributes()) {
      final String name = attr.getName();
      final AntAttributeType type = def.getAttributeType(name);
      if (type == null) {
        holder.createErrorAnnotation(se, AntBundle.getMessage("attribute.is.not.allowed.here", name));
      }
      else {
        final String value = attr.getValue();
        if (type == AntAttributeType.INTEGER) {
          try {
            Integer.parseInt(value);
          }
          catch (NumberFormatException e) {
            holder.createErrorAnnotation(attr, AntBundle.getMessage("integer.attribute.has.invalid.value", name));
          }
        }
      }
    }
  }

  private static void checkReferences(AntElement element, @NonNls AnnotationHolder holder) {
    PsiReference[] refs = element.getReferences();
    for (PsiReference ref : refs) {
      if (ref.resolve() == null) {
        final TextRange absoluteRange = ref.getRangeInElement().shiftRight(ref.getElement().getTextRange().getStartOffset());
        final Annotation annotation = holder.createErrorAnnotation(absoluteRange, ((AntGenericReference)ref).getUnresolvedMessagePattern());
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
  }
}
