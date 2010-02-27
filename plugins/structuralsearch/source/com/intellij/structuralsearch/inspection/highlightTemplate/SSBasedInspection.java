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
package com.intellij.structuralsearch.inspection.highlightTemplate;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.impl.matcher.MatcherImpl;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.Replacer;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.util.PairProcessor;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
    
/**
 * @author cdr
 */
public class SSBasedInspection extends BaseJavaLocalInspectionTool {
  private List<Configuration> myConfigurations = new ArrayList<Configuration>();
  private MatcherImpl.CompiledOptions compiledConfigurations;

  public void writeSettings(Element node) throws WriteExternalException {
    ConfigurationManager.writeConfigurations(node, myConfigurations, Collections.<Configuration>emptyList());
  }

  public void readSettings(Element node) throws InvalidDataException {
    myConfigurations.clear();
    ConfigurationManager.readConfigurations(node, myConfigurations, new ArrayList<Configuration>());
  }

  @NotNull
  public String getGroupDisplayName() {
    return GENERAL_GROUP_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return SSRBundle.message("SSRInspection.display.name");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "SSBasedInspection";
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, final boolean isOnTheFly) {
    final Project project = file.getProject();
    if (compiledConfigurations == null) return null;
    final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
    try {
      new Matcher(project).processMatchesInFile(compiledConfigurations, file, new PairProcessor<MatchResult, Configuration>() {
        public boolean process(MatchResult matchResult, Configuration configuration) {
          PsiElement element = matchResult.getMatch();
          String name = configuration.getName();
          LocalQuickFix fix = createQuickFix(project, matchResult, configuration);
          ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(element, name, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                                isOnTheFly);
          problems.add(problemDescriptor);
          return true;
        }
      });
      return problems.toArray(new ProblemDescriptor[problems.size()]);
    }
    catch (StackOverflowError e) {
      return null;
    }
  }

  private static LocalQuickFix createQuickFix(final Project project, final MatchResult matchResult, final Configuration configuration) {
    if (!(configuration instanceof ReplaceConfiguration)) return null;
    ReplaceConfiguration replaceConfiguration = (ReplaceConfiguration)configuration;
    final Replacer replacer = new Replacer(project, replaceConfiguration.getOptions());
    final ReplacementInfo replacementInfo = replacer.buildReplacement(matchResult);

    return new LocalQuickFix() {
      @NotNull
      public String getName() {
        return SSRBundle.message("SSRInspection.replace.with", replacementInfo.getReplacement());
      }

      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element != null && CodeInsightUtilBase.preparePsiElementsForWrite(element)) {
          replacer.replace(replacementInfo);
        }
      }

      @NotNull
      public String getFamilyName() {
        return SSRBundle.message("SSRInspection.family.name");
      }
    };
  }

  @Nullable
  public JComponent createOptionsPanel() {
    return new SSBasedInspectionOptions(myConfigurations){
      public void configurationsChanged(final SearchContext searchContext) {
        super.configurationsChanged(searchContext);
        precompileConfigurations(searchContext.getProject());
      }
    }.getComponent();
  }

  private void precompileConfigurations(final Project project) {
    if (compiledConfigurations == null) {
      final Runnable precompile = new Runnable() {
        public void run() {
          if (!project.isDisposed()) {
            Matcher matcher = new Matcher(project);
            compiledConfigurations = matcher.precompileOptions(myConfigurations);
            InspectionProfileManager.getInstance().fireProfileChanged(null);
          }
        }
      };
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        precompile.run();
      } else {
        DumbService.getInstance(project).smartInvokeLater(precompile, ModalityState.NON_MODAL);
      }
    }
  }

  public void projectOpened(final Project project) {
    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        precompileConfigurations(project);
      }
    });
  }

  @TestOnly
  public void setConfigurations(final List<Configuration> configurations, final Project project) {
    myConfigurations = configurations;
    Matcher matcher = new Matcher(project);
    compiledConfigurations = matcher.precompileOptions(myConfigurations);
  }
}
