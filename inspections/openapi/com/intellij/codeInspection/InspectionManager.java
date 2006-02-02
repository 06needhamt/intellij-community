/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public abstract class InspectionManager {
  public static InspectionManager getInstance(Project project) {
    return project.getComponent(InspectionManager.class);
  }

  @NotNull public abstract CommonProblemDescriptor createProblemDescriptor(String descriptionTemplate, QuickFix... fixes);

  /**
   * Factory method for ProblemDescriptor. Should be called from LocalInspectionTool.checkXXX() methods.
   * @param psiElement problem is reported against
   * @param descriptionTemplate problem message. Use <code>#ref</code> for a link to problem piece of code and <code>#loc</code> for location in source code.
   * @param fix should be null if no fix is provided.
   */
  @NotNull public abstract ProblemDescriptor createProblemDescriptor(PsiElement psiElement, String descriptionTemplate, LocalQuickFix fix, ProblemHighlightType highlightType);

  @NotNull public abstract ProblemDescriptor createProblemDescriptor(PsiElement psiElement, String descriptionTemplate, LocalQuickFix[] fixes, ProblemHighlightType highlightType);

  @NotNull public abstract ProblemDescriptor createProblemDescriptor(PsiElement psiElement, String descriptionTemplate, LocalQuickFix[] fixes, ProblemHighlightType highlightType, boolean isAfterEndOfLine);

  @NotNull public abstract ProblemDescriptor createProblemDescriptor(PsiElement startElement,
                                                                     PsiElement endElement,
                                                                     String descriptionTemplate,
                                                                     ProblemHighlightType highlightType,
                                                                     LocalQuickFix... fixes
  );

  
  @NotNull public abstract Project getProject();
}
