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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrArrayDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

import java.util.*;

/**
 * @author ilyas
 */
public class GrNewExpressionImpl extends GrCallExpressionImpl implements GrNewExpression {
  public GrNewExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "NEW expression";
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitNewExpression(this);
  }

  public PsiType getType() {
    PsiType type = null;
    GrCodeReferenceElement refElement = getReferenceElement();
    if (refElement != null) {
      type = new GrClassReferenceType(refElement);
    } else {
      GrBuiltInTypeElement builtin = findChildByClass(GrBuiltInTypeElement.class);
      if (builtin != null) type = builtin.getType();
    }

    if (type != null) {
      for (int i = 0; i < getArrayCount(); i++) {
        type = type.createArrayType();
      }
      return type;
    }

    return null;
  }

  public GrCodeReferenceElement getReferenceElement() {
    return findChildByClass(GrCodeReferenceElement.class);
  }

  public GroovyResolveResult[] multiResolveConstructor() {
    GrCodeReferenceElement ref = getReferenceElement();
    if (ref == null) return null;

    final GroovyResolveResult[] classResults = ref.multiResolve(false);
    if (classResults.length == 0) return GroovyResolveResult.EMPTY_ARRAY;

    final PsiType[] argTypes = PsiUtil.getArgumentTypes(ref, true);
    List<GroovyResolveResult> constructorResults = new ArrayList<GroovyResolveResult>();
    for (GroovyResolveResult classResult : classResults) {
      final PsiElement element = classResult.getElement();
      if (element instanceof PsiClass) {
        final GroovyPsiElement context = classResult.getCurrentFileResolveContext();
        String className = ((PsiClass) element).getName();
        final MethodResolverProcessor processor = new MethodResolverProcessor(className, ref, false, true, argTypes, PsiType.EMPTY_ARRAY);
        processor.setCurrentFileResolveContext(context);
        final boolean toBreak = element.processDeclarations(processor, classResult.getSubstitutor(), null, ref);
        constructorResults.addAll(Arrays.asList(processor.getCandidates()));
        if (!toBreak) break;
      }
    }

    return constructorResults.toArray(new GroovyResolveResult[constructorResults.size()]);
  }

  public PsiMethod resolveConstructor() {
    return PsiImplUtil.extractUniqueElement(multiResolveConstructor());
  }

  public GroovyResolveResult resolveConstructorGenerics() {
    return PsiImplUtil.extractUniqueResult(multiResolveConstructor());
  }

  public int getArrayCount() {
    final GrArrayDeclaration arrayDeclaration = findChildByClass(GrArrayDeclaration.class);
    if (arrayDeclaration == null) return 0;
    return arrayDeclaration.getArrayCount();
  }

  @Nullable
  public PsiMethod resolveMethod() {
    return resolveConstructor();
  }

  @NotNull
  public GroovyResolveResult[] getMethodVariants() {
    final GrCodeReferenceElement referenceElement = getReferenceElement();
    if (referenceElement == null) return GroovyResolveResult.EMPTY_ARRAY;
    final GroovyResolveResult[] classResults = referenceElement.multiResolve(false);
    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
    final PsiResolveHelper helper = getManager().getResolveHelper();
    for (GroovyResolveResult classResult : classResults) {
      final PsiElement element = classResult.getElement();
      if (element instanceof PsiClass) {
        final PsiMethod[] constructors = ((PsiClass) element).getConstructors();
        for (PsiMethod constructor : constructors) {
          boolean isAccessible = helper.isAccessible(constructor, this, null);
          result.add(new GroovyResolveResultImpl(constructor, null, classResult.getSubstitutor(), isAccessible, true));
        }
      }
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }
}
