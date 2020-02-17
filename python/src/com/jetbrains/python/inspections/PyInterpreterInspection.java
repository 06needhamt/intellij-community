// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonIdeLanguageCustomization;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PySdkPopupFactory;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.pipenv.PipenvKt;
import com.jetbrains.python.sdk.pipenv.UsePipEnvQuickFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PyInterpreterInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(@Nullable ProblemsHolder holder,
                   @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFile(PyFile node) {
      Module module = guessModule(node);
      if (module == null || isFileIgnored(node)) return;
      final Sdk sdk = PythonSdkUtil.findPythonSdk(module);

      final boolean pyCharm = PythonIdeLanguageCustomization.isMainlyPythonIde();

      final String interpreterOwner = pyCharm ? "project" : "module";
      final List<LocalQuickFix> fixes = new ArrayList<>();
      // TODO: Introduce an inspection extension
      if (UsePipEnvQuickFix.Companion.isApplicable(module)) {
        fixes.add(new UsePipEnvQuickFix(sdk, module));
      }
      if (pyCharm) {
        fixes.add(new ConfigureInterpreterFix());
      }

      final String product = pyCharm ? "PyCharm" : "Python plugin";

      if (sdk == null) {
        registerProblem(node, "No Python interpreter configured for the " + interpreterOwner, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
      else {
        final Module associatedModule = PySdkExtKt.getAssociatedModule(sdk);
        final String associatedName = associatedModule != null ? associatedModule.getName() : PySdkExtKt.getAssociatedModulePath(sdk);
        // TODO: Introduce an inspection extension
        if (PipenvKt.isPipEnv(sdk) && associatedModule != module) {
          final String message = associatedName != null ?
                                 "Pipenv interpreter is associated with another " + interpreterOwner + ": '" + associatedName + "'" :
                                 "Pipenv interpreter is not associated with any " + interpreterOwner;
          registerProblem(node, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
        else if (PythonSdkUtil.isInvalid(sdk)) {
          registerProblem(node, "Invalid Python interpreter selected for the " + interpreterOwner, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }
        else {
          final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
          if (!LanguageLevel.SUPPORTED_LEVELS.contains(languageLevel)) {
            registerProblem(
              node,
              "Python " + languageLevel + " has reached its end-of-life date and it is no longer supported in " + product + ".",
              fixes.toArray(LocalQuickFix.EMPTY_ARRAY)
            );
          }
        }
      }
    }
  }

  @Nullable
  private static Module guessModule(@NotNull PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      Module[] modules = ModuleManager.getInstance(element.getProject()).getModules();
      if (modules.length != 1) {
        return null;
      }
      module = modules[0];
    }
    return module;
  }

  private static boolean isFileIgnored(@NotNull PyFile pyFile) {
    return PyInspectionExtension.EP_NAME.getExtensionList().stream().anyMatch(ep -> ep.ignoreInterpreterWarnings(pyFile));
  }

  public static final class InterpreterSettingsQuickFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return PyBundle.message("INSP.interpreter.interpreter.settings");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      showProjectInterpreterDialog(project);
    }

    /**
     * It is only applicable to PyCharm, not Python plugin.
     */
    public static void showProjectInterpreterDialog(@NotNull Project project) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, "Project Interpreter");
    }
  }

  public static final class ConfigureInterpreterFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return PyBundle.message("INSP.interpreter.configure.python.interpreter");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element == null) return;

      final Module module = guessModule(element);
      if (module == null) return;

      PySdkPopupFactory.Companion.createAndShow(project, module);
    }
  }
}
