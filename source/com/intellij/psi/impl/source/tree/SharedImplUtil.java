package com.intellij.psi.impl.source.tree;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PsiTypeElementImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;

//TODO: rename/regroup?

public class SharedImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.SharedImplUtil");

  public static PsiElement getParent(ASTNode thisElement) {
    return SourceTreeToPsiMap.treeElementToPsi(thisElement.getTreeParent());
  }

  public static PsiElement getFirstChild(CompositeElement element) {
    final TreeElement firstChild = element.firstChild;
    return firstChild != null ? SourceTreeToPsiMap.treeElementToPsi(firstChild.getTransformedFirstOrSelf()) : null;
  }

  public static PsiElement getLastChild(CompositeElement element) {
    final TreeElement lastChild = element.lastChild;
    return lastChild != null ? SourceTreeToPsiMap.treeElementToPsi(lastChild.getTransformedLastOrSelf()) : null;
  }

  public static PsiElement getNextSibling(ASTNode thisElement) {
    final TreeElement treeNext = (TreeElement)thisElement.getTreeNext();
    return treeNext != null ? SourceTreeToPsiMap.treeElementToPsi(treeNext.getTransformedFirstOrSelf()) : null;
  }

  public static PsiElement getPrevSibling(ASTNode thisElement) {
    final TreeElement treePrev = (TreeElement)thisElement.getTreePrev();
    return treePrev != null ? SourceTreeToPsiMap.treeElementToPsi(treePrev.getTransformedLastOrSelf()) : null;
  }

  public static PsiFile getContainingFile(TreeElement thisElement) {
    TreeElement element;
    for (element = thisElement; element.getTreeParent() != null; element = element.getTreeParent()) {
    }

    if (element.getManager() == null) return null; // otherwise treeElementToPsi may crash!
    PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    if (psiElement instanceof DummyHolder) return psiElement.getContainingFile();
    if (!(psiElement instanceof PsiFile)) return null;
    return (PsiFile)psiElement;
  }

  public static boolean isValid(TreeElement thisElement) {
    LOG.assertTrue(thisElement instanceof PsiElement);
    PsiFile file = getContainingFile(thisElement);
    if (file == null) return false;
    return file.isValid();
  }

  public static boolean isWritable(ASTNode thisElement) {
    PsiFile file = (SourceTreeToPsiMap.treeElementToPsi(thisElement)).getContainingFile();
    return file != null ? file.isWritable() : true;
  }

  public static CharTable findCharTableByTree(ASTNode tree) {
    while (tree != null) {
      final CharTable userData = tree.getUserData(CharTable.CHAR_TABLE_KEY);
      if (userData != null) return userData;
      if (tree instanceof FileElement) return ((FileElement)tree).getCharTable();
      tree = tree.getTreeParent();
    }
    LOG.assertTrue(false, "Invalid root element");
    return null;
  }

  public static PsiElement addRange(PsiElement thisElement,
                                    PsiElement first,
                                    PsiElement last,
                                    ASTNode anchor,
                                    Boolean before) throws IncorrectOperationException {
    CheckUtil.checkWritable(thisElement);
    final CharTable table = findCharTableByTree(SourceTreeToPsiMap.psiElementToTree(thisElement));
    FileType fileType = thisElement.getContainingFile().getFileType();
    Project project = thisElement.getProject();
    Helper helper = new Helper(fileType, project);

    TreeElement copyFirst = null;
    ASTNode copyLast = null;
    ASTNode next = SourceTreeToPsiMap.psiElementToTree(last).getTreeNext();
    ASTNode parent = null;
    for (ASTNode element = SourceTreeToPsiMap.psiElementToTree(first); element != next; element = element.getTreeNext()) {
      TreeElement elementCopy = ChangeUtil.copyElement((TreeElement)element, table);
      if (element == first) {
        copyFirst = elementCopy;
      }
      if (element == last) {
        copyLast = elementCopy;
      }
      if (parent == null) {
        parent = elementCopy.getTreeParent();
      }
      else {
        parent.addChild(elementCopy, null);
        helper.normalizeIndent(elementCopy);
      }
    }
    if (copyFirst == null) return null;
    copyFirst = ((CompositeElement)SourceTreeToPsiMap.psiElementToTree(thisElement)).addInternal(copyFirst, copyLast, anchor, before);
    for (TreeElement element = copyFirst; element != null; element = element.getTreeNext()) {
      element = ChangeUtil.decodeInformation(element);
      if (element.getTreePrev() == null) {
        copyFirst = element;
      }
    }
    return SourceTreeToPsiMap.treeElementToPsi(copyFirst);
  }

  public static PsiType getType(PsiVariable variable) {
    PsiTypeElement typeElement = variable.getTypeElement();
    int arrayCount = 0;
    ASTNode name = SourceTreeToPsiMap.psiElementToTree(variable.getNameIdentifier());
    Loop:
    for (ASTNode child = name.getTreeNext(); child != null; child = child.getTreeNext()) {
      IElementType i = child.getElementType();
      if (i == ElementType.LBRACKET) {
        arrayCount++;
      }
      else if (i == ElementType.RBRACKET ||
        i == ElementType.WHITE_SPACE ||
        i == ElementType.C_STYLE_COMMENT ||
        i == JavaDocElementType.DOC_COMMENT ||
        i == JavaTokenType.DOC_COMMENT ||
        i == ElementType.END_OF_LINE_COMMENT) {
      }
      else {
        break Loop;
      }
    }
    PsiType type;
    if (!(typeElement instanceof PsiTypeElementImpl)) {
      type = typeElement.getType();
    }
    else {
      type = ((PsiTypeElementImpl)typeElement).getDetachedType(variable);
    }

    for (int i = 0; i < arrayCount; i++) {
      type = type.createArrayType();
    }
    return type;
  }

  public static void normalizeBrackets(PsiVariable variable) {
    CompositeElement variableElement = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(variable);
    ASTNode type = variableElement.findChildByRole(ChildRole.TYPE);
    LOG.assertTrue(type.getTreeParent() == variableElement);
    ASTNode name = variableElement.findChildByRole(ChildRole.NAME);

    ASTNode firstBracket = null;
    ASTNode lastBracket = null;
    int arrayCount = 0;
    ASTNode element = name;
    while (true) {
      element = TreeUtil.skipElements(element.getTreeNext(), ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (element == null || element.getElementType() != ElementType.LBRACKET) break;
      if (firstBracket == null) firstBracket = element;
      lastBracket = element;
      arrayCount++;

      element = TreeUtil.skipElements(element.getTreeNext(), ElementType.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (element == null || element.getElementType() != ElementType.RBRACKET) break;
      lastBracket = element;
    }

    if (firstBracket != null) {
      element = firstBracket;
      while (true) {
        ASTNode next = element.getTreeNext();
        variableElement.removeChild(element);
        if (element == lastBracket) break;
        element = next;
      }

      CompositeElement newType = (CompositeElement)type.clone();
      final CharTable treeCharTable = SharedImplUtil.findCharTableByTree(type);
      for (int i = 0; i < arrayCount; i++) {
        CompositeElement newType1 = Factory.createCompositeElement(ElementType.TYPE);
        TreeUtil.addChildren(newType1, newType);

        TreeUtil.addChildren(newType1, Factory.createLeafElement(ElementType.LBRACKET, new char[]{'['}, 0, 1, -1, treeCharTable));
        TreeUtil.addChildren(newType1, Factory.createLeafElement(ElementType.RBRACKET, new char[]{']'}, 0, 1, -1, treeCharTable));
        newType = newType1;
      }
      newType.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(type));
      variableElement.replaceChild(type, newType);
    }
  }

  public static PsiManager getManagerByTree(final ASTNode node) {
    if(node instanceof FileElement) return node.getPsi().getManager();
    return node.getTreeParent().getPsi().getManager();
  }
}
