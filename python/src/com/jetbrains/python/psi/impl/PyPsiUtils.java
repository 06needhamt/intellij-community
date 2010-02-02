/*
 * @author max
 */
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyPsiUtils {
  public static final Key<Pair<PsiElement, TextRange>> SELECTION_BREAKS_AST_NODE =
    new Key<Pair<PsiElement, TextRange>>("python.selection.breaks.ast.node");
  private static final Logger LOG = Logger.getInstance(PyPsiUtils.class.getName());

  private PyPsiUtils() {
  }

  protected static <T extends PyElement> T[] nodesToPsi(ASTNode[] nodes, T[] array) {
    T[] psiElements = (T[])java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), nodes.length);
    for (int i = 0; i < nodes.length; i++) {
      //noinspection unchecked
      psiElements[i] = (T)nodes[i].getPsi();
    }
    return psiElements;
  }

  @Nullable
  protected static ASTNode getPrevComma(ASTNode after) {
    ASTNode node = after;
    PyElementType comma = PyTokenTypes.COMMA;
    do {
      node = node.getTreePrev();
    }
    while (node != null && !node.getElementType().equals(comma));
    return node;
  }

  @Nullable
  protected static ASTNode getNextComma(ASTNode after) {
    ASTNode node = after;
    PyElementType comma = PyTokenTypes.COMMA;
    do {
      node = node.getTreeNext();
    }
    while (node != null && !node.getElementType().equals(comma));
    return node;
  }

  public static PsiElement replaceExpression(@NotNull final Project project,
                                             @NotNull final PsiElement oldExpression,
                                             @NotNull final PsiElement newExpression) {
    final Pair<PsiElement, TextRange> data = oldExpression.getUserData(SELECTION_BREAKS_AST_NODE);
    if (data != null) {
      final PsiElement parent = data.first;
      final TextRange textRange = data.second;
      final String parentText = parent.getText();
      final String prefix = parentText.substring(0, textRange.getStartOffset());
      final String suffix = parentText.substring(textRange.getEndOffset(), parent.getTextLength());
      final PsiElement expression = PythonLanguage.getInstance().getElementGenerator()
        .createFromText(project, parent.getClass(), prefix + newExpression.getText() + suffix);
      return parent.replace(expression);
    }
    else {
      return oldExpression.replace(newExpression);
    }
  }

  public static void addToEnd(@NotNull final PsiElement psiElement, @NotNull final PsiElement... newElements) {
    final ASTNode psiNode = psiElement.getNode();
    LOG.assertTrue(psiNode != null);
    for (PsiElement newElement : newElements) {
      //noinspection ConstantConditions
      psiNode.addChild(newElement.getNode());
    }
  }

  public static void addBeforeInParent(@NotNull final PsiElement anchor, @NotNull final PsiElement... newElements) {
    final ASTNode anchorNode = anchor.getNode();
    LOG.assertTrue(anchorNode != null);
    for (PsiElement newElement : newElements) {
      anchorNode.getTreeParent().addChild(newElement.getNode(), anchorNode);
    }
  }

  public static void removeElements(@NotNull final PsiElement... elements) {
    final ASTNode parentNode = elements[0].getParent().getNode();
    LOG.assertTrue(parentNode != null);
    for (PsiElement element : elements) {
      //noinspection ConstantConditions
      parentNode.removeChild(element.getNode());
    }
  }

  @Nullable
  public static PsiElement getStatement(@NotNull final PsiElement element) {
    final PyElement compStatement = getCompoundStatement(element);
    if (compStatement == null){
      return null;
    }
    return getStatement(compStatement, element);
  }

  @Nullable
  public static PyElement getCompoundStatement(final PsiElement element) {
    return element instanceof PyFile || element instanceof PyStatementList
           ? (PyElement) element
           : PsiTreeUtil.getParentOfType(element, PyFile.class, PyStatementList.class);
  }

  @Nullable
  public static PsiElement getStatement(final PsiElement compStatement, PsiElement element) {
    PsiElement parent = element.getParent();
    while (parent != null && parent != compStatement){
      element = parent;
      parent = element.getParent();
    }
    return parent != null ? element : null;
  }

  public static List<PsiElement> collectElements(final PsiElement statement1, final PsiElement statement2) {
    // Process ASTNodes here to handle all the nodes
    final ASTNode node1 = statement1.getNode();
    final ASTNode node2 = statement2.getNode();
    final ASTNode parentNode = node1.getTreeParent();

    boolean insideRange = false;
    final List<PsiElement> result = new ArrayList<PsiElement>();
    for (ASTNode node : parentNode.getChildren(null)) {
      // start
      if (node1 == node){
        insideRange = true;
      }
      if (insideRange){
        result.add(node.getPsi());
      }
      // stop
      if (node == node2){
        insideRange = false;
        break;
      }
    }
    return result;
  }
}