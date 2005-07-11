/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
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

package com.intellij.lang;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A node in the AST tree. The AST is an intermediate parsing tree created by {@link PsiBuilder},
 * out of which a PSI tree is then created.
 * @author max
 * @see PsiElement
 */

public interface ASTNode extends UserDataHolder {
  ASTNode[] EMPTY_ARRAY = new ASTNode[0];

  /**
   * Returns the type of this node.
   * @return the element type.
   */
  IElementType getElementType();

  /**
   * Returns the text of this node.
   * @return the node text.
   */
  String getText();

  /**
   * Checks if the specified character is present in the text of this node.
   * @param c the character to search for.
   * @return true if the character is found, false otherwise.
   */
  boolean textContains(char c);

  /**
   * Returns the starting offset of the node text in the document.
   * @return the start offset.
   */
  int getStartOffset();

  /**
   * Returns the length of the node text.
   * @return the text length.
   */
  int getTextLength();

  /**
   * Returns the text range (a combination of starting offset in the document and length) for this node.
   * @return the text range.
   */
  TextRange getTextRange();

  /**
   * Returns the parent of this node in the tree.
   * @return the parent node.
   */
  ASTNode getTreeParent();

  /**
   * Returns the first child of this node in the tree.
   * @return the first child node.
   */
  @Nullable
  ASTNode getFirstChildNode();

  /**
   * Returns the last child of this node in the tree.
   * @return the last child node.
   */
  @Nullable
  ASTNode getLastChildNode();

  /**
   * Returns the previous sibling of this node in the tree.
   * @return the previous sibling node.
   */
  @Nullable
  ASTNode getTreeNext();

  /**
   * Returns the next sibling of this node in the tree.
   * @return the next sibling node.
   */
  @Nullable
  ASTNode getTreePrev();

  /**
   * Returns the list of children of the specified node, optionally filtered by the
   * specified token type filter.
   * @param filter the token set used to filter the returned children, or null if
   * all children should be returned.
   * @return the children array.
   */
  ASTNode[] getChildren(@Nullable TokenSet filter);

  /**
   * Adds the specified child node as the last child of this node.
   * @param child the child node to add.
   */
  void addChild(@NotNull ASTNode child);

  /**
   * Adds the specified child node at the specified position in the child list.
   * @param child the child node to add.
   * @param anchorBefore the node before which the child node is inserted.
   */
  void addChild(@NotNull ASTNode child, ASTNode anchorBefore);

  /**
   * Removes the specified node from the list of children of this node.
   * @param child the child node to remove.
   */
  void removeChild(@NotNull ASTNode child);

  /**
   * Removes a range of nodes from the list of children, starting with <code>firstNodeToRemove</code>,
   * up to and not including <code>firstNodeToKeep</code>.
   * @param firstNodeToRemove the first child node to remove from the tree.
   * @param firstNodeToKeep the first child node to keep in the tree.
   */
  void removeRange(@NotNull ASTNode firstNodeToRemove, ASTNode firstNodeToKeep);

  /**
   * Replaces the specified child node with another node.
   * @param oldChild the child node to replace.
   * @param newChild the node to replace with.
   */
  void replaceChild(@NotNull ASTNode oldChild, @NotNull ASTNode newChild);

  /**
   * Replaces all child nodes with the children of the specified node.
   * @param anotherParent the parent node whose children are used for replacement.
   */
  void replaceAllChildrenToChildrenOf(ASTNode anotherParent);

  /**
   * Adds a range of nodes belonging to the same parent to the list of children of this node.
   * @param firstChild the first node to add.
   * @param lastChild the last node to add.
   * @param anchorBefore the node before which the child nodes are inserted.
   */
  void addChildren(ASTNode firstChild, ASTNode lastChild, ASTNode anchorBefore);

  /**
   * Creates and returns a deep copy of the AST tree part starting at this node.
   * @return the top node of the copied tree (as an ASTNode object)
   */
  Object clone();

  /**
   * Creates a copy of the entire AST tree containing this node and returns a counterpart
   * of this node in the resulting tree.
   * @return the counterpart of this node in the copied tree.
   */
  ASTNode copyElement();

  /**
   * Finds a leaf child node at the specified offset from the start of the text range of this node.
   * @param offset the relative offset for which the child node is requested.
   * @return the child node, or null if none is found.
   */
  @Nullable
  ASTNode findLeafElementAt(int offset);

  /**
   * Returns a copyable user data object attached to this node.
   * @param key the key for accessing the user data object.
   * @return the user data object, or null if no such object is found in the current node.
   * @see #putCopyableUserData(com.intellij.openapi.util.Key, Object)
   */
  @Nullable
  <T> T getCopyableUserData(Key<T> key);

  /**
   * Attaches a copyable user data object to this node. Copyable user data objects are copied
   * when the AST tree nodes are copied.
   * @param key the key for accessing the user data object.
   * @see #getCopyableUserData(com.intellij.openapi.util.Key)
   */
  <T> void putCopyableUserData(Key<T> key, T value);

  /**
   * Returns the first child of the specified node which has the specified type.
   * @param type the type of the node to return.
   * @return the found node, or null if none was found.
   */
  @Nullable
  ASTNode findChildByType(IElementType type);

  /**
   * Returns the PSI element for this node.
   * @return the PSI element.
   */
  PsiElement getPsi();
}
