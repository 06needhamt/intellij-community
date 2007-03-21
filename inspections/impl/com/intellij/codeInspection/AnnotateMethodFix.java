package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.impl.AddAnnotationFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class AnnotateMethodFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.AnnotateMethodFix");
  private final String myAnnotation;

  public AnnotateMethodFix(final String fqn) {
    myAnnotation = fqn;
  }

  @NotNull
  public String getName() {
    return InspectionsBundle.message("inspection.annotate.quickfix.name", ClassUtil.extractClassName(myAnnotation));
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();

    PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (method == null) return;
    final List<PsiMethod> toAnnotate = new ArrayList<PsiMethod>();
    toAnnotate.add(method);
    List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      if (!AnnotationUtil.isAnnotated(superMethod, myAnnotation, false) && superMethod.getManager().isInProject(superMethod)) {
        int ret = annotateBaseMethod(method, superMethod, project);
        if (ret != 0 && ret != 1) return;
        if (ret == 0) {
          toAnnotate.add(superMethod);
        }
      }
    }
    if (annotateOverriddenMethods()) {
      PsiMethod[] methods = method.getManager().getSearchHelper().findOverridingMethods(method, GlobalSearchScope.allScope(project), true);
      for (PsiMethod psiMethod : methods) {
        if (!AnnotationUtil.isAnnotated(psiMethod, myAnnotation, false) && psiMethod.getManager().isInProject(psiMethod)) {
          toAnnotate.add(psiMethod);
        }
      }
    }

    CodeInsightUtil.preparePsiElementsForWrite(toAnnotate);
    for (PsiMethod psiMethod : toAnnotate) {
      annotateMethod(psiMethod);
    }
    UndoManager.getInstance(project).markDocumentForUndo(method.getContainingFile());
  }

  public int annotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
    String implement = !method.hasModifierProperty(PsiModifier.ABSTRACT) && superMethod.hasModifierProperty(PsiModifier.ABSTRACT)
                  ? InspectionsBundle.message("inspection.annotate.quickfix.implements")
                  : InspectionsBundle.message("inspection.annotate.quickfix.overrides");
    String message = InspectionsBundle.message("inspection.annotate.quickfix.overridden.method.messages",
                                               UsageViewUtil.getDescriptiveName(method), implement,
                                               UsageViewUtil.getDescriptiveName(superMethod));
    String title = InspectionsBundle.message("inspection.annotate.quickfix.overridden.method.warning");
    return Messages.showYesNoCancelDialog(project, message, title, Messages.getQuestionIcon());
  }

  protected boolean annotateOverriddenMethods() {
    return false;
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  private void annotateMethod(final PsiMethod method) {
    try {
      new AddAnnotationFix(myAnnotation, method).invoke(method.getProject(), null, method.getContainingFile());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
