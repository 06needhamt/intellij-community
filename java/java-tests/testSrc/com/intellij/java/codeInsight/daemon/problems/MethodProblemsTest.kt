// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*

internal class MethodProblemsTest: ProjectProblemsViewTest() {

  fun testRename() = doMethodTest { method, factory ->
    method.nameIdentifier?.replace(factory.createIdentifier("bar"))
  }

  fun testChangeReturnType() = doMethodTest { method, factory ->
    method.returnTypeElement?.replace(factory.createTypeElement(PsiPrimitiveType.BOOLEAN))
  }

  fun testMakeMethodPackagePrivate() = doMethodTest { method, _ ->
    method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
  }

  fun testMakeMethodPrivate() = doMethodTest {method, _ ->
    method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
    method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
  }

  fun testRemoveParameter() = doMethodTest { method, _ ->
    method.parameterList.getParameter(0)?.delete()
  }

  fun testAddParameter() = doMethodTest { method, factory ->
    val parameter = factory.createParameter("i", PsiPrimitiveType.INT)
    method.parameterList.add(parameter)
  }

  fun testChangeParameterType() = doMethodTest {method, factory ->
    method.parameterList.getParameter(0)?.typeElement?.replace(factory.createTypeElement(PsiPrimitiveType.BOOLEAN))
  }

  fun testMakeOverrideMethodPrivateInAbstractClass() {
    myFixture.addClass("""
      package foo;
      
      public abstract class Parent {
        public abstract int len(String s);
      }
    """.trimIndent())

    val targetClass = myFixture.addClass("""
      package foo;
      
      public abstract class A extends Parent {
      
        public int len(String s) {
          return s.length();
        }
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package foo;
      
      public class B extends A {
      }
    """.trimIndent())

    doTest(targetClass) {
      changeMethod(targetClass) { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
        method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
      }
      assertTrue(hasReportedProblems<PsiClass>(targetClass, refClass))
    }
  }

  fun testMakeAbstractInAbstractClass() {
    val targetClass = myFixture.addClass("""
      package foo;
      
      public abstract class A {
      
        public int len(String s) {
          return s.length();
        }
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      
      import foo.A;
      
      public class B {
        void test() {
          int len = (new A() {}).len("foo");
        }
      }
    """.trimIndent())

    doTest(targetClass) {
      changeMethod(targetClass) { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.ABSTRACT, true)
      }
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(targetClass, refClass))
    }
  }

  fun testMethodOverrideScopeIsChanged() {
    myFixture.addClass("""
      package bar;
      
      public class A {
        public void foo() {}
      }
    """.trimIndent())

    val bClass = myFixture.addClass("""
      package bar;
      
      public class B extends A {
        @Override
        public void foo() {}
      }
    """.trimIndent())

    val cClass = myFixture.addClass("""
      package baz;
      
      import bar.B;
      
      public class C extends B {
        @Override
        public void foo() {}
      }
    """.trimIndent())

    doTest(bClass) {
      changeMethod(bClass) { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.PUBLIC, false)
      }
      assertTrue(hasReportedProblems<PsiMethod>(bClass, cClass))
      changeMethod(bClass) { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
      }
      // method now overrides A#foo
      assertFalse(hasReportedProblems<PsiMethod>(bClass, cClass))
    }
  }

  fun testMakeInterfaceMethodPrivate() {
    val targetClass = myFixture.addClass("""
      package foo;
      
      public interface A {
        int test();
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      
      import foo.*;
      
      public class B {
      
        void test(A a) { 
          int i = a.test();
        }
      }
    """.trimIndent())

    doTest(targetClass) {
      changeMethod(targetClass) { method, _ ->
        method.modifierList.setModifierProperty(PsiModifier.PRIVATE, true)
      }
    }

    assertTrue(hasReportedProblems<PsiDeclarationStatement>(targetClass, refClass))
  }

  private fun doMethodTest(methodChangeAction: (PsiMethod, PsiElementFactory) -> Unit) {

    val targetClass = myFixture.addClass("""
      package foo;
      
      public class A {
      
        public int len(String s) {
          return s.length();
        }
      }
    """.trimIndent())

    val refClass = myFixture.addClass("""
      package bar;
      
      import foo.A;
      
      public class B {
        void test() {
          int len = (new A()).len("foo");
        }
      }
    """.trimIndent())

    doTest(targetClass) {
      changeMethod(targetClass, methodChangeAction)
      assertTrue(hasReportedProblems<PsiDeclarationStatement>(targetClass, refClass))
    }
  }

  private fun changeMethod(targetClass: PsiClass, methodChangeAction: (PsiMethod, PsiElementFactory) -> Unit) {
    val methods = targetClass.methods
    assertSize(1, methods)
    val method = methods[0]

    WriteCommandAction.runWriteCommandAction(project) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory
      methodChangeAction(method, factory)
    }
    myFixture.doHighlighting()
  }
}