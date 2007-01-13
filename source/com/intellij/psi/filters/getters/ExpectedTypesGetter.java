package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.*;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 09.04.2003
 * Time: 12:37:26
 * To change this template use Options | File Templates.
 */
public class ExpectedTypesGetter implements ContextGetter{
  public Object[] get(PsiElement context, CompletionContext completionContext){
    ExpectedTypesProvider typesProvider = ExpectedTypesProvider.getInstance(context.getProject());
    PsiExpression expression = PsiTreeUtil.getContextOfType(context, PsiExpression.class, true);
    
    if(expression == null) {
      PsiElement parent = context.getParent();
      
      if (parent instanceof PsiJavaCodeReferenceElement &&
          parent.getParent() instanceof PsiAnnotation
         ) {
        parent = parent.getParent().getParent();
      }
      
      if (!(parent instanceof PsiNameValuePair))
        return ArrayUtil.EMPTY_OBJECT_ARRAY;

      final PsiNameValuePair psiNameValuePair = ((PsiNameValuePair)parent);
      final PsiReference ref = psiNameValuePair.getReference();
      if (ref != null) {
        final Object[] variants = ref.getVariants();

        if (variants != null &&
            variants.length == 1 &&
            variants[0] instanceof PsiMethod &&
            ((PsiMethod)variants[0]).getReturnType() != null
          ) {
          return new PsiType[] { ((PsiMethod)variants[0]).getReturnType() };
        }
      }

      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    ExpectedTypeInfo[] infos = typesProvider.getExpectedTypes(expression, true);

    return ContainerUtil.map(infos, new Function<ExpectedTypeInfo, PsiType>() { //We want function types!!!
      public PsiType fun(ExpectedTypeInfo info) {
        return info.getType();
      }
    }, PsiType.EMPTY_ARRAY);
  }
}
