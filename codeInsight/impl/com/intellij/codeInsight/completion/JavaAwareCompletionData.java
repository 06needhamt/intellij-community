/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.scope.CompletionElement;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import static com.intellij.patterns.StandardPatterns.character;
import static com.intellij.patterns.StandardPatterns.not;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class JavaAwareCompletionData extends CompletionData{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaAwareCompletionData");

  protected void completeReference(final PsiReference reference, final PsiElement position, final Set<LookupElement> set, final TailType tailType,
                                   final PsiFile file,
                                   final ElementFilter filter,
                                   final CompletionVariant variant) {
    completeReference(reference, position, set, tailType, file, filter, variant, true);
  }

  protected void completeReference(final PsiReference reference, final PsiElement position, final Set<LookupElement> set, final TailType tailType,
                                   final PsiFile file,
                                   final ElementFilter filter,
                                   final CompletionVariant variant,
                                   final boolean checkAccess) {
    final JavaCompletionProcessor processor = new JavaCompletionProcessor(position, filter, checkAccess);

    if (reference instanceof PsiMultiReference) {
      int javaReferenceStart = -1;

      PsiReference[] references = getReferences((PsiMultiReference)reference);

      for (PsiReference ref : references) {
        if (ref instanceof PsiJavaReference) {
          int newStart = ref.getElement().getTextRange().getStartOffset() + ref.getRangeInElement().getStartOffset();
          if (javaReferenceStart == -1) {
            javaReferenceStart = newStart;
          } else {
            if (newStart == javaReferenceStart) continue;
          }
        }
        completeReference(ref, position, set, tailType, file, filter, variant);
      }
    }
    else if(reference instanceof PsiJavaReference){
      ((PsiJavaReference)reference).processVariants(processor);
    }
    else{
      final Object[] completions = reference.getVariants();
      if(completions == null) return;

      for (Object completion : completions) {
        if (completion == null) {
          LOG.assertTrue(false, "Position=" + position + "\n;Reference=" + reference + "\n;variants=" + Arrays.toString(completions));
        }
        if (completion instanceof PsiElement) {
          final PsiElement psiElement = (PsiElement)completion;
          if (filter.isClassAcceptable(psiElement.getClass()) && filter.isAcceptable(new CandidateInfo(psiElement, PsiSubstitutor.EMPTY), position)) {
            processor.execute(psiElement, ResolveState.initial());
          }
        }
        else if (completion instanceof CandidateInfo) {
          final CandidateInfo info = (CandidateInfo)completion;
          if (info.isValidResult() && filter.isAcceptable(info, position)) {
            processor.execute(info.getElement(), ResolveState.initial().put(PsiSubstitutor.KEY, info.getSubstitutor()));
          }
        }
        else {
          if (completion instanceof LookupItem) {
            final Object o = ((LookupItem)completion).getObject();
            if (o instanceof PsiElement && (!filter.isClassAcceptable(o.getClass()) ||
                                            !filter.isAcceptable(new CandidateInfo((PsiElement)o, PsiSubstitutor.EMPTY), position))) {
              continue;
            }
          }
          addLookupItem(set, tailType, completion, file, variant);
        }
      }
    }

    Collection<CompletionElement> results = processor.getResults();
    if (results != null) {
      for (CompletionElement element : results) {
        variant.setItemProperty(LookupItem.SUBSTITUTOR, element.getSubstitutor());
        variant.setItemProperty(JavaCompletionUtil.QUALIFIER_TYPE_ATTR, element.getQualifier());
        addLookupItem(set, tailType, element.getElement(), file, variant);
        variant.setItemProperty(LookupItem.SUBSTITUTOR, null);
        variant.setItemProperty(JavaCompletionUtil.QUALIFIER_TYPE_ATTR, null);
      }
    }
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  @Nullable
  public static String getReferencePrefix(@NotNull PsiElement insertedElement, int offsetInFile) {
    int offsetInElement = offsetInFile - insertedElement.getTextRange().getStartOffset();
    //final PsiReference ref = insertedElement.findReferenceAt(offsetInElement);
    final PsiReference ref = insertedElement.getContainingFile().findReferenceAt(insertedElement.getTextRange().getStartOffset() + offsetInElement);
    if(ref instanceof PsiJavaCodeReferenceElement) {
      final PsiElement name = ((PsiJavaCodeReferenceElement)ref).getReferenceNameElement();
      if(name != null){
        offsetInElement = offsetInFile - name.getTextRange().getStartOffset();
        return name.getText().substring(0, offsetInElement);
      }
      return "";
    }
    else if(ref != null) {
      offsetInElement = offsetInFile - ref.getElement().getTextRange().getStartOffset();

      String result = ref.getElement().getText().substring(ref.getRangeInElement().getStartOffset(), offsetInElement);
      if(result.indexOf('(') > 0){
        result = result.substring(0, result.indexOf('('));
      }

      if (ref.getElement() instanceof PsiNameValuePair && StringUtil.startsWithChar(result,'{')) {
        result = result.substring(1); // PsiNameValuePair reference without name span all content of the element
      }
      return result;
    }

    return null;
  }

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static String findPrefixStatic(PsiElement insertedElement, int offsetInFile) {
    if(insertedElement == null) return "";

    final String prefix = getReferencePrefix(insertedElement, offsetInFile);
    if (prefix != null) return prefix;

    if (insertedElement instanceof PsiPlainText) return "";

    return findPrefixDefault(insertedElement, offsetInFile, not(character().javaIdentifierPart()));
  }

  protected void addLookupItem(Set<LookupElement> set, TailType tailType, @NotNull Object completion, final PsiFile file, final CompletionVariant variant) {
    if (completion instanceof LookupElement && !(completion instanceof LookupItem)) {
      set.add((LookupElement)completion);
      return;
    }

    LookupItem ret = LookupItemUtil.objectToLookupItem(completion);
    if(ret == null) return;

    final InsertHandler insertHandler = variant.getInsertHandler();
    if(insertHandler != null && ret.getInsertHandler() == null) {
      ret.setInsertHandler(insertHandler);
      ret.setTailType(TailType.UNKNOWN);
    }
    else if (tailType != TailType.NONE) {
      ret.setTailType(tailType);
    }
    else if (file instanceof PsiJavaCodeReferenceCodeFragment) {
      PsiJavaCodeReferenceCodeFragment fragment = (PsiJavaCodeReferenceCodeFragment)file;
      if (!fragment.isClassesAccepted() && completion instanceof PsiPackage) {
        ret.setTailType(TailType.NONE);
      }
    }

    final Map<Object, Object> itemProperties = variant.getItemProperties();
    for (final Object key : itemProperties.keySet()) {
      if (key == LookupItem.DO_AUTOCOMPLETE_ATTR) {
        ret.setAutoCompletionPolicy(AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE);
        continue;
      } else if (key == LookupItem.DO_NOT_AUTOCOMPLETE_ATTR) {
        ret.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
        continue;
      }

      if (key == LookupItem.FORCE_SHOW_FQN_ATTR && ret.getObject() instanceof PsiClass) {
        setShowFQN(ret);
      }
      else {
        if (completion instanceof PsiMember && key == LookupItem.FORCE_QUALIFY) {
          qualify(ret);
        }
        ret.setAttribute(key, itemProperties.get(key));
      }
    }
    set.add(ret);
  }

  public static LookupItem qualify(final LookupItem ret) {
    final PsiMember completionElement = (PsiMember)ret.getObject();
    final PsiClass containingClass = completionElement.getContainingClass();
    if (containingClass != null) {
      final String className = containingClass.getName();
      ret.setLookupString(className + "." + ret.getLookupString());
    }
    ret.setAttribute(LookupItem.FORCE_QUALIFY, "");
    return ret;
  }


  protected void addKeywords(final Set<LookupElement> set, final PsiElement position, final PrefixMatcher matcher, final PsiFile file,
                                  final CompletionVariant variant, final Object comp, final TailType tailType) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();
    if (comp instanceof String) {
      addKeyword(factory, set, tailType, comp, matcher, file, variant);
    }
    else {
      final CompletionContext context = position.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
      if (comp instanceof ContextGetter) {
        final Object[] elements = ((ContextGetter)comp).get(position, context);
        for (Object element : elements) {
          addLookupItem(set, tailType, element, file, variant);
        }
      }
      // TODO: KeywordChooser -> ContextGetter
      else if (comp instanceof KeywordChooser) {
        final String[] keywords = ((KeywordChooser)comp).getKeywords(context, position);
        for (String keyword : keywords) {
          addKeyword(factory, set, tailType, keyword, matcher, file, variant);
        }
      }
    }
  }

  private void addKeyword(PsiElementFactory factory, Set<LookupElement> set, final TailType tailType, final Object comp, final PrefixMatcher matcher,
                                final PsiFile file,
                                final CompletionVariant variant) {
    for (final LookupElement item : set) {
      if (item.getObject().toString().equals(comp.toString())) {
        return;
      }
    }
    if(factory == null){
      addLookupItem(set, tailType, comp, file, variant);
    }
    else{
      try{
        final PsiKeyword keyword = factory.createKeyword((String)comp);
        addLookupItem(set, tailType, keyword, file, variant);
      }
      catch(IncorrectOperationException e){
        addLookupItem(set, tailType, comp, file, variant);
      }
    }
  }

  public static LookupItem setShowFQN(final LookupItem ret) {
    final PsiClass psiClass = (PsiClass)ret.getObject();
    @NonNls String packageName = PsiFormatUtil.getPackageDisplayName(psiClass);

    final String tailText = (String)ret.getAttribute(LookupItem.TAIL_TEXT_ATTR);
    ret.setAttribute(LookupItem.TAIL_TEXT_ATTR, StringUtil.notNullize(tailText) + " (" + packageName + ")");
    ret.setAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR, "");
    return ret;
  }

}
