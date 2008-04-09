package com.intellij.psi.scope.processor;

import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.util.SmartList;

/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Dec 12, 2002
 * Time: 8:24:29 PM
 * To change this template use Options | File Templates.
 */
public abstract class MethodsProcessor extends ConflictFilterProcessor {
  private static final ClassFilter ourFilter = new ClassFilter(PsiMethod.class);

  private boolean myStaticScopeFlag = false;
  private boolean myIsConstructor = false;
  protected PsiElement myCurrentFileContext = null;
  protected PsiClass myAccessClass = null;
  private PsiExpressionList myArgumentList;
  private PsiType[] myTypeArguments;

  public MethodsProcessor(PsiConflictResolver[] resolvers, SmartList<CandidateInfo> container, final PsiElement place) {
    super(null, ourFilter, resolvers, container, place);
  }

  public PsiExpressionList getArgumentList() {
    return myArgumentList;
  }

  public void setArgumentList(PsiExpressionList argList) {
    myArgumentList = argList;
  }

  public void obtainTypeArguments(PsiCallExpression callExpression) {
    final PsiType[] typeArguments = callExpression.getTypeArguments();
    if (typeArguments.length > 0) {
      setTypeArguments(typeArguments);
    }
  }

  protected void setTypeArguments(PsiType[] typeParameters) {
    myTypeArguments = typeParameters;
  }

  public PsiType[] getTypeArguments() {
    return myTypeArguments;
  }

  public boolean isInStaticScope() {
    return myStaticScopeFlag;
  }

  public void handleEvent(Event event, Object associated) {
    if (event == JavaScopeProcessorEvent.START_STATIC) {
      myStaticScopeFlag = true;
    }
    else if (JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT.equals(event)) {
      myCurrentFileContext = (PsiElement)associated;
    }
  }

  public void setAccessClass(PsiClass accessClass) {
/*    if (isConstructor() && accessClass instanceof PsiAnonymousClass) {
      myAccessClass = ((PsiAnonymousClass)accessClass).getBaseClassType().resolve();
    }
    else*/ {
      myAccessClass = accessClass;
    }
    /*if (isConstructor() && myAccessClass != null) {
      setName(myAccessClass.getName());
    }*/
  }

  public boolean isConstructor() {
    return myIsConstructor;
  }

  public void setIsConstructor(boolean myIsConstructor) {
    this.myIsConstructor = myIsConstructor;
  }

  public void forceAddResult(PsiMethod method) {
    add(new CandidateInfo(method, PsiSubstitutor.EMPTY, false, false, myCurrentFileContext));
  }
}
