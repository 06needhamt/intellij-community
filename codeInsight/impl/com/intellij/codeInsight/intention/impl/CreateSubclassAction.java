/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 26, 2002
 * Time: 2:33:58 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightNamesUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CreateSubclassAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.ImplementAbstractClassAction");
  private String myText = CodeInsightBundle.message("intention.implement.abstract.class.default.text");
  @NonNls private static final String IMPL_SUFFIX = "Impl";

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.implement.abstract.class.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
    if (element == null) return false;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null || psiClass.isAnnotationType() || psiClass.isEnum() || psiClass instanceof PsiAnonymousClass) return false;
    PsiJavaToken lBrace = psiClass.getLBrace();
    if (lBrace == null) return false;
    if (element.getTextOffset() >= lBrace.getTextOffset()) return false;

    TextRange declarationRange = HighlightNamesUtil.getClassDeclarationTextRange(psiClass);
    if (!declarationRange.contains(element.getTextRange())) return false;

    myText = psiClass.isInterface()
             ? CodeInsightBundle.message("intention.implement.abstract.class.interface.text")
             : psiClass.hasModifierProperty(PsiModifier.ABSTRACT)
               ? CodeInsightBundle.message("intention.implement.abstract.class.default.text")
               : CodeInsightBundle.message("intention.implement.abstract.class.subclass.text");
    return true;
  }

  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

    PsiDirectory sourceDir = file.getContainingDirectory();

    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(sourceDir);
    final CreateClassDialog dialog = new CreateClassDialog(
      project,
      myText,
      psiClass.getName() + IMPL_SUFFIX,
      aPackage != null ? aPackage.getQualifiedName() : "",
      CreateClassKind.CLASS, true, ModuleUtil.findModuleForPsiElement(file));
    dialog.show();
    if (!dialog.isOK()) return;
    final PsiDirectory targetDirectory = dialog.getTargetDirectory();
    if (targetDirectory == null) return;

    PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable () {
      public void run() {
        PsiClass targetClass = ApplicationManager.getApplication().runWriteAction(new Computable<PsiClass>() {
          public PsiClass compute() {

            IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

            PsiClass targetClass;
            try {
              targetClass = JavaDirectoryService.getInstance().createClass(targetDirectory, dialog.getClassName());
            }
            catch (IncorrectOperationException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                      public void run() {
                        Messages.showErrorDialog(project,
                                                 CodeInsightBundle.message( "intention.implement.abstract.class.error.cannot.create.class.message", dialog.getClassName()),
                                                 CodeInsightBundle.message("intention.implement.abstract.class.error.cannot.create.class.title"));
                      }
                    });
              return null;
            }
            PsiJavaCodeReferenceElement ref = JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createClassReferenceElement(psiClass);

            try {
              if (psiClass.isInterface()) {
                targetClass.getImplementsList().add(ref);
              }
              else {
                targetClass.getExtendsList().add(ref);
              }
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }

            return targetClass;
          }
        });
        if (targetClass == null) return;

        final Editor editor1 = CodeInsightUtil.positionCursor(project, targetClass.getContainingFile(), targetClass.getLBrace());
        if (editor1 == null) return;
        OverrideImplementUtil.chooseAndImplementMethods(project, editor1, targetClass);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

}
