/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 27.03.2007
 */
public class GrVariableDeclarationImpl extends GroovyPsiElementImpl implements GrVariableDeclaration {
  public GrVariableDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitVariableDeclaration(this);
  }

  public String toString() {
    return "Variable definitions";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrVariable variable : getVariables()) {
      if (lastParent == variable) break;
      if (!ResolveUtil.processElement(processor, variable)) return false;
    }

    return true;
  }

  @NotNull
  public GrModifierList getModifierListGroovy() {
    GrModifierList list = findChildByClass(GrModifierList.class);
    assert list != null;
    return list;
  }

  public GrVariable[] getVariables() {
    return findChildrenByClass(GrVariable.class);
  }

  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return findChildByClass(GrTypeElement.class);
  }

  public GrMember[] getMembers() {
    return findChildrenByClass(GrMember.class);
  }
}
