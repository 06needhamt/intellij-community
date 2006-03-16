package com.intellij.psi.filters.getters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 24.11.2003
 * Time: 14:17:59
 * To change this template use Options | File Templates.
 */
public class HtmlAttributeValueGetter extends XmlAttributeValueGetter {
  private boolean myCaseSensitive;

  public HtmlAttributeValueGetter(boolean _caseSensitive) {
    myCaseSensitive = _caseSensitive;
  }

  @NonNls protected String[] addSpecificCompletions(final PsiElement context) {
    if (!(context instanceof XmlAttribute)) return null;

    XmlAttribute attribute = (XmlAttribute)context;
    @NonNls String name = attribute.getName();
    if (!myCaseSensitive) name = name.toLowerCase();

    final String namespace = attribute.getParent().getNamespace();
    if (XmlUtil.XHTML_URI.equals(namespace) || XmlUtil.HTML_URI.equals(namespace)) {

      if ("target".equals(name)) {
        return new String[] {"_blank","_top","_self","_parent"};
      } else if ("enctype".equals(name)) {
        return new String[] {"multipart/form-data","application/x-www-form-urlencoded"};
      }
    }

    return null;
  }
}
