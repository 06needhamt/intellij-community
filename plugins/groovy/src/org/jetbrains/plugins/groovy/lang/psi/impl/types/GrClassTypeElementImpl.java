package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeOrPackageReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.04.2007
 */
public class GrClassTypeElementImpl extends GroovyPsiElementImpl implements GrClassTypeElement {
  public GrClassTypeElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Type element";
  }

  @NotNull
  public GrTypeOrPackageReferenceElement getReferenceElement() {
    return findChildByClass(GrTypeOrPackageReferenceElement.class);
  }

  @NotNull
  public PsiType getType() {
    return new GrClassReferenceType(getReferenceElement());
  }

}
