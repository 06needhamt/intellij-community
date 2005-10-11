package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.xml.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.dtd.XmlNSDescriptorImpl;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 4, 2005
 * Time: 3:58:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class DtdReferencesProvider implements PsiReferenceProvider {
  static class ElementReference implements PsiReference {
    private XmlElement myElement;
    private XmlElement myNameElement;
    private TextRange myRange;

    public ElementReference(final XmlElement element, final XmlElement nameElement) {
      myElement = element;
      myNameElement = nameElement;

      final int textOffset = element.getTextOffset();
      final int nameTextOffset = nameElement.getTextOffset();

      myRange = new TextRange(
        nameTextOffset - textOffset,
        nameTextOffset + nameElement.getTextLength() - textOffset
      );

    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return myRange;
    }

    @Nullable
    public PsiElement resolve() {
      XmlNSDescriptor rootTagNSDescriptor = getNsDescriptor();

      if (rootTagNSDescriptor instanceof XmlNSDescriptorImpl) {
        final XmlElementDescriptor elementDescriptor = ((XmlNSDescriptorImpl)rootTagNSDescriptor).getElementDescriptor(getCanonicalText());

        if (elementDescriptor != null) return elementDescriptor.getDeclaration();
      }
      return null;
    }

    private XmlNSDescriptor getNsDescriptor() {
      final XmlDocument document = ((XmlFile)myElement.getContainingFile()).getDocument();
      XmlNSDescriptor rootTagNSDescriptor = document.getRootTagNSDescriptor();
      if (rootTagNSDescriptor == null) rootTagNSDescriptor = (XmlNSDescriptor)document.getMetaData();
      return rootTagNSDescriptor;
    }

    public String getCanonicalText() {
      final XmlElement nameElement = myNameElement;
      return nameElement != null ? nameElement.getText() : "";
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      myNameElement = ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myNameElement).handleContentChange(
        myNameElement,
        new TextRange(0,myNameElement.getTextLength()),
        newElementName
      );

      return null;
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(element, resolve());
    }

    public Object[] getVariants() {
      final XmlNSDescriptor rootTagNSDescriptor = getNsDescriptor();
      return rootTagNSDescriptor.getRootElementsDescriptors(((XmlFile)myElement.getContainingFile()).getDocument());
    }

    public boolean isSoft() {
      return true;
    }
  }

  static class EntityReference implements PsiReference {
    private PsiElement myElement;

    EntityReference(PsiElement element) {
      myElement = element;
    }

    public PsiElement getElement() {
      return myElement;
    }

    public TextRange getRangeInElement() {
      return new TextRange(1,myElement.getTextLength()-1);
    }

    @Nullable
    public PsiElement resolve() {
      return ((XmlEntityRef)myElement).resolve(myElement.getContainingFile());
    }

    public String getCanonicalText() {
      return StringUtil.stripQuotesAroundValue(myElement.getText());
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return null;
    }

    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      return null;
    }

    public boolean isReferenceTo(PsiElement element) {
      return myElement.getManager().areElementsEquivalent(resolve(), element);
    }

    public Object[] getVariants() {
      return new Object[0];
    }

    public boolean isSoft() {
      return false;
    }
  }

  public PsiReference[] getReferencesByElement(PsiElement element) {
    XmlElement nameElement = null;

    if (element instanceof XmlDoctype) {
      nameElement = ((XmlDoctype)element).getNameElement();
    } else if (element instanceof XmlElementDecl) {
      nameElement = ((XmlElementDecl)element).getNameElement();
    } else if (element instanceof XmlAttlistDecl) {
      nameElement = ((XmlAttlistDecl)element).getNameElement();
    } else if (element instanceof XmlElementContentSpec) {
      final PsiElement[] children = ((XmlElementContentSpec)element).getChildren();
      final List<PsiReference> psiRefs = new ArrayList<PsiReference>(children.length);
      
      for (int i = 0; i < children.length; i++) {
        final PsiElement child = children[i];
        
        if (child instanceof XmlToken &&
            ((XmlToken)child).getTokenType() == XmlTokenType.XML_NAME
           ) {
          psiRefs.add( new ElementReference((XmlElement)element, (XmlElement)child) );
        }
      }
      
      return psiRefs.toArray(new PsiReference[psiRefs.size()]);
    }

    if (nameElement != null) {
      return new PsiReference[] { new ElementReference((XmlElement)element, nameElement) };
    }

    if (element instanceof XmlEntityRef)
      return new PsiReference[] { new EntityReference(element) };

    return PsiReference.EMPTY_ARRAY;
  }

  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return PsiReference.EMPTY_ARRAY;
  }

  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }

  public ElementFilter getSystemReferenceFilter() {
    return new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        final PsiElement parent = context.getParent();
        
        if((parent instanceof XmlEntityDecl &&
           !((XmlEntityDecl)parent).isInternalReference()
           )
          ) {
          PsiElement prevSibling = context.getPrevSibling();
          if (prevSibling instanceof PsiWhiteSpace) {
            prevSibling = prevSibling.getPrevSibling();
          }

          if (prevSibling instanceof XmlToken &&
              ((XmlToken)prevSibling).getTokenType() == XmlTokenType.XML_DOCTYPE_SYSTEM ||
              prevSibling instanceof XmlAttributeValue
            ) {
            return true;
          }
        }

        return false;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    };
  }
}
