package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class XmlAttributeValueImpl extends XmlElementImpl implements XmlAttributeValue{
  public XmlAttributeValueImpl() {
    super(XML_ATTRIBUTE_VALUE);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlAttributeValue(this);
  }

  public String getValue() {
    return StringUtil.stripQuotesAroundValue(getText());
  }

  public PsiReference[] getReferences() {
    return ResolveUtil.getReferencesFromProviders(this);
  }

  public PsiElement replaceRangeInText(final TextRange range, String newSubText)
    throws IncorrectOperationException {
    XmlFile file = (XmlFile) getManager().getElementFactory().createFileFromText("dummy.xml", "<a attr=" + getNewText(range, newSubText) + "/>");
    return XmlAttributeValueImpl.this.replace(file.getDocument().getRootTag().getAttributes()[0].getValueElement());
  }

  private String getNewText(final TextRange range, String newSubstring) {
    final String text = XmlAttributeValueImpl.this.getText();
    return text.substring(0, range.getStartOffset()) + newSubstring + text.substring(range.getEndOffset());
  }

  public int getTextOffset() {
    return getTextRange().getStartOffset() + 1;
  }
}
