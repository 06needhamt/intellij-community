package com.intellij.psi.scope.processor;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.DuplicateConflictResolver;
import com.intellij.util.SmartList;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 31.01.2003
 * Time: 19:31:12
 * To change this template use Options | File Templates.
 */
public class MethodCandidatesProcessor extends MethodsProcessor{
  protected boolean myHasAccessibleStaticCorrectCandidate = false;

  protected MethodCandidatesProcessor(PsiElement place, PsiConflictResolver[] resolvers, SmartList<CandidateInfo> container){
    super(resolvers, container, place);
  }

  public MethodCandidatesProcessor(PsiElement place){
    super(new PsiConflictResolver[]{new DuplicateConflictResolver()}, new SmartList<CandidateInfo>(), place);
  }

  public void add(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod){
      final PsiMethod currentMethod = (PsiMethod)element;
      boolean staticProblem = isInStaticScope() && !currentMethod.hasModifierProperty(PsiModifier.STATIC);
      boolean isAccessible = JavaResolveUtil.isAccessible(currentMethod, currentMethod.getContainingClass(), currentMethod.getModifierList(),
                                                      myPlace, myAccessClass, myCurrentFileContext);
      myHasAccessibleStaticCorrectCandidate |= isAccessible && !staticProblem;

      if (isAccepted(currentMethod)){
        add(new MethodCandidateInfo(currentMethod, substitutor, !isAccessible, staticProblem, getArgumentList(), myCurrentFileContext, getTypeArguments()));
      }
    }
  }

  private boolean isAccepted(final PsiMethod candidate) {
    if (!isConstructor()) {
      return !candidate.isConstructor() && getName(ResolveState.initial()).equals(candidate.getName());
    } else {
      if (!candidate.isConstructor()) return false;
      if (myAccessClass == null) return true;
      if (myAccessClass instanceof PsiAnonymousClass) {
        return candidate.getContainingClass().equals(myAccessClass.getSuperClass());
      }
      return myAccessClass.equals(candidate.getContainingClass());
    }
  }

  public CandidateInfo[] getCandidates() {
    final JavaResolveResult[] resolveResult = getResult();
    CandidateInfo[] infos = new CandidateInfo[resolveResult.length];
    System.arraycopy(resolveResult, 0, infos, 0, resolveResult.length);
    return infos;
  }
}
