package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.*;
import com.intellij.codeInspection.util.XMLExportUtl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jdom.IllegalDataException;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author max
 */
public abstract class DescriptorProviderInspection extends InspectionTool implements ProblemDescriptionsProcessor {
  private HashMap<RefEntity, CommonProblemDescriptor[]> myProblemElements;
  private HashMap<String, Set<RefElement>> myPackageContents = null;
  private HashSet<RefModule> myModulesProblems = null;
  private HashMap<CommonProblemDescriptor,RefEntity> myProblemToElements;
  private DescriptorComposer myComposer;
  private HashMap<RefEntity, Set<QuickFix>> myQuickFixActions;
  private HashMap<RefEntity, CommonProblemDescriptor[]> myIgnoredElements;

  private HashMap<RefEntity, CommonProblemDescriptor[]> myOldProblemElements = null;

  protected DescriptorProviderInspection() {
    myProblemElements = new HashMap<RefEntity, CommonProblemDescriptor[]>();
    myProblemToElements = new HashMap<CommonProblemDescriptor, RefEntity>();
    myQuickFixActions = new HashMap<RefEntity, Set<QuickFix>>();
    myIgnoredElements = new HashMap<RefEntity, CommonProblemDescriptor[]>();
  }

  public void addProblemElement(RefEntity refElement, CommonProblemDescriptor[] descriptions) {
    if (refElement == null) return;
    if (descriptions == null || descriptions.length == 0) return;
    myProblemElements.put(refElement, descriptions);
    for (CommonProblemDescriptor description : descriptions) {
      myProblemToElements.put(description, refElement);
      collectQuickFixes(description.getFixes(), refElement);
    }
  }

  public Collection<CommonProblemDescriptor> getProblemDescriptors() {
    return myProblemToElements.keySet();
  }

  private void collectQuickFixes(final QuickFix[] fixes, final RefEntity refEntity) {
    if (fixes != null) {
      Set<QuickFix> localQuickFixes = myQuickFixActions.get(refEntity);
      if (localQuickFixes == null) {
        localQuickFixes = new HashSet<QuickFix>();
        myQuickFixActions.put(refEntity, localQuickFixes);
      }
      localQuickFixes.addAll(Arrays.asList(fixes));
    }
  }

  public void ignoreElement(RefEntity refEntity) {
    if (refEntity == null) return;
    ignoreProblemElement(refEntity);
    myQuickFixActions.remove(refEntity);
  }

  public void ignoreProblem(ProblemDescriptor problem) {
    RefEntity refElement = myProblemToElements.get(problem);
    if (refElement != null) ignoreProblem(refElement, problem, -1);
  }

  public void ignoreProblem(RefEntity refEntity, CommonProblemDescriptor problem, int idx) {
    if (refEntity == null) return;
    final Set<QuickFix> localQuickFixes = myQuickFixActions.get(refEntity);
    final QuickFix[] fixes = problem.getFixes();
    if (isIgnoreProblem(fixes, localQuickFixes, idx)){
      myProblemToElements.remove(problem);
      CommonProblemDescriptor[] descriptors = myProblemElements.get(refEntity);
      if (descriptors != null) {
        ArrayList<CommonProblemDescriptor> newDescriptors = new ArrayList<CommonProblemDescriptor>(Arrays.asList(descriptors));
        newDescriptors.remove(problem);
        myQuickFixActions.put(refEntity, null);
        if (newDescriptors.size() > 0) {
          myProblemElements.put(refEntity, newDescriptors.toArray(new ProblemDescriptor[newDescriptors.size()]));
          for (CommonProblemDescriptor descriptor : newDescriptors) {
            collectQuickFixes(descriptor.getFixes(), refEntity);
          }
        }
        else {
          ignoreProblemElement(refEntity);
        }
      }
    }
  }

  private void ignoreProblemElement(RefEntity refEntity){
    final CommonProblemDescriptor[] problemDescriptors = myProblemElements.remove(refEntity);
    myIgnoredElements.put(refEntity, problemDescriptors);
  }

  private static boolean isIgnoreProblem(QuickFix[] problemFixes, Set<QuickFix> fixes, int idx){
    if (problemFixes == null || fixes == null) {
      return true;
    }
    if (problemFixes.length <= idx){
      return true;
    }
    for (QuickFix fix : problemFixes) {
      if (fix != problemFixes[idx] && !fixes.contains(fix)){
        return false;
      }
    }
    return true;
  }

  public void cleanup() {
    super.cleanup();
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldProblemElements == null) {
        myOldProblemElements = new HashMap<RefEntity, CommonProblemDescriptor[]>();
      }
      myOldProblemElements.clear();
      myOldProblemElements.putAll(myIgnoredElements);
      myOldProblemElements.putAll(myProblemElements);
    } else {
      myOldProblemElements = null;
    }
    myProblemElements.clear();
    myProblemToElements.clear();
    myQuickFixActions.clear();
    myIgnoredElements.clear();
    myPackageContents = null;
    myModulesProblems = null;
  }


  public void finalCleanup() {
    super.finalCleanup();
    myOldProblemElements = null;
  }

  public CommonProblemDescriptor[] getDescriptions(RefEntity refEntity) {
    if (refEntity instanceof RefElement && !((RefElement)refEntity).isValid()) {
      ignoreElement(refEntity);
      return null;
    }

    return myProblemElements.get(refEntity);
  }

  public HTMLComposer getComposer() {
    if (myComposer == null) {
      myComposer = new DescriptorComposer(this);
    }
    return myComposer;
  }

  public void exportResults(final Element parentNode) {
    getRefManager().iterate(new RefVisitor() {
      public void visitElement(final RefEntity refEntity) {
        if (myProblemElements.containsKey(refEntity)) {
          CommonProblemDescriptor[] descriptions = getDescriptions(refEntity);
          for (CommonProblemDescriptor description : descriptions) {
            @NonNls final String template = description.getDescriptionTemplate();
            int line = description instanceof ProblemDescriptor ? ((ProblemDescriptor)description).getLineNumber() : -1;
            final String text = description instanceof ProblemDescriptor ? ((ProblemDescriptor)description).getPsiElement().getText() : "";
            @NonNls String problemText = template.replaceAll("#ref", text.replaceAll("\\$", "\\\\\\$"));
            problemText = problemText.replaceAll(" #loc ", " ");

            Element element = XMLExportUtl.createElement(refEntity, parentNode, line);
            Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));
            problemClassElement.addContent(getDisplayName());
            element.addContent(problemClassElement);
            try {
              Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
              descriptionElement.addContent(problemText);
              element.addContent(descriptionElement);
            }
            catch (IllegalDataException e) {
              //noinspection HardCodedStringLiteral,UseOfSystemOutOrSystemErr
              System.out.println("Cannot save results for "
                                 + refEntity.getName());
            }
          }
        }
      }
    });
  }

  public boolean isGraphNeeded() {
    return false;
  }

  public boolean hasReportedProblems() {
    final boolean hasProblems = myProblemElements.size() > 0;
    if (hasProblems) return true;
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      return myOldProblemElements != null && myOldProblemElements.size() > 0;
    }
    return hasProblems;
  }

  public void updateContent() {
    myPackageContents = new HashMap<String, Set<RefElement>>();
    myModulesProblems = new HashSet<RefModule>();
    final Set<RefEntity> elements = myProblemElements.keySet();
    for (RefEntity element : elements) {
      if (element instanceof RefElement) {
        String packageName = RefUtil.getInstance().getPackageName(element);
        Set<RefElement> content = myPackageContents.get(packageName);
        if (content == null) {
          content = new HashSet<RefElement>();
          myPackageContents.put(packageName, content);
        }
        content.add((RefElement)element);
      } else if (element instanceof RefModule){
        myModulesProblems.add((RefModule)element);
      }
    }
  }

  public InspectionTreeNode[] getContents() {
    List<InspectionTreeNode> content = new ArrayList<InspectionTreeNode>();
    buildTreeNode(content, myPackageContents, myProblemElements);
    if (isOldProblemsIncluded(getContext())){
      HashMap<String, Set<RefElement>> oldContents = new HashMap<String, Set<RefElement>>();
      final Set<RefEntity> elements = myOldProblemElements.keySet();
      for (RefEntity element : elements) {
        if (element instanceof RefElement) {
          String packageName = RefUtil.getInstance().getPackageName(element);
          final Set<RefElement> collection = myPackageContents.get(packageName);
          if (collection != null){
            final Set<RefEntity> currentElements = new HashSet<RefEntity>(collection);
            if (contains((RefElement)element, currentElements)) continue;
          }
          Set<RefElement> oldContent = oldContents.get(packageName);
          if (oldContent == null) {
            oldContent = new HashSet<RefElement>();
            oldContents.put(packageName, oldContent);
          }
          oldContent.add((RefElement)element);
        }
      }
      buildTreeNode(content, oldContents, myOldProblemElements);
    }

    for (RefModule refModule : myModulesProblems) {
      InspectionModuleNode moduleNode = new InspectionModuleNode(refModule.getModule());
      final CommonProblemDescriptor[] problems = myProblemElements.get(refModule);
      for (CommonProblemDescriptor problem : problems) {
        moduleNode.add(new ProblemDescriptionNode(refModule, problem, !(this instanceof DuplicatePropertyInspection), this));
      }
      content.add(moduleNode);
    }
    return content.toArray(new InspectionTreeNode[content.size()]);
  }

  private boolean isOldProblemsIncluded(final GlobalInspectionContextImpl context) {
    return context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN && myOldProblemElements != null;
  }

  private void buildTreeNode(final List<InspectionTreeNode> content,
                             final HashMap<String, Set<RefElement>> packageContents,
                             final HashMap<RefEntity, CommonProblemDescriptor[]> problemElements) {
    Set<String> packages = packageContents.keySet();
    for (String p : packages) {
      InspectionPackageNode pNode = new InspectionPackageNode(p);
      Set<RefElement> elements = packageContents.get(p);
      for (RefElement refElement : elements) {
        final RefElementNode elemNode = addNodeToParent(refElement, pNode);
        final CommonProblemDescriptor[] problems = problemElements.get(refElement);
        if (problems != null) {
          for (CommonProblemDescriptor problem : problems) {
            elemNode.add(new ProblemDescriptionNode(refElement, problem, !(this instanceof DuplicatePropertyInspection), this));
          }
          if (problems.length == 1){
            elemNode.setProblem(problems[0]);
          }
        }
      }
      content.add(pNode);
    }
  }

  public Map<String, Set<RefElement>> getPackageContent() {
    return myPackageContents;
  }

  public Set<RefModule> getModuleProblems() {
    return myModulesProblems;
  }

  public QuickFixAction[] getQuickFixes(final RefEntity[] refElements) {
    if (refElements == null) return null;
    Map<Class, QuickFixAction> result = new java.util.HashMap<Class, QuickFixAction>();
    for (RefEntity refElement : refElements) {
      final Set<QuickFix> localQuickFixes = myQuickFixActions.get(refElement);
      if (localQuickFixes != null){
        for (QuickFix fix : localQuickFixes) {
          final Class klass = fix.getClass();
          final QuickFixAction quickFixAction = result.get(klass);
          if (quickFixAction != null){
            try {
              String familyName = fix.getFamilyName();
              familyName = familyName != null && familyName.length() > 0 ? "\'" + familyName + "\'" : familyName;
              ((LocalQuickFixWrapper)quickFixAction).setText(InspectionsBundle.message("inspection.descriptor.provider.apply.fix", familyName));
            }
            catch (AbstractMethodError e) {
              //for plugin compatibility
              ((LocalQuickFixWrapper)quickFixAction).setText(InspectionsBundle.message("inspection.descriptor.provider.apply.fix", ""));
            }
          } else {
            LocalQuickFixWrapper quickFixWrapper = new LocalQuickFixWrapper(fix, this);
            result.put(fix.getClass(), quickFixWrapper);
          }
        }
      }
    }
    return result.values().isEmpty() ? null : result.values().toArray(new QuickFixAction[result.size()]);
  }

  protected RefEntity getElement(CommonProblemDescriptor descriptor) {
    return myProblemToElements.get(descriptor);
  }

  public void ignoreProblem(final CommonProblemDescriptor descriptor, final QuickFix fix) {
    RefEntity refElement = myProblemToElements.get(descriptor);
    if (refElement != null) {
      final QuickFix[] fixes = descriptor.getFixes();
      for (int i = 0; i < fixes.length; i++) {
        if (fixes[i] == fix){
          ignoreProblem(refElement, descriptor, i);
          return;
        }
      }
    }
  }


  public boolean isElementIgnored(final RefElement element) {
    if (myIgnoredElements == null) return false;
    for (RefEntity entity : myIgnoredElements.keySet()) {
      if (entity instanceof RefElement){
        final RefElement refElement = (RefElement)entity;
        if (Comparing.equal(refElement.getElement(), element.getElement())){
          return true;
        }
      }
    }
    return false;
  }

  public FileStatus getProblemStatus(final CommonProblemDescriptor descriptor) {
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldProblemElements != null){
        final Set<CommonProblemDescriptor> allAvailable = new HashSet<CommonProblemDescriptor>();
        for (CommonProblemDescriptor[] descriptors : myOldProblemElements.values()) {
          if (descriptors != null) {
            allAvailable.addAll(Arrays.asList(descriptors));
          }
        }
        final boolean old = contains(descriptor, allAvailable);
        final boolean current = contains(descriptor, myProblemToElements.keySet());
        return calcStatus(old, current);
      }
    }
    return FileStatus.NOT_CHANGED;
  }

  private static boolean contains(CommonProblemDescriptor descriptor, Collection<CommonProblemDescriptor> descriptors){
    for (CommonProblemDescriptor problemDescriptor : descriptors) {
      if (Comparing.strEqual(problemDescriptor.getDescriptionTemplate(), descriptor.getDescriptionTemplate())){
        return true;
      }
    }
    return false;
  }


  public FileStatus getElementStatus(final RefElement element) {
    final GlobalInspectionContextImpl context = getContext();
    if (context != null && context.getUIOptions().SHOW_DIFF_WITH_PREVIOUS_RUN){
      if (myOldProblemElements != null){
        final boolean old = contains(element, myOldProblemElements.keySet());
        final boolean current = contains(element, myProblemElements.keySet());
        return calcStatus(old, current);
      }
    }
    return FileStatus.NOT_CHANGED;
  }

}
