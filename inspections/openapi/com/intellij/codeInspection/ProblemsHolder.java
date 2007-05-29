/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class ProblemsHolder {
  private InspectionManager myManager;
  private List<ProblemDescriptor> myProblems = null;

  public ProblemsHolder(final InspectionManager manager) {
    myManager = manager;
  }

  public void registerProblem(PsiElement psiElement, String descriptionTemplate, LocalQuickFix... fixes) {
    registerProblem(psiElement, descriptionTemplate, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes);
  }

  public void registerProblem(PsiElement psiElement,
                              String descriptionTemplate,
                              ProblemHighlightType highlightType,
                              LocalQuickFix... fixes) {
    registerProblem(myManager.createProblemDescriptor(psiElement, descriptionTemplate, fixes, highlightType));
  }

  public void registerProblem(ProblemDescriptor problemDescriptor) {
    if (myProblems == null) {
      myProblems = new ArrayList<ProblemDescriptor>(1);
    }
    myProblems.add(problemDescriptor);
  }

  public void registerProblem(PsiReference reference, String descriptionTemplate, ProblemHighlightType highlightType) {
    if (myProblems == null) {
      myProblems = new ArrayList<ProblemDescriptor>(1);
    }

    LocalQuickFix[] fixes = null;
    if (reference instanceof LocalQuickFixProvider) {
      fixes = ((LocalQuickFixProvider)reference).getQuickFixes();
    }

    myProblems.add(myManager.createProblemDescriptor(reference.getElement(), reference.getRangeInElement(), descriptionTemplate, highlightType, fixes));
  }

  @Nullable
  public List<ProblemDescriptor> getResults() {
    final List<ProblemDescriptor> problems = myProblems;
    myProblems = null;
    return problems;
  }

  public final InspectionManager getManager() {
    return myManager;
  }
  public boolean hasResults() {
    return myProblems != null && !myProblems.isEmpty();
  }
}
