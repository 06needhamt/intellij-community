package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.codeInsight.daemon.Validator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public class XmlElementDescriptorImpl implements XmlElementDescriptor, PsiWritableMetaData, Validator {
  protected XmlTag myDescriptorTag;
  protected volatile XmlNSDescriptor NSDescriptor;
  private volatile @Nullable Validator myValidator;

  @NonNls
  public static final String QUALIFIED_ATTR_VALUE = "qualified";
  @NonNls
  public static final String NONQUALIFIED_ATTR_VALUE = "unqualified";
  @NonNls
  private static final String ELEMENT_FORM_DEFAULT = "elementFormDefault";

  public XmlElementDescriptorImpl(XmlTag descriptorTag) {
    myDescriptorTag = descriptorTag;
  }

  public XmlElementDescriptorImpl() {}

  public PsiElement getDeclaration(){
    return myDescriptorTag;
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place){
    return true;
  }

  public String getName(PsiElement context){
    String value = myDescriptorTag.getAttributeValue("name");

    if(context instanceof XmlElement){
      final String namespace = getNamespaceByContext(context);
      final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);

      if(tag != null){
        final String namespacePrefix = tag.getPrefixByNamespace(namespace);

        if (namespacePrefix != null && namespacePrefix.length() > 0) {
          final XmlTag rootTag = ((XmlFile)myDescriptorTag.getContainingFile()).getDocument().getRootTag();

          if (rootTag != null && NONQUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue(ELEMENT_FORM_DEFAULT))) {
            value = XmlUtil.findLocalNameByQualifiedName(value);
          } else {
            value = namespacePrefix + ":" + XmlUtil.findLocalNameByQualifiedName(value);
          }
        }
      }
    }
    return value;
  }

  /** getter for _local_ name */
  public String getName() {
    return XmlUtil.findLocalNameByQualifiedName(getName(null));
  }

  public String getNamespaceByContext(PsiElement context){
    //while(context != null){
    //  if(context instanceof XmlTag){
    //    final XmlTag contextTag = ((XmlTag)context);
    //    final XmlNSDescriptorImpl schemaDescriptor = XmlUtil.findXmlNSDescriptorByType(contextTag);
    //    if (schemaDescriptor != null) {
    //      return schemaDescriptor.getDefaultNamespace();
    //    }
    //  }
    //  context = context.getContext();
    //}
    return getNamespace();
  }

  public String getNamespace(){
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(getName(null));
    final XmlNSDescriptorImpl xmlNSDescriptor = (XmlNSDescriptorImpl)getNSDescriptor();
    if(xmlNSDescriptor == null || myDescriptorTag == null) return XmlUtil.EMPTY_URI;
    return "".equals(namespacePrefix) ?
           xmlNSDescriptor.getDefaultNamespace() :
           myDescriptorTag.getNamespaceByPrefix(namespacePrefix);
  }

  public void init(PsiElement element){
    if (myDescriptorTag!=element && myDescriptorTag!=null) {
      NSDescriptor = null;
    }
    myDescriptorTag = (XmlTag) element;
  }

  public Object[] getDependences(){
    return new Object[]{myDescriptorTag};
  }

  public XmlNSDescriptor getNSDescriptor() {
    XmlNSDescriptor nsDescriptor = NSDescriptor;
    if (nsDescriptor ==null) {
      final XmlFile file = XmlUtil.getContainingFile(getDeclaration());
      if(file == null) return null;
      final XmlDocument document = file.getDocument();
      if(document == null) return null;
      NSDescriptor = nsDescriptor = (XmlNSDescriptor)document.getMetaData();
    }

    return nsDescriptor;
  }

  public TypeDescriptor getType() {
    final XmlNSDescriptor nsDescriptor = getNSDescriptor();
    if (nsDescriptor == null) return null;

    TypeDescriptor type = ((XmlNSDescriptorImpl) nsDescriptor).getTypeDescriptor(myDescriptorTag);
    if (type == null) {
      String substAttr = myDescriptorTag.getAttributeValue("substitutionGroup");
      if (substAttr != null) {
        final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(substAttr);
        final String namespace = "".equals(namespacePrefix) ?
                                 ((XmlNSDescriptorImpl)getNSDescriptor()).getDefaultNamespace() :
                                 myDescriptorTag.getNamespaceByPrefix(namespacePrefix);
        final String local = XmlUtil.findLocalNameByQualifiedName(substAttr);
        final XmlElementDescriptorImpl originalElement = (XmlElementDescriptorImpl)((XmlNSDescriptorImpl)getNSDescriptor()).getElementDescriptor(local, namespace);
        if (originalElement != null) {
          type = originalElement.getType();
        }
      }
    }
    return type;
  }

  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    XmlElementDescriptor[] elementsDescriptors = getElementsDescriptors();

    final TypeDescriptor type = getType();

    if (type instanceof ComplexTypeDescriptor) {
      final ComplexTypeDescriptor descriptor = (ComplexTypeDescriptor)type;
      String contextNs;
      PsiFile containingFile = context != null ? context.getContainingFile():null;

      if (context != null && !containingFile.isPhysical() && containingFile.getOriginalFile() != null) {
        containingFile = containingFile.getOriginalFile();
        context = context.getParentTag();
      }

      if (context != null &&
          ( descriptor.canContainTag(context.getLocalName(), contextNs = context.getNamespace() ) &&
            (!contextNs.equals(getNamespace()) || descriptor.hasAnyInContentModel())
          ) ) {
        final XmlNSDescriptor nsDescriptor = getNSDescriptor();

        if (nsDescriptor != null) {
          elementsDescriptors = ArrayUtil.mergeArrays(
            elementsDescriptors,
            nsDescriptor.getRootElementsDescriptors(((XmlFile)containingFile).getDocument()),
            XmlElementDescriptor.class
          );
        }
      }
    }

    return elementsDescriptors;
  }

  private XmlElementDescriptor[] getElementsDescriptors() {
    TypeDescriptor type = getType();

    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor typeDescriptor = (ComplexTypeDescriptor)type;

      return typeDescriptor.getElements();
    }

    return EMPTY_ARRAY;
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    TypeDescriptor type = getType();

    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor typeDescriptor = (ComplexTypeDescriptor)type;
      XmlAttributeDescriptor[] attributeDescriptors = typeDescriptor.getAttributes();

      if (context != null) {
        final String contextNs = context.getNamespace();
        final XmlDocument doc = PsiTreeUtil.getParentOfType(context, XmlDocument.class);

        for(String ns:context.knownNamespaces()) {
          if (!contextNs.equals(ns) && ns.length() > 0) {
            if (typeDescriptor.canContainAttribute("any",ns)) {
              final XmlNSDescriptor descriptor = context.getNSDescriptor(ns, true);

              if (descriptor instanceof XmlNSDescriptorImpl) {
                attributeDescriptors = ArrayUtil.mergeArrays(
                  attributeDescriptors,
                  ((XmlNSDescriptorImpl)descriptor).getRootAttributeDescriptors(doc),
                  XmlAttributeDescriptor.class
                );
              }
            }
          }
        }
      }
      return attributeDescriptors;
    }

    return XmlAttributeDescriptor.EMPTY;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context){
    return getAttributeDescriptorImpl(attributeName,context);
  }

  private XmlAttributeDescriptor getAttributeDescriptorImpl(final String attributeName, XmlTag context) {
    final String localName = XmlUtil.findLocalNameByQualifiedName(attributeName);
    final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(attributeName);
    final XmlNSDescriptorImpl xmlNSDescriptor = (XmlNSDescriptorImpl)getNSDescriptor();
    final String namespace = "".equals(namespacePrefix) ?
                             ((xmlNSDescriptor != null)?xmlNSDescriptor.getDefaultNamespace():"") :
                             context.getNamespaceByPrefix(namespacePrefix);

    final XmlAttributeDescriptor attribute = getAttribute(localName, namespace, context);
    
    if (attribute instanceof AnyXmlAttributeDescriptor && namespace.length() > 0) {
      final XmlNSDescriptor candidateNSDescriptor = context.getNSDescriptor(namespace, true);

      if (candidateNSDescriptor instanceof XmlNSDescriptorImpl) {
        final XmlNSDescriptorImpl nsDescriptor = (XmlNSDescriptorImpl)candidateNSDescriptor;

        final XmlAttributeDescriptorImpl xmlAttributeDescriptor = nsDescriptor.getAttribute(localName, namespace);
        if (xmlAttributeDescriptor != null) return xmlAttributeDescriptor;
      }
    }
    return attribute;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute){
    return getAttributeDescriptorImpl(attribute.getName(),attribute.getParent());
  }

  private XmlAttributeDescriptor getAttribute(String attributeName, String namespace, XmlTag context) {
    XmlAttributeDescriptor[] descriptors = getAttributesDescriptors(context);

    for (XmlAttributeDescriptor descriptor : descriptors) {
      if (descriptor.getName().equals(attributeName)) {
        return descriptor;
      }
    }

    TypeDescriptor type = getType();
    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor descriptor = (ComplexTypeDescriptor)type;
      if (descriptor.canContainAttribute(attributeName, namespace)) {
        return new AnyXmlAttributeDescriptor(attributeName);
      }
    }

    return null;
  }

  public int getContentType() {
    TypeDescriptor type = getType();

    if (type instanceof ComplexTypeDescriptor) {
      final XmlElementDescriptor[] elements = ((ComplexTypeDescriptor)type).getElements();

      if (elements.length > 0) return CONTENT_TYPE_CHILDREN;
      return CONTENT_TYPE_EMPTY;
    }

    return CONTENT_TYPE_MIXED;
  }

  public XmlElementDescriptor getElementDescriptor(final String name) {
      final String localName = XmlUtil.findLocalNameByQualifiedName(name);
      final String namespacePrefix = XmlUtil.findPrefixByQualifiedName(name);
      final String namespace = "".equals(namespacePrefix) ?
                               ((XmlNSDescriptorImpl)getNSDescriptor()).getDefaultNamespace() :
                               myDescriptorTag.getNamespaceByPrefix(namespacePrefix);
    return getElementDescriptor(localName, namespace, null, name);
  }

  protected XmlElementDescriptor getElementDescriptor(final String localName, final String namespace, XmlElement context, String fullName) {
    XmlElementDescriptor[] elements = getElementsDescriptors();

    for (XmlElementDescriptor element1 : elements) {
      final XmlElementDescriptorImpl element = (XmlElementDescriptorImpl)element1;
      final String namespaceByContext = element.getNamespaceByContext(context);

      if (element.getName().equals(localName)) {
        if ( namespace == null ||
             namespace.equals(namespaceByContext) ||
             namespaceByContext.equals(XmlUtil.EMPTY_URI) ||
             element.getName(context).equals(fullName)
           ) {
          return element;
        } else if ((namespace == null || namespace.length() == 0) &&
                   element.getDefaultName().equals(localName)) {
          return element;
        } else {
          final XmlNSDescriptor descriptor = context instanceof XmlTag? ((XmlTag)context).getNSDescriptor(namespace, true) : null;

          // schema's targetNamespace could be different from file systemId used as NS
          if (descriptor instanceof XmlNSDescriptorImpl &&
              ((XmlNSDescriptorImpl)descriptor).getDefaultNamespace().equals(namespaceByContext)
             ) {
            return element;
          }
        }
      }
    }

    TypeDescriptor type = getType();
    if (type instanceof ComplexTypeDescriptor) {
      ComplexTypeDescriptor descriptor = (ComplexTypeDescriptor)type;
      if (descriptor.canContainTag(localName, namespace)) {
        return new AnyXmlElementDescriptor(this, getNSDescriptor());
      }
    }

    return null;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag element){
    XmlElementDescriptor elementDescriptor = getElementDescriptor(
      element.getLocalName(),
      element.getNamespace(),
      (XmlElement)element.getParent(),
      element.getName()
    );

    if(elementDescriptor == null || element.getAttributeValue("xsi:type") != null){
      final XmlElementDescriptor xmlDescriptorByType = XmlUtil.findXmlDescriptorByType(element);
      if (xmlDescriptorByType != null) elementDescriptor = xmlDescriptorByType;
    }
    return elementDescriptor;
  }

  public String getQualifiedName() {
    if (!"".equals(getNS())) {
      return getNS() + ":" + getName();
    }

    return getName();
  }

  private String getNS(){
    return XmlUtil.findNamespacePrefixByURI((XmlFile) myDescriptorTag.getContainingFile(), getNamespace());
  }

  public String getDefaultName() {
    final PsiFile psiFile = myDescriptorTag.getContainingFile();
    XmlTag rootTag = psiFile instanceof XmlFile ?((XmlFile)psiFile).getDocument().getRootTag():null;

    if (rootTag != null && QUALIFIED_ATTR_VALUE.equals(rootTag.getAttributeValue(ELEMENT_FORM_DEFAULT))) {
      return getQualifiedName();
    }

    return getName();
  }

  public boolean isAbstract() {
    return Boolean.valueOf(myDescriptorTag.getAttributeValue("abstract")).booleanValue();
  }

  public void setName(String name) throws IncorrectOperationException {
    NamedObjectDescriptor.setName(myDescriptorTag, name);
  }

  public void setValidator(final Validator validator) {
    myValidator = validator;
  }

  public void validate(PsiElement context, ValidationHost host) {
    Validator validator = myValidator;
    if (validator != null) {
      validator.validate(context, host);
    }
  }

  public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
    final TypeDescriptor type = getType();
    
    if (type instanceof ComplexTypeDescriptor) {
      return ((ComplexTypeDescriptor)type).canContainTag("a", namespace) ||
             XmlUtil.tagFromTemplateFramework(context) ||
             XmlUtil.nsFromTemplateFramework(namespace)
        ;
    }
    return false;
  }
}
