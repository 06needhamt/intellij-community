package com.intellij.lang.ant.validation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AntDuplicateTargetsInspection extends AntInspection {

  @NonNls private static final String SHORT_NAME = "AntDuplicateTargetsInspection";

  @Nls
  @NotNull
  public String getDisplayName() {
    return AntBundle.message("ant.duplicate.targets.inspection");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof AntFile) {
      final AntProject project = ((AntFile)file).getAntProject();
      if (project != null) {
        final AntTarget[] targets = project.getTargets();
        if (targets.length > 0) {
          final HashMap<String, AntTarget> name2Target = new HashMap<String, AntTarget>();
          final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
          for (final AntTarget target : targets) {
            final String name = target.getName();
            final AntTarget t = name2Target.get(name);
            if (t != null) {
              final String duplicatedMessage = AntBundle.message("target.is.duplicated", name);
              problems.add(
                manager.createProblemDescriptor(target, duplicatedMessage, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
              problems
                .add(manager.createProblemDescriptor(t, duplicatedMessage, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
            name2Target.put(name, target);
          }
          final int prolemCount = problems.size();
          if (prolemCount > 0) {
            return problems.toArray(new ProblemDescriptor[prolemCount]);
          }
        }
      }
    }
    return null;
  }
}
