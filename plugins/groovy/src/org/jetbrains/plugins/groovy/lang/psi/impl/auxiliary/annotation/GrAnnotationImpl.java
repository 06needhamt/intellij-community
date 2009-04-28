/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.04.2007
 */
public class GrAnnotationImpl extends GroovyPsiElementImpl implements GrAnnotation {
  public GrAnnotationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  public String toString() {
    return "Annotation";
  }

  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    return findChildByClass(PsiAnnotationParameterList.class);
  }

  @Nullable
  @NonNls
  public String getQualifiedName() {
    final GrCodeReferenceElement nameRef = getClassReference();
    if (nameRef != null) {
      final PsiElement resolved = nameRef.resolve();
      if (resolved instanceof PsiClass) return ((PsiClass) resolved).getQualifiedName();
    }
    return null;
  }

  @Nullable
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return null;
  }

  @Nullable
  public PsiAnnotationMemberValue findAttributeValue(@NonNls String attributeName) {
    return null; //todo
  }

  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls String attributeName) {
    return null; //todo
  }

  @NotNull
  public <T extends PsiAnnotationMemberValue>  T setDeclaredAttributeValue(@NonNls String attributeName, T value) {
    throw new UnsupportedOperationException("Method setDeclaredAttributeValue is not yet implemented in " + getClass().getName());
  }

  @Nullable
  public PsiMetaData getMetaData() {
    return null;
  }

  public GrCodeReferenceElement getClassReference() {
    return findChildByClass(GrCodeReferenceElement.class);
  }

  @NotNull
  public String getName(){
    //Annotation is an identifier always
    return getClassReference().getText();
  }
}
