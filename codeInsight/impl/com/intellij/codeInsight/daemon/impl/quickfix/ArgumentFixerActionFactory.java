package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public abstract class ArgumentFixerActionFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.ArgumentFixerActionFactory");

  @Nullable
  protected abstract PsiExpression getModifiedArgument(PsiExpression expression, final PsiType toType) throws IncorrectOperationException;

  public void registerCastActions(CandidateInfo[] candidates, PsiCall call, HighlightInfo highlightInfo, final TextRange fixRange) {
    if (candidates.length == 0) return;
    List<CandidateInfo> methodCandidates = new ArrayList<CandidateInfo>(Arrays.asList(candidates));
    PsiExpressionList list = call.getArgumentList();
    PsiExpression[] expressions = list.getExpressions();
    if (expressions.length == 0) return;
    // filter out not castable candidates
    nextMethod:
    for (int i = methodCandidates.size() - 1; i >= 0; i--) {
      CandidateInfo candidate = methodCandidates.get(i);
      PsiMethod method = (PsiMethod) candidate.getElement();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      assert method != null;
      PsiParameter[] parameters = method.getParameterList().getParameters();
      if (expressions.length != parameters.length) {
        methodCandidates.remove(i);
        continue;
      }
      for (int j = 0; j < parameters.length; j++) {
        PsiParameter parameter = parameters[j];
        PsiExpression expression = expressions[j];
        // check if we can cast to this method
        PsiType exprType = expression.getType();
        PsiType parameterType = substitutor.substitute(parameter.getType());
        if (exprType == null
            || parameterType == null
            || !areTypesConvertible(exprType, parameterType)) {
          methodCandidates.remove(i);
          continue nextMethod;
        }
      }
    }

    if (methodCandidates.isEmpty()) return;

    try {
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression expression = expressions[i];
        PsiType exprType = expression.getType();
        Set<String> suggestedCasts = new THashSet<String>();
        // find to which type we can cast this param to get valid method call
        for (CandidateInfo candidate : methodCandidates) {
          PsiMethod method = (PsiMethod)candidate.getElement();
          PsiSubstitutor substitutor = candidate.getSubstitutor();
          assert method != null;
          PsiParameter[] parameters = method.getParameterList().getParameters();
          PsiType originalParameterType = parameters[i].getType();
          PsiType parameterType = substitutor.substitute(originalParameterType);
          if (parameterType instanceof PsiWildcardType) continue;
          if (!GenericsUtil.isFromExternalTypeLanguage(parameterType)) continue;
          if (suggestedCasts.contains(parameterType.getCanonicalText())) continue;
          // strict compare since even widening cast may help
          if (Comparing.equal(exprType, parameterType)) continue;
          PsiCall newCall = (PsiCall) call.copy();
          PsiExpression modifiedExpression = getModifiedArgument(expression, parameterType);
          if (modifiedExpression == null) continue;
          newCall.getArgumentList().getExpressions()[i].replace(modifiedExpression);
          JavaResolveResult resolveResult = newCall.resolveMethodGenerics();
          if (resolveResult.getElement() != null && resolveResult.isValidResult()) {
            suggestedCasts.add(parameterType.getCanonicalText());
            QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, createFix(list, i, parameterType),null);
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public abstract boolean areTypesConvertible(final PsiType exprType, final PsiType parameterType);

  public abstract MethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType parameterType);

}
