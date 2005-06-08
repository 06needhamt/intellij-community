package com.intellij.psi.impl.source.xml;

import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;

/**
 * @author Mike
 */
public class XmlCommentImpl extends XmlElementImpl implements XmlComment {
  public XmlCommentImpl() {
    super(XML_COMMENT);
  }

  public IElementType getTokenType() {
    return XML_COMMENT;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlComment(this);
  }

  public XmlTag getParentTag() {
    if(getParent() instanceof XmlTag) return (XmlTag)getParent();
    return null;
  }

  public XmlTagChild getNextSiblingInTag() {
    if(getParent() instanceof XmlTag) return (XmlTagChild)getNextSibling();
    return null;
  }

  public XmlTagChild getPrevSiblingInTag() {
    if(getParent() instanceof XmlTag) return (XmlTagChild)getPrevSibling();
    return null;
  }
}
