/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.codeInsight.regexp;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonRegexpInjector implements LanguageInjector {
  private static class RegexpMethodDescriptor {
    private final String methodName;
    private final int argIndex;

    private RegexpMethodDescriptor(String methodName, int argIndex) {
      this.methodName = methodName;
      this.argIndex = argIndex;
    }
  }

  private final List<RegexpMethodDescriptor> myDescriptors = new ArrayList<RegexpMethodDescriptor>();

  public PythonRegexpInjector() {
    addMethod("compile");
    addMethod("search");
    addMethod("match");
    addMethod("split");
    addMethod("findall");
    addMethod("finditer");
    addMethod("sub");
    addMethod("subn");
  }

  private void addMethod(final String name) {
    myDescriptors.add(new RegexpMethodDescriptor(name, 0));
  }

  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (host instanceof PyStringLiteralExpression && host.getParent() instanceof PyArgumentList) {
      final PyExpression[] args = ((PyArgumentList)host.getParent()).getArguments();
      int index = ArrayUtil.indexOf(args, host);
      PyCallExpression call = PsiTreeUtil.getParentOfType(host, PyCallExpression.class);
      if (call != null) {
        final PyExpression callee = call.getCallee();
        if (callee instanceof PyReferenceExpression && canBeRegexpCall(callee)) {
          final PsiElement element = ((PyReferenceExpression)callee).getReference(PyResolveContext.noImplicits()).resolve();
          if (element != null && element.getContainingFile().getName().equals("re.py") && isRegexpMethod(element, index)) {
            List<TextRange> ranges = ((PyStringLiteralExpression)host).getStringValueTextRanges();
            if (ranges.size() == 1) {
              injectionPlacesRegistrar.addPlace(isVerbose(call) ? PythonVerboseRegexpLanguage.INSTANCE : PythonRegexpLanguage.INSTANCE,
                                                ranges.get(0), null, null);
            }
          }
        }
      }
    }
  }

  private static boolean isVerbose(PyCallExpression call) {
    PyExpression[] arguments = call.getArguments();
    if (arguments.length <= 1) {
      return false;
    }
    return isVerbose(arguments[arguments.length-1]);
  }

  private static boolean isVerbose(PyExpression expr) {
    if (expr instanceof PyKeywordArgument) {
      PyKeywordArgument keywordArgument = (PyKeywordArgument)expr;
      if (!"flags".equals(keywordArgument.getName())) {
        return false;
      }
      return isVerbose(keywordArgument.getValueExpression());
    }
    if (expr instanceof PyReferenceExpression) {
      return "VERBOSE".equals(((PyReferenceExpression)expr).getReferencedName());
    }
    if (expr instanceof PyBinaryExpression) {
      return isVerbose(((PyBinaryExpression)expr).getLeftExpression()) || isVerbose(((PyBinaryExpression)expr).getRightExpression());
    }
    return false;
  }

  private boolean isRegexpMethod(PsiElement element, int index) {
    if (!(element instanceof PyFunction)) {
      return false;
    }
    final String name = ((PyFunction)element).getName();
    for (RegexpMethodDescriptor descriptor : myDescriptors) {
      if (descriptor.methodName.equals(name) && descriptor.argIndex == index) {
        return true;
      }
    }
    return false;
  }

  private boolean canBeRegexpCall(PyExpression callee) {
    String text = callee.getText();
    for (RegexpMethodDescriptor descriptor : myDescriptors) {
      if (text.endsWith(descriptor.methodName)) {
        return true;
      }
    }
    return false;
  }
}
