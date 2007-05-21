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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.ResolveKind;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;

import java.util.*;

/**
 * @author ilyas
 */
public class GrReferenceExpressionImpl extends GrReferenceElementImpl implements GrReferenceExpression {
  public GrReferenceExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public int getTextOffset() {
    PsiElement parent = getParent();
    TextRange range = getTextRange();
    if (!(parent instanceof GrAssignmentExpression) || !this.equals(((GrAssignmentExpression) parent).getLValue())) {
      return range.getEndOffset(); //need this as a hack against TargetElementUtil
    }

    return range.getStartOffset();
  }

  public String toString() {
    return "Reference expression";
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] results = ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  private static final MyResolver RESOLVER = new MyResolver();

  public PsiType getType() {

    PsiElement resolved = resolve();
    PsiType result = null;
    if (resolved instanceof PsiClass) {
      result = getManager().getElementFactory().createType((PsiClass) resolved);
    } else if (resolved instanceof PsiVariable) {
      result = ((PsiVariable) resolved).getType();
    } else if (resolved instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) resolved;
      if (PropertyUtil.isSimplePropertySetter(method)) {
        result = method.getParameterList().getParameters()[0].getType();
      } else {
        result = method.getReturnType();
      }
    } else if (resolved instanceof GrReferenceExpression) {
      PsiElement parent = resolved.getParent();
      if (parent instanceof GrAssignmentExpression) {
        GrAssignmentExpression assignment = (GrAssignmentExpression) parent;
        if (resolved.equals(assignment.getLValue())) {
          GrExpression rValue = assignment.getRValue();
          if (rValue != null) {
            PsiType rType = rValue.getType();
            if (rType != null) result = rType;
          }
        }
      }
    }

    return PsiUtil.boxPrimitiveType(result, getManager(), getResolveScope());
  }

  public String getName() {
    return getReferenceName();
  }

  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  private static class MyResolver implements ResolveCache.PolyVariantResolver<GrReferenceExpressionImpl> {
    public GroovyResolveResult[] resolve(GrReferenceExpressionImpl refExpr, boolean incompleteCode) {
      GrExpression qualifier = refExpr.getQualifierExpression();
      String name = refExpr.getReferenceName();
      if (name == null) return null;
      ResolverProcessor processor = getResolveProcessor(refExpr, name, false);

      resolveImpl(refExpr, qualifier, processor);

      GroovyResolveResult[] propertyCandidates = processor.getCandidates();
      if (propertyCandidates.length > 0) return propertyCandidates;
      if (refExpr.getKind() == Kind.TYPE_OR_PROPERTY) {
        ResolverProcessor classProcessor = new ResolverProcessor(refExpr.getReferenceName(), EnumSet.of(ResolveKind.CLASS), refExpr, false);
        resolveImpl(refExpr, qualifier, classProcessor);
        return classProcessor.getCandidates();
      }

      return GroovyResolveResult.EMPTY_ARRAY;
    }

    private void resolveImpl(GrReferenceExpressionImpl refExpr, GrExpression qualifier, ResolverProcessor processor) {
      if (qualifier == null) {
        ResolveUtil.treeWalkUp(refExpr, processor);
      } else {
        PsiType qualifierType = qualifier.getType();
        if (qualifierType instanceof PsiClassType) {
          PsiClass qualifierClass = ((PsiClassType) qualifierType).resolve();
          if (qualifierClass != null) {
            qualifierClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, refExpr);
            if (!(qualifierClass instanceof GrTypeDefinition)) {
              addDefaultMethods(qualifierClass, processor, new HashSet<PsiClass>());
            }
          }
        }
      }
    }
  }

  private static ResolverProcessor getResolveProcessor(GrReferenceExpressionImpl refExpr, String name, boolean forCompletion) {
    Kind kind = refExpr.getKind();
    return getMethodOrPropertyResolveProcessor(refExpr, name, forCompletion, kind);
  }

  private static ResolverProcessor getMethodOrPropertyResolveProcessor(GrReferenceExpressionImpl refExpr, String name, boolean forCompletion, Kind kind) {
    ResolverProcessor processor;
    if (kind == Kind.METHOD_OR_PROPERTY) {
      processor = new MethodResolverProcessor(name, refExpr, forCompletion);
    } else {
      processor = new PropertyResolverProcessor(name, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), refExpr, forCompletion);
    }

    return processor;
  }

  private enum Kind {
    PROPERTY,
    TYPE_OR_PROPERTY,
    METHOD_OR_PROPERTY
  }

  private Kind getKind() {
    PsiElement parent = getParent();
    if (parent instanceof GrMethodCall || parent instanceof GrApplicationExpression) {
      return Kind.METHOD_OR_PROPERTY;
    } else if (parent instanceof GrStatement || parent instanceof GrCodeBlock) {
      return Kind.TYPE_OR_PROPERTY;
    }

    return Kind.PROPERTY;
  }

  public String getCanonicalText() {
    return ""; //todo
  }

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("NIY"); //todo
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiNamedElement && Comparing.equal(((PsiNamedElement) element).getName(), getReferenceName())) {
      return element.equals(resolve());
    }
    return false;
  }

  public Object[] getVariants() {

    Object[] propertyVariants = getVariantsImpl(getResolveProcessor(this, null, true));
    if (getKind() == Kind.TYPE_OR_PROPERTY) {
      ResolverProcessor classVariantsCollector = new ResolverProcessor(null, EnumSet.of(ResolveKind.CLASS), this, true);
      GroovyResolveResult[] classVariants = classVariantsCollector.getCandidates();
      return ArrayUtil.mergeArrays(propertyVariants, classVariants, Object.class);
    }

    return propertyVariants;
  }

  private Object[] getVariantsImpl(ResolverProcessor processor) {
    GrExpression qualifierExpression = getQualifierExpression();
    if (qualifierExpression == null) {
      ResolveUtil.treeWalkUp(this, processor);
    } else {
      PsiType qualifierType = qualifierExpression.getType();
      if (qualifierType instanceof PsiClassType) {
        PsiClass qualifierClass = ((PsiClassType) qualifierType).resolve();
        if (qualifierClass != null) {
          qualifierClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, this);
          if (!(qualifierClass instanceof GrTypeDefinition)) {
            addDefaultMethods(qualifierClass, processor, new HashSet<PsiClass>());
          }
        }
      }
    }

    GroovyResolveResult[] candidates = processor.getCandidates();
    if (candidates.length == 0) return PsiNamedElement.EMPTY_ARRAY;
    return ResolveUtil.mapToElements(candidates);
  }

  private static void addDefaultMethods(PsiClass clazz, ResolverProcessor processor, Set<PsiClass> visited) {
    if (visited.contains(clazz)) return;
    visited.add(clazz);

    String qName = clazz.getQualifiedName();
    if (qName != null) {
      List<PsiMethod> defaultMethods = GroovyPsiManager.getInstance(clazz.getProject()).getDefaultMethods(qName);
      for (PsiMethod defaultMethod : defaultMethods) {
        if (!ResolveUtil.processElement(processor, defaultMethod)) return;
      }
      for (PsiClass aSuper : clazz.getSupers()) {
        addDefaultMethods(aSuper, processor, new HashSet<PsiClass>());
      }
    }
  }

  public boolean isSoft() {
    return getQualifierExpression() != null;  //todo rethink
  }

  public GrExpression getQualifierExpression() {
    return findChildByClass(GrExpression.class);
  }

  public GroovyResolveResult advancedResolve() {
    ResolveResult[] results = ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
    return results.length == 1 ? (GroovyResolveResult) results[0] : GroovyResolveResult.EMPTY_RESULT;
  }

  @NotNull
  public ResolveResult[] multiResolve(boolean b) {
    return ((PsiManagerEx) getManager()).getResolveCache().resolveWithCaching(this, RESOLVER, false, false);
  }

}