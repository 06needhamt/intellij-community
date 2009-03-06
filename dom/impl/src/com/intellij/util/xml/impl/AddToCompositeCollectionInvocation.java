/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.xml.EvaluatedXmlName;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * @author peter
*/
class AddToCompositeCollectionInvocation implements Invocation {
  private final CollectionChildDescriptionImpl myMainDescription;
  private final Set<CollectionChildDescriptionImpl> myQnames;
  private final Type myType;

  public AddToCompositeCollectionInvocation(final CollectionChildDescriptionImpl tagName, final Set<CollectionChildDescriptionImpl> qnames, final Type type) {
    myMainDescription = tagName;
    myQnames = qnames;
    myType = type;
  }

  public Object invoke(final DomInvocationHandler<?> handler, final Object[] args) throws Throwable {
    Set<XmlTag> set = CollectionFactory.newTroveSet();
    for (final CollectionChildDescriptionImpl qname : myQnames) {
      set.addAll(qname.getTagsGetter().fun(handler));
    }

    final XmlTag tag = handler.ensureTagExists();
    int index = args != null && args.length == 1 ? (Integer)args[0] : Integer.MAX_VALUE;

    XmlTag lastTag = null;
    int i = 0;
    final XmlTag[] tags = tag.getSubTags();
    for (final XmlTag subTag : tags) {
      if (i == index) break;
      if (set.contains(subTag)) {
        lastTag = subTag;
        i++;
      }
    }
    final DomManagerImpl manager = handler.getManager();
    final boolean b = manager.setChanging(true);
    try {
      final EvaluatedXmlName evaluatedXmlName = handler.createEvaluatedXmlName(myMainDescription.getXmlName());
      final XmlTag emptyTag = handler.createChildTag(evaluatedXmlName);
      final XmlTag newTag;
      if (lastTag == null) {
        if (tags.length == 0) {
          newTag = (XmlTag)tag.add(emptyTag);
        }
        else {
          newTag = (XmlTag)tag.addBefore(emptyTag, tags[0]);
        }
      }
      else {
        newTag = (XmlTag)tag.addAfter(emptyTag, lastTag);
      }

      return new CollectionElementInvocationHandler(myType, newTag, myMainDescription, handler).getProxy();
    }
    finally {
      manager.setChanging(b);
    }
  }


}
