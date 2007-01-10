/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.structureView.impl.xml;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

public class XmlTagTreeElement extends PsiTreeElementBase<XmlTag>{
  @NonNls private static final String ID_ATTR_NAME = "id";
  @NonNls private static final String NAME_ATTR_NAME = "name";

  public XmlTagTreeElement(XmlTag tag) {
    super(tag);
  }

  public Collection<StructureViewTreeElement> getChildrenBase() {
    XmlTag[] subTags = getElement().getSubTags();
    final XmlStructureViewElementProvider[] providers = (XmlStructureViewElementProvider[])
      Extensions.getExtensions(XmlStructureViewElementProvider.EXTENSION_POINT_NAME);

    return ContainerUtil.map2List(subTags, new Function<XmlTag, StructureViewTreeElement>() {
      public StructureViewTreeElement fun(final XmlTag xmlTag) {
        for (final XmlStructureViewElementProvider provider : providers) {
          final StructureViewTreeElement element = provider.createCustomXmlTagTreeElement(xmlTag);
          if (element != null) {
            return element;
          }
        }
        return new XmlTagTreeElement(xmlTag);
      }
    });
  }

  public String getPresentableText() {
    final XmlTag element = getElement();
    String id = element.getAttributeValue(ID_ATTR_NAME);
    if (id == null) id = element.getAttributeValue(NAME_ATTR_NAME);
    id = toCanonicalForm(id);

    if (id != null) return id + ":" + element.getLocalName();
    return element.getName();
  }

  public String getLocationString() {
    final StringBuffer buffer = new StringBuffer();
    final XmlTag element = getElement();
    final XmlAttribute[] attributes = element.getAttributes();

    String id = element.getAttributeValue(ID_ATTR_NAME);
    String usedAttrName = null;

    if (id == null) {
      id = element.getAttributeValue(NAME_ATTR_NAME);
      if (id != null) usedAttrName = NAME_ATTR_NAME;
    }
    else {
      usedAttrName = ID_ATTR_NAME;
    }

    id = toCanonicalForm(id);

    for (XmlAttribute attribute : attributes) {
      if (buffer.length() != 0) {
        buffer.append(" ");
      }

      final String name = attribute.getName();
      if (usedAttrName != null &&
          id != null &&
          usedAttrName.equals(name)
        ) {
        continue; // we output this name in name
      }

      buffer.append(name);
      buffer.append("=");
      buffer.append("\"");
      buffer.append(attribute.getValue());
      buffer.append("\"");
    }
    return buffer.toString();
  }

  private static String toCanonicalForm(String id) {
    if (id != null) {
      id = id.trim();
      if (id.length() == 0) id = null;
    }
    return id;
  }
}
