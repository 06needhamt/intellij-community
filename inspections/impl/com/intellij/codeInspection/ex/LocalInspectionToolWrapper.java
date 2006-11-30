package com.intellij.codeInspection.ex;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author max
 */
public final class LocalInspectionToolWrapper extends DescriptorProviderInspection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.LocalInspectionToolWrapper");

  @NotNull private final LocalInspectionTool myTool;

  public LocalInspectionToolWrapper(@NotNull LocalInspectionTool tool) {
    myTool = tool;
  }

  @NotNull public LocalInspectionTool getTool() {
    return myTool;
  }

  public void processFile(PsiFile file, final boolean filterSuppressed, final InspectionManager manager) {
    final ProblemsHolder holder = new ProblemsHolder(manager);
    final PsiElementVisitor customVisitor = myTool.buildVisitor(holder, false);
    LOG.assertTrue(!(customVisitor instanceof PsiRecursiveElementVisitor), "The visitor returned from LocalInspectionTool.buildVisitor() must not be recursive");

    file.accept(new PsiRecursiveElementVisitor() {
      public void visitElement(PsiElement element) {
        element.accept(customVisitor);
        super.visitElement(element);
      }

      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }
    });

    addProblemDescriptors(holder.getResults(), filterSuppressed);
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return JobDescriptor.EMPTY_ARRAY;
  }

  private void addProblemDescriptors(List<ProblemDescriptor> descriptors, final boolean filterSuppressed) {
    if (descriptors == null || descriptors.isEmpty()) return;

    Map<RefElement, List<ProblemDescriptor>> problems = new HashMap<RefElement, List<ProblemDescriptor>>();
    final RefManagerImpl refManager = (RefManagerImpl)getContext().getRefManager();
    for (ProblemDescriptor descriptor : descriptors) {
      final PsiElement elt = descriptor.getPsiElement();
      if (elt == null) continue;
      if (filterSuppressed) {
        if (elt instanceof PsiModifierListOwner && refManager.isDeclarationsFound() && getContext().isSuppressed(elt, myTool.getID())) {
          continue;
        }
        if (InspectionManagerEx.inspectionResultSuppressed(elt, myTool)) continue;
      }

      final PsiNamedElement problemElement =
        PsiTreeUtil.getNonStrictParentOfType(elt, PsiFile.class, PsiClass.class, PsiMethod.class, PsiField.class);

      RefElement refElement = refManager.getReference(problemElement);
      List<ProblemDescriptor> elementProblems = problems.get(refElement);
      if (elementProblems == null) {
        elementProblems = new ArrayList<ProblemDescriptor>();
        problems.put(refElement, elementProblems);
      }
      elementProblems.add(descriptor);
    }

    for (Map.Entry<RefElement, List<ProblemDescriptor>> entry : problems.entrySet()) {
      final List<ProblemDescriptor> problemDescriptors = entry.getValue();
      addProblemElement(entry.getKey(),
                        problemDescriptors.toArray(new CommonProblemDescriptor[problemDescriptors.size()]));
    }
  }

  private ProblemDescriptor[] filterUnsuppressedProblemDescriptions(ProblemDescriptor[] problemDescriptions) {
    Set<ProblemDescriptor> set = null;
    for (ProblemDescriptor description : problemDescriptions) {
      final PsiElement element = description.getPsiElement();
      if ((element instanceof PsiModifierListOwner && getContext().isSuppressed(element, myTool.getID())) || InspectionManagerEx.inspectionResultSuppressed(element, myTool)) {
        if (set == null) set = new LinkedHashSet<ProblemDescriptor>(Arrays.asList(problemDescriptions));
        set.remove(description);
      }
    }
    return set == null ? problemDescriptions : set.toArray(new ProblemDescriptor[set.size()]);
  }

  private void addProblemDescriptors(PsiElement element, ProblemDescriptor[] problemDescriptions, final boolean filterSuppressed) {
    if (problemDescriptions != null) {
      if (filterSuppressed) {
        problemDescriptions = filterUnsuppressedProblemDescriptions(problemDescriptions);
      }
      if (problemDescriptions.length != 0) {
        RefManager refManager = getContext().getRefManager();
        RefElement refElement = refManager.getReference(element);
        if (refElement != null) {
          addProblemElement(refElement, problemDescriptions);
        }
      }
    }
  }

  public void runInspection(AnalysisScope scope, final InspectionManager manager) {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode());
    scope.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
      }

      public void visitFile(PsiFile file) {
        processFile(file, true, manager);
      }
    });
  }

  public boolean isGraphNeeded() {
    return false;
  }

  @NotNull
  public String getDisplayName() {
    return myTool.getDisplayName();
  }

  @NotNull
  public String getGroupDisplayName() {
    return myTool.getGroupDisplayName();
  }

  @NotNull
  public String getShortName() {
    return myTool.getShortName();
  }

  public boolean isEnabledByDefault() {
    return myTool.isEnabledByDefault();
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return myTool.getDefaultLevel();
  }

  public void readSettings(Element element) throws InvalidDataException {
    myTool.readSettings(element);
  }

  public void writeSettings(Element element) throws WriteExternalException {
    myTool.writeSettings(element);
  }

  public JComponent createOptionsPanel() {
    return myTool.createOptionsPanel();    
  }

  public void projectOpened(Project project) {
    myTool.projectOpened(project);
  }

  public void projectClosed(Project project) {
    myTool.projectClosed(project);
  }
}
