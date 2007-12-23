package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.AccessorMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author ven
 */
public class ResolvePropertyTest extends GroovyResolveTestCase {
  protected String getTestDataPath() {
    return TestUtils.getTestDataPath() + "/resolve/property/";
  }

  public void testParameter1() throws Exception {
    doTest("parameter1/A.groovy");
  }

  public void testClosureParameter1() throws Exception {
    doTest("closureParameter1/A.groovy");
  }
  
  public void testClosureOwner() throws Exception {
    PsiReference ref = configureByFile("closureOwner/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrVariable);
    assertEquals(((PsiClassType) ((GrVariable) resolved).getTypeGroovy()).getCanonicalText(), "W");
  }

  public void testLocal1() throws Exception {
    doTest("local1/A.groovy");
  }

  public void testField1() throws Exception {
    doTest("field1/A.groovy");
  }

  public void testField2() throws Exception {
    doTest("field2/A.groovy");
  }

  public void testForVariable1() throws Exception {
    doTest("forVariable1/ForVariable.groovy");
  }

  public void testArrayLength() throws Exception {
    doTest("arrayLength/A.groovy");
  }

  public void testFromGetter() throws Exception {
    PsiReference ref = configureByFile("fromGetter/A.groovy");
    assertTrue(ref.resolve() instanceof AccessorMethod);
  }

  public void testFromSetter() throws Exception {
    PsiReference ref = configureByFile("fromGetter/A.groovy");
    assertTrue(ref.resolve() instanceof AccessorMethod);
  }

  public void testForVariable2() throws Exception {
    doTest("forVariable2/ForVariable.groovy");
  }

  public void testCatchParameter() throws Exception {
    doTest("CatchParameter/CatchParameter.groovy");
  }

  public void testCaseClause() throws Exception {
    doTest("caseClause/CaseClause.groovy");
  }

  public void testGrvy104() throws Exception {
    doTest("grvy104/Test.groovy");
  }

  public void testGrvy270() throws Exception {
    PsiReference ref = configureByFile("grvy270/Test.groovy");
    assertNull(ref.resolve());
  }

  public void testField3() throws Exception {
    GrReferenceElement ref = (GrReferenceElement) configureByFile("field3/A.groovy").getElement();
    GroovyResolveResult resolveResult = ref.advancedResolve();
    assertTrue(resolveResult.getElement() instanceof GrField);
    assertFalse(resolveResult.isValidResult());
  }

  public void testToGetter() throws Exception {
    GrReferenceElement ref = (GrReferenceElement) configureByFile("toGetter/A.groovy").getElement();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertTrue(PropertyUtil.isSimplePropertyGetter((PsiMethod) resolved));
  }

  public void testToSetter() throws Exception {
    GrReferenceElement ref = (GrReferenceElement) configureByFile("toSetter/A.groovy").getElement();
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertTrue(PropertyUtil.isSimplePropertySetter((PsiMethod) resolved));
  }

  public void testUndefinedVar1() throws Exception {
    PsiReference ref = configureByFile("undefinedVar1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrReferenceExpression);
    GrTopStatement statement = ((GroovyFileBase) resolved.getContainingFile()).getTopStatements()[2];
    assertTrue(resolved.equals(((GrAssignmentExpression) statement).getLValue()));
  }

  public void testRecursive1() throws Exception {
    PsiReference ref = configureByFile("recursive1/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof PsiMethod);
    assertEquals(resolved, PsiTreeUtil.getParentOfType(ref.getElement(), PsiMethod.class));
  }

  public void testRecursive2() throws Exception {
    PsiReference ref = configureByFile("recursive2/A.groovy");
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrMethod);
    assertNull(((GrMethod) resolved).getReturnType());
  }

  public void testNotAField() throws Exception {
    PsiReference ref = configureByFile("notAField/A.groovy");
    assertNull(ref.resolve());
  }

  public void testUndefinedVar2() throws Exception {
    doUndefinedVarTest("undefinedVar2/A.groovy");
  }

  public void testDefinedVar1() throws Exception {
    doTest("definedVar1/A.groovy");
  }

  public void testOperatorOverload() throws Exception {
    doTest("operatorOverload/A.groovy");
  }

  public void testEnumConstant() throws Exception {
    PsiReference ref = configureByFile("enumConstant/A.groovy");
    assertTrue(ref.resolve() instanceof GrEnumConstant);
  }

  public void testStackOverflow() throws Exception {
    doTest("stackOverflow/A.groovy");
  }

  public void testFromDifferentCaseClause() throws Exception {
    PsiReference ref = configureByFile("fromDifferentCaseClause/A.groovy");
    assertNull(ref.resolve());
  }

  public void testNotSettingProperty() throws Exception {
    PsiReference ref = configureByFile("notSettingProperty/A.groovy");
    assertNull(ref.resolve());
  }

  public void testGRVY633() throws Exception {
    PsiReference ref = configureByFile("GRVY633/A.groovy");
    assertNull(ref.resolve());
  }

  public void testGRVY575() throws Exception {
    doTest("GRVY575/A.groovy");
  }

  public void testGRVY747() throws Exception {
    PsiReference ref = configureByFile("GRVY747/A.groovy");
    assertTrue(ref.resolve() instanceof AccessorMethod);
  }

  private void doTest(String fileName) throws Exception {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrVariable);
  }

  private void doUndefinedVarTest(String fileName) throws Exception {
    PsiReference ref = configureByFile(fileName);
    PsiElement resolved = ref.resolve();
    assertTrue(resolved instanceof GrReferenceExpression);
  }
}