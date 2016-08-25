/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.completion.JavaKeywordCompletion.OverrideableSpace;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaModuleNameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.completion.JavaKeywordCompletion.createKeyword;

class JavaModuleCompletion {
  static boolean isModuleFile(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel9OrHigher(file) && PsiJavaModule.MODULE_INFO_FILE.equals(file.getName());
  }

  static void addVariants(@NotNull PsiElement position, @NotNull Consumer<LookupElement> result) {
    if (position instanceof PsiIdentifier) {
      PsiElement context = position.getParent();
      if (context instanceof PsiErrorElement) context = context.getParent();

      if (context instanceof PsiJavaFile) {
        addFileHeaderKeywords(position, result);
      }
      else if (context instanceof PsiJavaModule) {
        addModuleStatementKeywords(position, result);
      }
      else if (context instanceof PsiJavaModuleReferenceElement) {
        addModuleReferences(position, result);
      }
    }
  }

  private static void addFileHeaderKeywords(PsiElement position, Consumer<LookupElement> result) {
    if (PsiTreeUtil.prevVisibleLeaf(position) == null) {
      result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.MODULE), TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
  }

  private static void addModuleStatementKeywords(PsiElement position, Consumer<LookupElement> result) {
    result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.REQUIRES), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.EXPORTS), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.USES), TailType.HUMBLE_SPACE_BEFORE_WORD));
    result.consume(new OverrideableSpace(createKeyword(position, PsiKeyword.PROVIDES), TailType.HUMBLE_SPACE_BEFORE_WORD));
  }

  private static void addModuleReferences(PsiElement position, Consumer<LookupElement> result) {
    PsiJavaModule host = PsiTreeUtil.getParentOfType(position, PsiJavaModule.class);
    if (host != null) {
      String hostName = host.getModuleName();
      Project project = position.getProject();
      JavaModuleNameIndex index = JavaModuleNameIndex.getInstance();
      GlobalSearchScope scope = ProjectScope.getAllScope(project);
      index.processAllKeys(project, name -> {
        if (!name.equals(hostName) && index.get(name, project, scope).size() == 1) {
          result.consume(new OverrideableSpace(LookupElementBuilder.create(name), TailType.SEMICOLON));
        }
        return true;
      });
    }
  }
}