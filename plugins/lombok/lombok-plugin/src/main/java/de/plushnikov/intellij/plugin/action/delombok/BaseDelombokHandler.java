package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.codeStyle.CodeStyleManager;
import de.plushnikov.intellij.plugin.processor.AbstractProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.AbstractClassProcessor;
import de.plushnikov.intellij.plugin.processor.field.AbstractFieldProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BaseDelombokHandler implements CodeInsightActionHandler {

  private final Collection<AbstractClassProcessor> classProcessors;
  private final Collection<AbstractFieldProcessor> fieldProcessors;

  protected BaseDelombokHandler(AbstractClassProcessor classProcessor, AbstractFieldProcessor fieldProcessor) {
    this(classProcessor);
    fieldProcessors.add(fieldProcessor);
  }

  protected BaseDelombokHandler(AbstractClassProcessor... classProcessors) {
    this.classProcessors = new ArrayList<AbstractClassProcessor>(Arrays.asList(classProcessors));
    this.fieldProcessors = new ArrayList<AbstractFieldProcessor>();
  }

  void addFieldProcessor(AbstractFieldProcessor... fieldProcessor) {
    fieldProcessors.addAll(Arrays.asList(fieldProcessor));
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiClass psiClass = OverrideImplementUtil.getContextClass(project, editor, file, false);
    if (null != psiClass) {
      for (AbstractClassProcessor classProcessor : classProcessors) {
        processClass(project, psiClass, classProcessor);
      }

      for (AbstractFieldProcessor fieldProcessor : fieldProcessors) {
        processFields(project, psiClass, fieldProcessor);
      }

      UndoUtil.markPsiFileForUndo(file);
    }
  }

  protected void processClass(@NotNull Project project, @NotNull PsiClass psiClass, AbstractProcessor classProcessor) {
    Collection<PsiAnnotation> psiAnnotations = classProcessor.collectProcessedAnnotations(psiClass);

    List<? super PsiElement> psiElements = classProcessor.process(psiClass);
    for (Object psiElement : psiElements) {
      psiClass.add(rebuildPsiElement(project, (PsiElement) psiElement));
    }

    deleteAnnotations(psiAnnotations);
  }

  private void processFields(@NotNull Project project, @NotNull PsiClass psiClass, AbstractProcessor fieldProcessor) {
    Collection<PsiAnnotation> psiAnnotations = fieldProcessor.collectProcessedAnnotations(psiClass);

    List<? super PsiElement> psiElements = fieldProcessor.process(psiClass);
    for (Object psiElement : psiElements) {
      psiClass.add(rebuildPsiElement(project, (PsiMethod) psiElement));
    }

    deleteAnnotations(psiAnnotations);
  }

  private PsiElement rebuildPsiElement(@NotNull Project project, PsiElement psiElement) {
    if (psiElement instanceof PsiMethod) {
      return rebuildMethod(project, (PsiMethod) psiElement);
    } else if (psiElement instanceof PsiField) {
      return rebuildField(project, (PsiField) psiElement);
    } else if (psiElement instanceof PsiClass) {
      //TODO
    }
    return null;
  }

  private PsiMethod rebuildMethod(@NotNull Project project, @NotNull PsiMethod fromMethod) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    final PsiMethod resultMethod = elementFactory.createMethod(fromMethod.getName(), fromMethod.getReturnType());

    for (PsiParameter parameter : fromMethod.getParameterList().getParameters()) {
      PsiParameter param = elementFactory.createParameter(parameter.getName(), parameter.getType());
      resultMethod.getParameterList().add(param);
    }

    final PsiModifierList fromMethodModifierList = fromMethod.getModifierList();
    final PsiModifierList resultMethodModifierList = resultMethod.getModifierList();
    copyModifiers(fromMethodModifierList, resultMethodModifierList);
    for (PsiAnnotation psiAnnotation : fromMethodModifierList.getAnnotations()) {
      final PsiAnnotation annotation = resultMethodModifierList.addAnnotation(psiAnnotation.getQualifiedName());
      for (PsiNameValuePair nameValuePair : psiAnnotation.getParameterList().getAttributes()) {
        annotation.setDeclaredAttributeValue(nameValuePair.getName(), nameValuePair.getValue());
      }
    }

    PsiCodeBlock body = fromMethod.getBody();
    if (null != body) {
      resultMethod.getBody().replace(body);
    }

    return (PsiMethod) CodeStyleManager.getInstance(project).reformat(resultMethod);
  }

  private void copyModifiers(PsiModifierList fromModifierList, PsiModifierList resultModifierList) {
    for (String modifier : PsiModifier.MODIFIERS) {
      resultModifierList.setModifierProperty(modifier, fromModifierList.hasModifierProperty(modifier));
    }
  }

  private PsiField rebuildField(@NotNull Project project, @NotNull PsiField fromField) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

    final PsiField resultField = elementFactory.createField(fromField.getName(), fromField.getType());
    copyModifiers(fromField.getModifierList(), resultField.getModifierList());
    resultField.setInitializer(fromField.getInitializer());

    return (PsiField) CodeStyleManager.getInstance(project).reformat(resultField);
  }

  private void deleteAnnotations(Collection<PsiAnnotation> psiAnnotations) {
    for (PsiAnnotation psiAnnotation : psiAnnotations) {
      psiAnnotation.delete();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
