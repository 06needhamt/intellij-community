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

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.ResolveKind.*;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;

/**
 * @author ven
 */
public class ResolverProcessor implements PsiScopeProcessor, NameHint, ClassHint, ElementClassHint {
  protected String myName;
  private final EnumSet<ResolveKind> myResolveTargetKinds;
  private final Set<String> myProcessedClasses = new HashSet<String>();
  protected PsiElement myPlace;
  private
  @NotNull final PsiType[] myTypeArguments;

  protected Set<GroovyResolveResult> myCandidates = new LinkedHashSet<GroovyResolveResult>();

  public GroovyPsiElement getCurrentFileResolveContext() {
    return myCurrentFileResolveContext;
  }

  public void setCurrentFileResolveContext(GroovyPsiElement currentFileResolveContext) {
    myCurrentFileResolveContext = currentFileResolveContext;
  }

  protected GroovyPsiElement myCurrentFileResolveContext;

  protected ResolverProcessor(String name, EnumSet<ResolveKind> resolveTargets,
                              PsiElement place,
                              @NotNull PsiType[] typeArguments) {
    myName = name;
    myResolveTargetKinds = resolveTargets;
    myPlace = place;
    myTypeArguments = typeArguments;
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (myResolveTargetKinds.contains(getResolveKind(element))) {
      PsiNamedElement namedElement = (PsiNamedElement) element;
      PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
      if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;

      if (myTypeArguments.length > 0 && namedElement instanceof PsiClass) {
        substitutor = substitutor.putAll((PsiClass) namedElement, myTypeArguments);
      }

      if (namedElement instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)namedElement;
        final String fqn = aClass.getQualifiedName();
        if (!myProcessedClasses.contains(fqn)) {
          myProcessedClasses.add(fqn);
        } else {
          return true;
        }
      }

      boolean isAccessible = isAccessible(namedElement);
      boolean isStaticsOK = isStaticsOK(namedElement);
      myCandidates.add(new GroovyResolveResultImpl(namedElement, myCurrentFileResolveContext, substitutor, isAccessible, isStaticsOK));
      return !isAccessible;
    }

    return true;
  }

  protected boolean isAccessible(PsiNamedElement namedElement) {
    return !(namedElement instanceof PsiMember) ||
        org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isAccessible(myPlace, ((PsiMember) namedElement));
  }

  protected boolean isStaticsOK(PsiNamedElement element) {
    if (myCurrentFileResolveContext instanceof GrImportStatement) return true;

    if (element instanceof PsiModifierListOwner) {
      return org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isStaticsOK((PsiModifierListOwner) element, myPlace);
    }
    return true;
  }

  public GroovyResolveResult[] getCandidates() {
    return myCandidates.toArray(new GroovyResolveResult[myCandidates.size()]);
  }

  public <T> T getHint(Class<T> hintClass) {
    if (NameHint.class == hintClass && myName != null) {
      return (T) this;
    } else if (ClassHint.class == hintClass) {
      return (T) this;
    } else if (ElementClassHint.class == hintClass) {
      return (T) this;
    }

    return null;
  }

  public void handleEvent(Event event, Object associated) {
  }

  public String getName() {
    return myName;
  }

  public boolean shouldProcess(ResolveKind resolveKind) {
    return myResolveTargetKinds.contains(resolveKind);
  }

  public boolean shouldProcess(Class elementClass) {
    if (PsiMethod.class.isAssignableFrom(elementClass)) return shouldProcess(METHOD);
    if (PsiVariable.class.isAssignableFrom(elementClass)) return shouldProcess(PROPERTY);
    if (PsiPackage.class.isAssignableFrom(elementClass)) return shouldProcess(PACKAGE);
    if (PsiClass.class.isAssignableFrom(elementClass)) return shouldProcess(CLASS);
    return false;
  }

  public boolean hasCandidates() {
    return myCandidates.size() > 0;
  }

  private static ResolveKind getResolveKind(PsiElement element) {
    if (element instanceof PsiVariable) return PROPERTY;
    if (element instanceof GrReferenceExpression) return PROPERTY;

    else if (element instanceof PsiMethod) return METHOD;

    else if (element instanceof PsiPackage) return PACKAGE;

    return CLASS;
  }

  public String getName(ResolveState state) {
    //todo[DIANA] implement me!
    return myName;
  }
}
