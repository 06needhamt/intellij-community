package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 24.11.2003
 * Time: 14:17:59
 * To change this template use Options | File Templates.
 */
public class XmlAttributeValueGetter implements ContextGetter {
  public XmlAttributeValueGetter() {}

  public Object[] get(PsiElement context, CompletionContext completionContext) {
    return getApplicableAttributeVariants(context, completionContext);
  }

  private Object[] getApplicableAttributeVariants(PsiElement _context, CompletionContext completionContext) {
    PsiElement context = _context;
    if(context != null) {
      context = PsiTreeUtil.getParentOfType(context, XmlAttribute.class);
      if (context == null) {
        context = PsiTreeUtil.getParentOfType(_context, XmlAttributeValue.class);
      }
    }

    if(context instanceof XmlAttribute) {
      final XmlAttributeDescriptor descriptor = ((XmlAttribute)context).getDescriptor();

      if(descriptor != null) {
        if (descriptor.isFixed()) {
          final String defaultValue = descriptor.getDefaultValue();
          return defaultValue == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : new Object[] { defaultValue };
        }

        String[] values = descriptor instanceof BasicXmlAttributeDescriptor ?
                          ((BasicXmlAttributeDescriptor)descriptor).getEnumeratedValues((XmlElement)context):descriptor.getEnumeratedValues();

        final String[] strings = addSpecificCompletions(context);

        if(values == null || values.length==0) {
          values = strings;
        } else if (strings != null) {
          values = ArrayUtil.mergeArrays(values, strings, String.class);
        }

        return values == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : values;
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  protected String[] addSpecificCompletions(final PsiElement context) {
    return null;
  }

}
