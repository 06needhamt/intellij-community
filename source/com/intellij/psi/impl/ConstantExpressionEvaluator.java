package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentSoftHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public class ConstantExpressionEvaluator extends JavaRecursiveElementWalkingVisitor {
  private final boolean myThrowExceptionOnOverflow;
  private final Project myProject;

  private static final Key<CachedValue<ConcurrentMap<PsiElement,Object>>> CONSTANT_VALUE_WO_OVERFLOW_MAP_KEY = Key.create("CONSTANT_VALUE_WO_OVERFLOW_MAP_KEY");
  private static final Key<CachedValue<ConcurrentMap<PsiElement,Object>>> CONSTANT_VALUE_WITH_OVERFLOW_MAP_KEY = Key.create("CONSTANT_VALUE_WITH_OVERFLOW_MAP_KEY");
  private static final Object NO_VALUE = new Object();
  private final ConstantExpressionVisitor myConstantExpressionVisitor;

  private ConstantExpressionEvaluator(Set<PsiVariable> visitedVars, boolean throwExceptionOnOverflow, final Project project) {
    myThrowExceptionOnOverflow = throwExceptionOnOverflow;
    myProject = project;
    myConstantExpressionVisitor = new ConstantExpressionVisitor(visitedVars, throwExceptionOnOverflow);
  }

  @Override
  protected void elementFinished(PsiElement element) {
    Object value = getCached(element);
    if (value == null) {
      Object result = myConstantExpressionVisitor.handle(element);
      cache(element, result);
    }
  }

  @Override
  public void visitElement(PsiElement element) {
    Object value = getCached(element);
    if (value == null) {
      super.visitElement(element);
      // will cache back in elementFinished()
    }
    else {
      ConstantExpressionVisitor.store(element, value == NO_VALUE ? null : value);
    }
  }

  private static final CachedValueProvider<ConcurrentMap<PsiElement,Object>> PROVIDER = new CachedValueProvider<ConcurrentMap<PsiElement,Object>>() {
    public Result<ConcurrentMap<PsiElement,Object>> compute() {
      ConcurrentMap<PsiElement, Object> value = new ConcurrentSoftHashMap<PsiElement, Object>();
      return Result.create(value, PsiModificationTracker.MODIFICATION_COUNT);
    }
  };

  private Object getCached(@NotNull PsiElement element) {
    return map().get(element);
  }
  private Object cache(@NotNull PsiElement element, @Nullable Object value) {
    value = ConcurrencyUtil.cacheOrGet(map(), element, value == null ? NO_VALUE : value);
    if (value == NO_VALUE) {
      value = null;
    }
    return value;
  }

  @NotNull
  private ConcurrentMap<PsiElement, Object> map() {
    Key<CachedValue<ConcurrentMap<PsiElement, Object>>> key = myThrowExceptionOnOverflow ? CONSTANT_VALUE_WITH_OVERFLOW_MAP_KEY : CONSTANT_VALUE_WO_OVERFLOW_MAP_KEY;
    return PsiManager.getInstance(myProject).getCachedValuesManager().getCachedValue(myProject, key, PROVIDER, false);
  }

  public static Object computeConstantExpression(PsiExpression expression, @Nullable Set<PsiVariable> visitedVars, boolean throwExceptionOnOverflow) {
    if (expression == null) return null;

    ConstantExpressionEvaluator evaluator = new ConstantExpressionEvaluator(visitedVars, throwExceptionOnOverflow, expression.getProject());
    expression.accept(evaluator);
    Object cached = evaluator.getCached(expression);
    return cached == NO_VALUE ? null : cached;
  }
  
  public static Object computeConstantExpression(PsiExpression expression, boolean throwExceptionOnOverflow) {
    return computeConstantExpression(expression, null, throwExceptionOnOverflow);
  }
}
