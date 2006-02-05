package com.intellij.codeInsight.hint.api.impls;

import com.intellij.codeInsight.hint.api.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.*;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Feb 1, 2006
 * Time: 3:16:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class AnnotationParameterInfoHandler implements ParameterInfoHandler<PsiAnnotationParameterList,PsiAnnotationMethod> {
  public @Nullable Object[] getParametersForLookup(LookupItem item, ParameterInfoContext context) {
    return null;
  }

  public boolean couldShowInLookup() {
    return false;
  }

  public PsiAnnotationParameterList findElementForParameterInfo(final CreateParameterInfoContext context) {
    final PsiAnnotation annotation = ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiAnnotation.class);

    if (annotation != null) {
      final PsiJavaCodeReferenceElement nameReference = annotation.getNameReferenceElement();

      if (nameReference != null) {
        final PsiElement resolved = nameReference.resolve();

        if (resolved instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)resolved;

          if (aClass.isAnnotationType()) {
            final PsiMethod[] methods = aClass.getMethods();

            if (methods.length != 0) {
              context.setItemsToShow(methods);

              final PsiAnnotationMethod annotationMethod = findAnnotationMethod(context.getFile(), context.getOffset());
              if (annotationMethod != null) context.setHighlightedElement(annotationMethod);

              return annotation.getParameterList();
            }
          }
        }
      }
    }

    return null;
  }

  public void showParameterInfo(final PsiAnnotationParameterList element, final CreateParameterInfoContext context) {
    context.showHint(element, element.getTextRange().getStartOffset() + 1, this);
    //showParameterListHint(context.getEditor(), element, context.getProject(), ParameterInfoController.DEFAULT_PARAMETER_CLOSE_CHARS,
    //                      context.getHighlightedElement(), context.getItemsToShow(),null);
  }

  public PsiAnnotationParameterList findElementForUpdatingParameterInfo(final UpdateParameterInfoContext context) {
    final PsiAnnotation annotation = ParameterInfoUtils.findParentOfType(context.getFile(), context.getOffset(), PsiAnnotation.class);
    return annotation != null ? annotation.getParameterList() : null;
  }

  public void updateParameterInfo(final PsiAnnotationParameterList o, final UpdateParameterInfoContext context) {
    CharSequence chars = context.getEditor().getDocument().getCharsSequence();
    int offset1 = CharArrayUtil.shiftForward(chars, context.getEditor().getCaretModel().getOffset(), " \t");
    if (chars.charAt(offset1) == ',') {
      offset1 = CharArrayUtil.shiftBackward(chars, offset1 - 1, " \t");
    }
    context.setHighlightedParameter(findAnnotationMethod(context.getFile(), offset1));
  }

  public String getParameterCloseChars() {
    return ParameterInfoUtils.DEFAULT_PARAMETER_CLOSE_CHARS;
  }

  public boolean tracksParameterIndex() {
    return true;
  }

  private static void updateAnnotationMethodPresentation(PsiAnnotationMethod method, ParameterInfoUIContext context) {
      @NonNls StringBuffer buffer = new StringBuffer();
      int highlightStartOffset;
      int highlightEndOffset;
      buffer.append(method.getReturnType().getPresentableText());
      buffer.append(" ");
      highlightStartOffset = buffer.length();
      buffer.append(method.getName());
      highlightEndOffset = buffer.length();
      buffer.append("()");
      if (method.getDefaultValue() != null) {
        buffer.append(" default ");
        buffer.append(method.getDefaultValue().getText());
      }

      context.setupUIComponentPresentation(buffer.toString(), highlightStartOffset, highlightEndOffset, false, method.isDeprecated(),
                                           false, context.getDefaultParameterColor());
    }

  public void updateUI(final PsiAnnotationMethod p, final ParameterInfoUIContext context) {
    updateAnnotationMethodPresentation(p,context);
  }

  private static PsiAnnotationMethod findAnnotationMethod(PsiFile file, int offset) {
    PsiNameValuePair pair = ParameterInfoUtils.findParentOfType(file, offset, PsiNameValuePair.class);
    if (pair == null) return null;
    final PsiElement resolved = pair.getReference().resolve();
    return resolved instanceof PsiAnnotationMethod ? (PsiAnnotationMethod)resolved : null;
  }
}
