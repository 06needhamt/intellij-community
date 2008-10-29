package com.intellij.refactoring;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.util.PsiTreeUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class IntroduceConstantTest extends CodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/refactoring/introduceConstant/";

  public void testInNonNls() throws Exception {
    doTest(false);
  }

  private void doTest(boolean makeEnumConstant) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    convertLocal(makeEnumConstant);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testEnumConstant() throws Exception {
    doTest(true);    
  }

  private void convertLocal(final boolean makeEnumConstant) {
    PsiLocalVariable local = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
    new MockLocalToFieldHandler(getProject(), true, makeEnumConstant).convertLocalToField(local, getEditor());
  }

  public void testPartialStringLiteral() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testPartialStringLiteralQualified() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    final PsiClass psiClass = ((PsiJavaFile)getFile()).getClasses()[0];
    Assert.assertNotNull(psiClass);
    final PsiClass targetClass = psiClass.findInnerClassByName("D", false);
    Assert.assertNotNull(targetClass);
    new MockIntroduceConstantHandler(targetClass).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }
}