package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.Nullable;

public class ClassElement extends RepositoryTreeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ClassElement");

  public ClassElement(IElementType type) {
    super(type);
  }

  public int getTextOffset() {
    ASTNode name = findChildByRole(ChildRole.NAME);
    if (name != null) {
      return name.getStartOffset();
    }
    else {
      return super.getTextOffset();
    }
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    ChameleonTransforming.transformChildren(this);

    PsiClass psiClass = (PsiClass)SourceTreeToPsiMap.treeElementToPsi(this);
    if (anchor == null) {
      if (first.getElementType() != JavaDocElementType.DOC_COMMENT &&
          first.getElementType() != JavaTokenType.DOC_COMMENT) {
        if (before == null) {
          if (first == last) {
            PsiElement firstPsi = SourceTreeToPsiMap.treeElementToPsi(first);
            PsiElement psiElement = firstPsi instanceof PsiMember ? CodeEditUtil.getDefaultAnchor(psiClass, (PsiMember)firstPsi) : null;
            anchor = psiElement != null ? SourceTreeToPsiMap.psiElementToTree(psiElement) : null;
          }
          else {
            anchor = findChildByRole(ChildRole.RBRACE);
          }
          before = Boolean.TRUE;
        }
        else if (!before.booleanValue()) {
          anchor = findChildByRole(ChildRole.LBRACE);
        }
        else {
          anchor = findChildByRole(ChildRole.RBRACE);
        }
      }
    }

    if (isEnum()) {
      if (!ENUM_CONSTANT_LIST_ELEMENTS_BIT_SET.isInSet(first.getElementType())) {
        ASTNode semicolonPlace = findEnumConstantListDelimiterPlace();
        if (semicolonPlace == null || semicolonPlace.getElementType() != SEMICOLON) {
            final LeafElement semicolon = Factory.createSingleLeafElement(SEMICOLON, new char[]{';'}, 0, 1,
                                                                          SharedImplUtil.findCharTableByTree(this), getManager());
            addInternal(semicolon, semicolon, semicolonPlace, Boolean.FALSE);
            semicolonPlace = semicolon;
        }
        for (ASTNode run = anchor; run != null; run = run.getTreeNext()) {
          if (run == semicolonPlace) {
            anchor = before.booleanValue() ? semicolonPlace.getTreeNext() : semicolonPlace;
            break;
          }
        }
      }
    }

    ASTNode afterLast = last.getTreeNext();
    ASTNode next;
    for (ASTNode child = first; child != afterLast; child = next) {
      next = child.getTreeNext();
      if (child.getElementType() == ElementType.METHOD && ((PsiMethod)SourceTreeToPsiMap.treeElementToPsi(child)).isConstructor()) {
        ASTNode oldIdentifier = ((CompositeElement)child).findChildByRole(ChildRole.NAME);
        ASTNode newIdentifier = (ASTNode)findChildByRole(ChildRole.NAME).clone();
        newIdentifier.putUserData(CharTable.CHAR_TABLE_KEY, SharedImplUtil.findCharTableByTree(this));
        child.replaceChild(oldIdentifier, newIdentifier);
      }
    }

    if (psiClass.isEnum()) {
      for (ASTNode child = first; child != afterLast; child = next) {
        next = child.getTreeNext();
        if ((child.getElementType() == ElementType.METHOD && ((PsiMethod)SourceTreeToPsiMap.treeElementToPsi(child)).isConstructor()) ||
            child.getElementType() == ElementType.ENUM_CONSTANT) {
          CompositeElement modifierList = (CompositeElement)((CompositeElement)child).findChildByRole(ChildRole.MODIFIER_LIST);
          while (true) {
            ASTNode modifier = TreeUtil.findChild(modifierList, MODIFIERS_TO_REMOVE_IN_ENUM_BIT_SET);
            if (modifier == null) break;
            modifierList.deleteChildInternal(modifier);
          }
        }
      }
    }
    else if (psiClass.isInterface()) {
      for (ASTNode child = first; child != afterLast; child = next) {
        next = child.getTreeNext();
        if (child.getElementType() == ElementType.METHOD || child.getElementType() == ElementType.FIELD) {
          CompositeElement modifierList = (CompositeElement)((CompositeElement)child).findChildByRole(ChildRole.MODIFIER_LIST);
          while (true) {
            ASTNode modifier = TreeUtil.findChild(modifierList, MODIFIERS_TO_REMOVE_IN_INTERFACE_BIT_SET);
            if (modifier == null) break;
            modifierList.deleteChildInternal(modifier);
          }
        }
      }
    }

    return super.addInternal(first, last, anchor, before);
  }

  public void deleteChildInternal(ASTNode child) {
    if (isEnum()) {
      if (child.getElementType() == ENUM_CONSTANT) {
        ASTNode next = TreeUtil.skipElements(child.getTreeNext(), WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (next != null && next.getElementType() == COMMA) {
          deleteChildInternal(next);
        }
        else {
          ASTNode prev = TreeUtil.skipElementsBack(child.getTreePrev(), WHITE_SPACE_OR_COMMENT_BIT_SET);
          if (prev != null && prev.getElementType() == COMMA) {
            deleteChildInternal(prev);
          }
        }
      }
    }

    super.deleteChildInternal(child);
  }

  public boolean isEnum() {
    final ASTNode keyword = findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    return keyword != null && keyword.getElementType() == ENUM_KEYWORD;
  }

  public boolean isAnnotationType() {
    return findChildByRole(ChildRole.AT) != null;
  }

  private static final TokenSet MODIFIERS_TO_REMOVE_IN_INTERFACE_BIT_SET = TokenSet.create(
    PUBLIC_KEYWORD, ABSTRACT_KEYWORD,
    STATIC_KEYWORD, FINAL_KEYWORD,
    NATIVE_KEYWORD
  );

  private static final TokenSet MODIFIERS_TO_REMOVE_IN_ENUM_BIT_SET = TokenSet.create(
    PUBLIC_KEYWORD, FINAL_KEYWORD
  );

  private static final TokenSet ENUM_CONSTANT_LIST_ELEMENTS_BIT_SET = TokenSet.create(
    ENUM_CONSTANT, COMMA, SEMICOLON
  );


  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.DOC_COMMENT:
        if (getFirstChildNode().getElementType() == JavaDocElementType.DOC_COMMENT) {
          return getFirstChildNode();
        }
        else {
          return null;
        }

      case ChildRole.ENUM_CONSTANT_LIST_DELIMITER:
        if (!isEnum()) {
          return null;
        }
        return findEnumConstantListDelimiter();

      case ChildRole.MODIFIER_LIST:
        return TreeUtil.findChild(this, MODIFIER_LIST);

      case ChildRole.EXTENDS_LIST:
        if (isAnnotationType() || isEnum()) return null;
        return TreeUtil.findChild(this, EXTENDS_LIST);

      case ChildRole.IMPLEMENTS_LIST:
        return TreeUtil.findChild(this, IMPLEMENTS_LIST);

      case ChildRole.TYPE_PARAMETER_LIST:
        return TreeUtil.findChild(this, TYPE_PARAMETER_LIST);

      case ChildRole.CLASS_OR_INTERFACE_KEYWORD:
        for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
          if (CLASS_KEYWORD_BIT_SET.isInSet(child.getElementType())) return child;
        }
        LOG.assertTrue(false);
        return null;

      case ChildRole.NAME:
        return TreeUtil.findChild(this, IDENTIFIER);

      case ChildRole.LBRACE:
        return TreeUtil.findChild(this, LBRACE);

      case ChildRole.RBRACE:
        return TreeUtil.findChildBackward(this, RBRACE);

      case ChildRole.AT:
        ASTNode modifierList = findChildByRole(ChildRole.MODIFIER_LIST);
        if (modifierList != null) {
          ASTNode treeNext = modifierList.getTreeNext();
          if (treeNext != null) {
            treeNext = TreeUtil.skipElements(treeNext, WHITE_SPACE_OR_COMMENT_BIT_SET);
            if (treeNext.getElementType() == AT) return treeNext;
          }
        }
        return null;
    }
  }

  private ASTNode findEnumConstantListDelimiter() {
    ASTNode candidate = findEnumConstantListDelimiterPlace();
    return candidate != null && candidate.getElementType() == SEMICOLON ? candidate : null;
  }

  @Nullable
  public ASTNode findEnumConstantListDelimiterPlace() {
    final ASTNode first = findChildByRole(ChildRole.LBRACE);
    if (first == null) return null;
    for (ASTNode child = first.getTreeNext(); child != null; child = child.getTreeNext()) {
      final IElementType childType = child.getElementType();
      if (WHITE_SPACE_OR_COMMENT_BIT_SET.isInSet(childType) ||
          childType == ERROR_ELEMENT || childType == ENUM_CONSTANT) {
        continue;
      }
      else if (childType == COMMA) {
        continue;
      }
      else if (childType == SEMICOLON) {
        return child;
      }
      else {
        return TreeUtil.skipElementsBack(child.getTreePrev(), WHITE_SPACE_OR_COMMENT_BIT_SET);
      }
    }

    return null;
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == SEMICOLON) {
      if (!isEnum()) return ChildRole.NONE;
      if (child == findEnumConstantListDelimiter()) {
        return ChildRole.ENUM_CONSTANT_LIST_DELIMITER;
      }
      else {
        return ChildRole.NONE;
      }
    }
    else if (i == CLASS) {
      return ChildRole.CLASS;
    }
    else if (i == FIELD) {
      return ChildRole.FIELD;
    }
    else if (i == METHOD || i == ANNOTATION_METHOD) {
      return ChildRole.METHOD;
    }
    else if (i == CLASS_INITIALIZER) {
      return ChildRole.CLASS_INITIALIZER;
    }
    else if (i == TYPE_PARAMETER_LIST) {
      return ChildRole.TYPE_PARAMETER_LIST;
    }
    else if (i == JavaDocElementType.DOC_COMMENT) {
      return getChildRole(child, ChildRole.DOC_COMMENT);
    }
    else if (i == C_STYLE_COMMENT || i == END_OF_LINE_COMMENT) {
      return ChildRole.NONE;
    }
    else if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == EXTENDS_LIST) {
      return ChildRole.EXTENDS_LIST;
    }
    else if (i == IMPLEMENTS_LIST) {
      return ChildRole.IMPLEMENTS_LIST;
    }
    else if (i == CLASS_KEYWORD || i == INTERFACE_KEYWORD || i == ENUM_KEYWORD) {
      return getChildRole(child, ChildRole.CLASS_OR_INTERFACE_KEYWORD);
    }
    else if (i == IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == LBRACE) {
      return getChildRole(child, ChildRole.LBRACE);
    }
    else if (i == RBRACE) {
      return getChildRole(child, ChildRole.RBRACE);
    }
    else if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == AT) {
      return ChildRole.AT;
    }
    else {
      return ChildRole.NONE;
    }
  }
}
