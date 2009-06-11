package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import org.jetbrains.annotations.NotNull;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiTypeElementImpl");
  private volatile PsiType myCachedType = null;
  private volatile PatchedSoftReference<PsiType> myCachedDetachedType = null;

  public PsiTypeElementImpl() {
    super(JavaElementType.TYPE);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedType = null;
    myCachedDetachedType = null;
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiTypeElement:" + getText();
  }

  @NotNull
  public PsiType getType() {
    PsiType cachedType = myCachedType;
    if (cachedType == null) {
      TreeElement element = getFirstChildNode();
      while (element != null) {
        IElementType elementType = element.getElementType();
        if (element.getTreeNext() == null && ElementType.PRIMITIVE_TYPE_BIT_SET.contains(elementType)) {
          cachedType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createPrimitiveType(element.getText());
          assert cachedType != null;
        }
        else if (elementType == JavaElementType.TYPE) {
          PsiType componentType = ((PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(element)).getType();
          cachedType = getLastChildNode().getElementType() == JavaTokenType.ELLIPSIS ? new PsiEllipsisType(componentType)
                                                                                     : componentType.createArrayType();
        }
        else if (elementType == JavaElementType.JAVA_CODE_REFERENCE) {
          cachedType = new PsiClassReferenceType(getReferenceElement(), null);
        }
        else if (elementType == JavaTokenType.QUEST) {
          cachedType = createWildcardType();
        }
        //else if (elementType == JavaElementType.ANNOTATION) {
        //  PsiAnnotation annotation = JavaPsiFacade.getInstance(getProject()).getElementFactory().createAnnotationFromText(element.getText(), this);
        //  cachedType = createWildcardType();
        //}
        else {
          LOG.error("Unknown element type: " + elementType);
        }
        if (element.getTextLength() != 0) break;
        element = element.getTreeNext();
      }
      
      if (cachedType == null) cachedType = PsiType.NULL;
      myCachedType = cachedType;
    }
    return cachedType;
  }

  public PsiType getDetachedType(PsiElement context) {
    PatchedSoftReference<PsiType> cached = myCachedDetachedType;
    PsiType type = cached == null ? null : cached.get();
    if (type != null) return type;
    try {
      type = JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeFromText(getText(), context);
      myCachedDetachedType = new PatchedSoftReference<PsiType>(type);
    }
    catch (IncorrectOperationException e) {
      return getType();
    }
    return type;
  }

  @NotNull
  private PsiType createWildcardType() {
    final PsiType temp;
    if (getFirstChildNode().getTreeNext() == null) {
      temp = PsiWildcardType.createUnbounded(getManager());
    }
    else {
      if (getLastChildNode().getElementType() == JavaElementType.TYPE) {
        PsiTypeElement bound = (PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(getLastChildNode());
        ASTNode keyword = getFirstChildNode();
        while(keyword != null && keyword.getElementType() != JavaTokenType.EXTENDS_KEYWORD && keyword.getElementType() != JavaTokenType.SUPER_KEYWORD) {
          keyword = keyword.getTreeNext();
        }
        if (keyword != null) {
          IElementType i = keyword.getElementType();
          if (i == JavaTokenType.EXTENDS_KEYWORD) {
            temp = PsiWildcardType.createExtends(getManager(), bound.getType());
          }
          else if (i == JavaTokenType.SUPER_KEYWORD) {
            temp = PsiWildcardType.createSuper(getManager(), bound.getType());
          }
          else {
            LOG.assertTrue(false);
            temp = PsiWildcardType.createUnbounded(getManager());
          }
        }
        else {
          temp = PsiWildcardType.createUnbounded(getManager());
        }
      } else {
        temp = PsiWildcardType.createUnbounded(getManager());
      }
    }
    return temp;
  }

  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    TreeElement firstChildNode = getFirstChildNode();
    if (firstChildNode == null) return null;
    if (firstChildNode.getElementType() == JavaElementType.TYPE) {
      return ((PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(firstChildNode)).getInnermostComponentReferenceElement();
    }
    else {
      return getReferenceElement();
    }
  }

  private PsiJavaCodeReferenceElement getReferenceElement() {
    if (getFirstChildNode().getElementType() != JavaElementType.JAVA_CODE_REFERENCE) return null;
    return (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(getFirstChildNode());
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place){
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }
}

