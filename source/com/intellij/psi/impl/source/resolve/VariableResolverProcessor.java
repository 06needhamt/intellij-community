package com.intellij.psi.impl.source.resolve;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaVariableConflictResolver;
import com.intellij.psi.scope.processor.ConflictFilterProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.SmartList;

/**
 * @author ik, dsl
 */
public class VariableResolverProcessor extends ConflictFilterProcessor implements ElementClassHint {
  private static final ClassFilter ourFilter = new ClassFilter(PsiVariable.class);

  private boolean myStaticScopeFlag = false;
  private PsiClass myAccessClass = null;
  private PsiElement myCurrentFileContext = null;

  public VariableResolverProcessor(PsiJavaCodeReferenceElement place) {
    super(place.getText(), ourFilter, new PsiConflictResolver[]{new JavaVariableConflictResolver()}, new SmartList<CandidateInfo>(),
          place);

    PsiElement qualifier = place.getQualifier();
    PsiElement referenceName = place.getReferenceNameElement();

    if (referenceName instanceof PsiIdentifier){
      setName(referenceName.getText());
    }
    if (qualifier instanceof PsiExpression){
      final JavaResolveResult accessClass = PsiUtil.getAccessObjectClass((PsiExpression)qualifier);
      final PsiElement element = accessClass.getElement();
      if (element instanceof PsiTypeParameter) {
        final PsiManager manager = element.getManager();
        final PsiClassType type = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createType((PsiTypeParameter) element);
        final PsiType accessType = accessClass.getSubstitutor().substitute(type);
        if(accessType instanceof PsiArrayType) {
          LanguageLevel languageLevel = PsiUtil.getLanguageLevel(qualifier);
          myAccessClass = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().getArrayClass(languageLevel);
        }
        else if(accessType instanceof PsiClassType)
          myAccessClass = ((PsiClassType)accessType).resolve();
      }
      else if (element instanceof PsiClass)
        myAccessClass = (PsiClass) element;
    }
  }

  public final void handleEvent(Event event, Object associated) {
    super.handleEvent(event, associated);
    if(event == Event.START_STATIC){
      myStaticScopeFlag = true;
    }
    else if (Event.SET_CURRENT_FILE_CONTEXT.equals(event)) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  public void add(PsiElement element, PsiSubstitutor substitutor) {
    final boolean staticProblem = myStaticScopeFlag && !((PsiVariable)element).hasModifierProperty(PsiModifier.STATIC);
    super.add(new CandidateInfo(element, substitutor, myPlace, myAccessClass, staticProblem, myCurrentFileContext));
  }

  public boolean shouldProcess(Class elementClass) {
    return ReflectionCache.isAssignable(PsiVariable.class, elementClass);
  }

  public boolean execute(PsiElement element, ResolveState state) {
    if (!(element instanceof PsiField) && (myName == null || PsiUtil.checkName(element, myName, myPlace))) {
      super.execute(element, state);
      return myResults.isEmpty();
    }

    return super.execute(element, state);
  }
}
