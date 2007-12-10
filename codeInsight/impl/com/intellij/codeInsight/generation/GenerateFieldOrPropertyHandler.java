/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.generation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyMemberType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class GenerateFieldOrPropertyHandler extends GenerateMembersHandlerBase {
  private final String myAttributeName;
  private final PsiType myType;
  private final PropertyMemberType myMemberType;
  private final PsiAnnotation[] myAnnotations;

  public GenerateFieldOrPropertyHandler(String attributeName, PsiType type, final PropertyMemberType memberType, final PsiAnnotation... annotations) {
    super("");
    myAttributeName = attributeName;
    myType = type;
    myMemberType = memberType;
    myAnnotations = annotations;
  }

  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    return ClassMember.EMPTY_ARRAY;
  }


  @NotNull
  public List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    final PsiElementFactory psiElementFactory = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory();
    try {
      final String name = myMemberType == PropertyMemberType.FIELD? myAttributeName : JavaCodeStyleManager.getInstance(aClass.getProject()).propertyNameToVariableName(myAttributeName, VariableKind.FIELD);
      final PsiField psiField = psiElementFactory.createField(name, myType);
      final GenerationInfo[] objects = new GenerateGetterAndSetterHandler().generateMemberPrototypes(aClass, new PsiFieldMember(psiField));
      final GenerationInfo getter = objects[0];
      final GenerationInfo setter = objects[1];
      if (myAnnotations.length > 0) {
        final PsiMember targetMember;
        switch (myMemberType) {
          case FIELD: targetMember = psiField;
            break;
          case GETTER: targetMember = getter.getPsiMember();
            break;
          case SETTER: targetMember = setter.getPsiMember();
            break;
          default: targetMember = null;
            break;
        }
        assert targetMember != null;
        for (PsiAnnotation annotation : myAnnotations) {
          targetMember.getModifierList().addAfter(annotation, null);
        }

        JavaCodeStyleManager.getInstance(targetMember.getProject()).shortenClassReferences(targetMember.getModifierList());
      }
      return Arrays.asList(new PsiGenerationInfo<PsiField>(psiField), getter, setter);
    }
    catch (IncorrectOperationException e) {
      assert false : e;
      return Collections.emptyList();
    }
  }

  protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
    throw new UnsupportedOperationException();
  }

  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }
}
