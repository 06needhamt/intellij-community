package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.RemoveSuppressWarningAction;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class RedundantSuppressInspection extends LocalInspectionTool{
  public String getGroupDisplayName() {
    return GroupNames.GENERAL_GROUP_NAME;
  }

  public String getDisplayName() {
    return InspectionsBundle.message("inspection.redundant.suppression.name");
  }

  @NonNls
  public String getShortName() {
    return "RedundantSuppression";
  }

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    final Map<PsiElement, Collection<String>> suppressedScopes = new THashMap<PsiElement, Collection<String>>();
    file.accept(new PsiRecursiveElementVisitor() {
      public void visitModifierList(PsiModifierList list) {
        super.visitModifierList(list);
        final PsiElement parent = list.getParent();
        if (parent instanceof PsiModifierListOwner) {
          checkElement(parent);
        }
      }

      public void visitComment(PsiComment comment) {
        checkElement(comment);
      }

      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);
        checkElement(method);
      }

      public void visitClass(PsiClass aClass) {
        super.visitClass(aClass);
        checkElement(aClass);
      }

      public void visitField(PsiField field) {
        super.visitField(field);
        checkElement(field);
      }

      private void checkElement(final PsiElement owner) {
        String idsString = InspectionManagerEx.getSuppressedInspectionIdsIn(owner);
        if (idsString != null && idsString.length() != 0) {
          List<String> ids = StringUtil.split(idsString, ",");
          Collection<String> suppressed = suppressedScopes.get(owner);
          if (suppressed == null) {
            suppressed = ids;
          }
          else {
            suppressed.addAll(ids);
          }
          suppressedScopes.put(owner, suppressed);
        }
      }
    });

    if (suppressedScopes.values().size() == 0) return null;
    // have to visit all file from scratch since inspections can be written in any perversive way including checkFile() overriding
    InspectionProfileWrapper profile = InspectionProjectProfileManager.getInstance(manager.getProject()).getProfileWrapper(file);
    Collection<InspectionTool> suppressedTools = new THashSet<InspectionTool>();

    for (Collection<String> ids : suppressedScopes.values()) {
      for (String id : ids) {
        InspectionTool tool = profile.getInspectionTool(id.trim());
        if (tool != null) {
          suppressedTools.add(tool);
        }
      }
    }

    ((RefManagerImpl)((InspectionManagerEx)manager).getRefManager()).inspectionReadActionStarted();

    final List<ProblemDescriptor> result = new ArrayList<ProblemDescriptor>();
    for (InspectionTool tool : suppressedTools) {
      tool.initialize((InspectionManagerEx)manager);
      tool.cleanup();
      String toolId = tool.getShortName();
      Collection<CommonProblemDescriptor> descriptors;
      if (tool instanceof LocalInspectionToolWrapper) {
        LocalInspectionToolWrapper local = (LocalInspectionToolWrapper)tool;
        local.processFile(file, false);
        descriptors = local.getProblemDescriptors();
      }
      else if (tool instanceof GlobalInspectionToolWrapper) {
        GlobalInspectionToolWrapper global = (GlobalInspectionToolWrapper)tool;
        global.processFile(new AnalysisScope(file), manager, (GlobalInspectionContext)manager, false);
        descriptors = global.getProblemDescriptors();
      }
      else {
        continue;
      }
      for (PsiElement suppressedScope : suppressedScopes.keySet()) {
        Collection<String> suppressedIds = suppressedScopes.get(suppressedScope);
        if (!suppressedIds.contains(toolId)) continue;
        boolean hasErrorInsideSuppressedScope = false;
        for (CommonProblemDescriptor descriptor : descriptors) {
          if (!(descriptor instanceof ProblemDescriptor)) continue;
          PsiElement element = ((ProblemDescriptor)descriptor).getPsiElement();
          if (element == null) continue;
          PsiElement annotation = InspectionManagerEx.getElementToolSuppressedIn(element, toolId);
          if (annotation != null && PsiTreeUtil.isAncestor(suppressedScope, annotation, false)) {
            hasErrorInsideSuppressedScope = true;
            break;
          }
        }
        if (!hasErrorInsideSuppressedScope) {
          PsiElement element = suppressedScope instanceof PsiComment
                               ? PsiTreeUtil.skipSiblingsForward(suppressedScope, PsiWhiteSpace.class)
                               : suppressedScope.getFirstChild();
          PsiElement annotation = InspectionManagerEx.getElementToolSuppressedIn(element, toolId);
          if (annotation != null && annotation.isValid()) {
            String description = InspectionsBundle.message("inspection.redundant.suppression.description");
            LocalQuickFix fix = new RemoveSuppressWarningAction(toolId, annotation);
            ProblemDescriptor descriptor = manager.createProblemDescriptor(annotation, description, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            result.add(descriptor);
          }
        }
      }
    }
    return result.toArray(new ProblemDescriptor[result.size()]);
  }
}
