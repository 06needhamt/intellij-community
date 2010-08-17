package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;

/**
 * @author yole
 */
public class Py3ResolveTest extends PyResolveTestCase {
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPy3Descriptor;
  }

  @Override
  protected PsiElement doResolve() {
    myFixture.configureByFile("resolve/" + getTestName(false) + ".py");
    int offset = findMarkerOffset(myFixture.getFile());
    final PsiReference ref = myFixture.getFile().findReferenceAt(offset);
    return ref.resolve();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), LanguageLevel.PYTHON31);
  }

  @Override
  protected void tearDown() throws Exception {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), null);
    super.tearDown();
  }

  public void testObjectMethods() {  // PY-1494
    assertResolvesTo(PyFunction.class, "__repr__");
  }
}
