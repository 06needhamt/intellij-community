package com.intellij.codeInsight.problems;

import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.Disposable;
import com.intellij.pom.Navigatable;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import com.intellij.lang.annotation.HighlightSeverity;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author cdr
 */
public class WolfTheProblemSolverImpl extends WolfTheProblemSolver {
  private final Map<VirtualFile,  ProblemFileInfo> myProblems = new THashMap<VirtualFile, ProblemFileInfo>();
  private final CopyOnWriteArrayList<VirtualFile> myCheckingQueue = new CopyOnWriteArrayList<VirtualFile>();

  private final Project myProject;
  private final List<ProblemListener> myProblemListeners = new CopyOnWriteArrayList<ProblemListener>();
  private final List<Condition<VirtualFile>> myFilters = new CopyOnWriteArrayList<Condition<VirtualFile>>(Arrays.asList(new Condition<VirtualFile>() {
    public boolean value(final VirtualFile object) {
      return ProjectRootManager.getInstance(myProject).getFileIndex().isJavaSourceFile(object);
    }
  }));
  private final ProblemListener fireProblemListeners = new ProblemListener() {
    public void problemsChanged(Collection<VirtualFile> added, Collection<VirtualFile> removed) {
      for (ProblemListener problemListener : myProblemListeners) {
        problemListener.problemsChanged(added, removed);
      }
    }
  };

  private long myPsiModificationCount = -1000;
  private final PsiTreeChangeListener myChangeListener;

  private static class ProblemFileInfo {
    Collection<Problem> problems = new SmartList<Problem>();
    boolean hasSyntaxErrors;
  }

  public WolfTheProblemSolverImpl(Project project, PsiManager psiManager) {
    myProject = project;
    myChangeListener = new PsiTreeChangeAdapter() {
      public void childAdded(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childRemoved(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childReplaced(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childMoved(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void propertyChanged(PsiTreeChangeEvent event) {
        childrenChanged(event);
      }

      public void childrenChanged(PsiTreeChangeEvent event) {
        clearSyntaxErrorFlag(event);
      }
    };
    psiManager.addPsiTreeChangeListener(myChangeListener);
  }

  private void clearSyntaxErrorFlag(final PsiTreeChangeEvent event) {
    PsiFile file = event.getFile();
    if (file == null) return;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    ProblemFileInfo info = myProblems.get(virtualFile);
    if (info == null) return;
    info.hasSyntaxErrors = false;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myChangeListener);
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "Problems";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void startCheckingIfVincentSolvedProblemsYet(final ProgressIndicator progress) {
    if (!myProject.isOpen()) return;
    long psiModificationCount = PsiManager.getInstance(myProject).getModificationTracker().getOutOfCodeBlockModificationCount();
    if (psiModificationCount == myPsiModificationCount) return; //optimization

    try {
      for (VirtualFile virtualFile : myCheckingQueue) {
        if (progress.isCanceled()) break;
        if (virtualFile.isValid()) {
          // place the file to the end of queue to give all files a fair share
          myCheckingQueue.remove(virtualFile);
          myCheckingQueue.add(virtualFile);
          orderVincentToCleanTheCar(virtualFile, progress);
        }
        else {
          synchronized(myProblems) {
            myProblems.remove(virtualFile);
            myCheckingQueue.remove(virtualFile);
          }
        }
      }
      myPsiModificationCount = psiModificationCount;
    }
    catch (ProcessCanceledException e) {
      // ignore
    }
  }

  private static class HaveGotErrorException extends RuntimeException {
    private final HighlightInfo myHighlightInfo;
    private final boolean myHasErrorElement;

    public HaveGotErrorException(HighlightInfo info, final boolean hasErrorElement) {
      myHighlightInfo = info;
      myHasErrorElement = hasErrorElement;
    }
  }

  private void orderVincentToCleanTheCar(final VirtualFile file, ProgressIndicator progressIndicator) throws ProcessCanceledException {
    if (hasSyntaxErrors(file)) {
      // it's no use anyway to try clean the file with syntax errors, only changing the file itself can help
      return;
    }
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (willBeHighlightedAnyway(file)) return;
    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) return;
    if (!myProject.isOpen()) return;

    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    try {
      if (statusBar != null) {
        statusBar.setInfo("Checking '"+file.getPresentableUrl()+"'");
      }

      GeneralHighlightingPass pass = new GeneralHighlightingPass(myProject, psiFile, document, 0, document.getTextLength(), false, true){
        protected HighlightInfoHolder createInfoHolder() {
          final HighlightInfoFilter[] filters = ApplicationManager.getApplication().getComponents(HighlightInfoFilter.class);
          return new HighlightInfoHolder(psiFile, filters){
            public boolean add(HighlightInfo info) {
              if (info != null && info.getSeverity() == HighlightSeverity.ERROR) {
                throw new HaveGotErrorException(info, myHasErrorElement);
              }
              return super.add(info);
            }
          };
        }
      };
      pass.doCollectInformation(progressIndicator);
    }
    catch (HaveGotErrorException e) {
      ProblemImpl problem = new ProblemImpl(file, e.myHighlightInfo, e.myHasErrorElement);
      reportProblems(file, Collections.<Problem>singleton(problem));
    }
    finally {
      if (statusBar != null) {
        statusBar.setInfo("");
      }
    }
  }

  public boolean hasSyntaxErrors(final VirtualFile file) {
    ProblemFileInfo info = myProblems.get(file);
    return info != null && info.hasSyntaxErrors;
  }

  private boolean willBeHighlightedAnyway(final VirtualFile file) {
    // opened in some editor, and hence will be highlighted automatically sometime later
    FileEditor[] selectedEditors = FileEditorManager.getInstance(myProject).getSelectedEditors();
    for (FileEditor editor : selectedEditors) {
      if (!(editor instanceof TextEditor)) continue;
      Document document = ((TextEditor)editor).getEditor().getDocument();
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getCachedPsiFile(document);
      if (psiFile == null) continue;
      if (file == psiFile.getVirtualFile()) return true;
    }
    return false;
  }

  public boolean hasProblemFilesBeneath(final ProjectViewNode scope) {
    return hasProblemFilesBeneath(new Condition<VirtualFile>() {
      public boolean value(final VirtualFile virtualFile) {
        return scope.contains(virtualFile);
      }
    });
  }

  private boolean hasProblemFilesBeneath(Condition<VirtualFile> condition){
    if (!myProject.isOpen()) return false;
    synchronized (myProblems) {
      Set<VirtualFile> problemFiles = myProblems.keySet();
      for (VirtualFile problemFile : problemFiles) {
        if (condition.value(problemFile)) return true;
      }
      return false;
    }
  }

  public boolean hasProblemFilesBeneath(final PsiElement scope) {
    return hasProblemFilesBeneath(new Condition<VirtualFile>(){
      public boolean value(final VirtualFile virtualFile) {
        if (scope instanceof PsiDirectory) {
          final PsiDirectory directory = (PsiDirectory)scope;
          return VfsUtil.isAncestor(directory.getVirtualFile(), virtualFile, false);
        }
        else if (scope instanceof PsiPackage) {
          final PsiDirectory[] psiDirectories = ((PsiPackage)scope).getDirectories();
          for (PsiDirectory directory : psiDirectories) {
            if (VfsUtil.isAncestor(directory.getVirtualFile(), virtualFile, false)) {
              return true;
            }
          }
        }
        return false;
      }
    });
  }

  public boolean hasProblemFilesBeneath(final Module scope) {
    return hasProblemFilesBeneath(new Condition<VirtualFile>() {
      public boolean value(final VirtualFile virtualFile) {
        return PackageUtil.moduleContainsFile(scope, virtualFile, false);
      }
    });
  }

  public void addProblemListener(ProblemListener listener) {
    myProblemListeners.add(listener);
  }

  public void addProblemListener(final ProblemListener listener, Disposable parentDisposable) {
    addProblemListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeProblemListener(listener);
      }
    });
  }

  public void removeProblemListener(ProblemListener listener) {
    myProblemListeners.remove(listener);
  }

  public void registerFileHighlightFilter(final Condition<VirtualFile> filter, Disposable parentDisposable) {
    myFilters.add(filter);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        myFilters.remove(filter);
      }
    });
  }

  public boolean isProblemFile(VirtualFile virtualFile) {
    synchronized (myProblems) {
      return myProblems.containsKey(virtualFile);
    }
  }

  private boolean isToBeHighlighted(VirtualFile virtualFile) {
    if (virtualFile == null) return false;

    for (final Condition<VirtualFile> filter : myFilters) {
      if (filter.value(virtualFile)) {
        return true;
      }
    }

    return false;
  }

  public void weHaveGotProblem(Problem problem) {
    VirtualFile virtualFile = problem == null ? null : problem.getVirtualFile();
    if (!isToBeHighlighted(virtualFile)) return;

    synchronized (myProblems) {
      ProblemFileInfo storedProblems = myProblems.get(virtualFile);
      if (storedProblems == null) {
        storedProblems = new ProblemFileInfo();

        myProblems.put(virtualFile, storedProblems);
        fireProblemListeners.problemsChanged(Collections.singletonList(virtualFile), Collections.<VirtualFile>emptyList());
      }
      storedProblems.problems.add(problem);
      myCheckingQueue.addIfAbsent(virtualFile);
    }
  }

  public void clearProblems(@NotNull VirtualFile virtualFile) {
    synchronized (myProblems) {
      ProblemFileInfo old = myProblems.remove(virtualFile);
      if (old != null) {
        fireProblemListeners.problemsChanged(Collections.<VirtualFile>emptyList(), Collections.singletonList(virtualFile));
      }
      myCheckingQueue.remove(virtualFile);
    }
  }

  public Problem convertToProblem(final CompilerMessage message) {
    VirtualFile virtualFile = message.getVirtualFile();
    if (virtualFile == null) {
      Navigatable navigatable = message.getNavigatable();
      if (navigatable instanceof OpenFileDescriptor) {
        virtualFile = ((OpenFileDescriptor)navigatable).getFile();
      }
    }
    if (virtualFile == null) return null;
    HighlightInfo info = ApplicationManager.getApplication().runReadAction(new Computable<HighlightInfo>(){
      public HighlightInfo compute() {
        return HighlightInfo.createHighlightInfo(convertToHighlightInfoType(message), getTextRange(message), message.getMessage());
      }
    });
    return new ProblemImpl(virtualFile, info, false);
  }

  public Problem convertToProblem(final VirtualFile virtualFile, final int line, final int column, final String[] message) {
    if (virtualFile == null) return null;
    HighlightInfo info = ApplicationManager.getApplication().runReadAction(new Computable<HighlightInfo>(){
      public HighlightInfo compute() {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, getTextRange(virtualFile, line, column), StringUtil.join(message, "\n"));
      }
    });
    return new ProblemImpl(virtualFile, info, false);
  }

  public void reportProblems(final VirtualFile file, Collection<Problem> problems) {
    if (problems.isEmpty()) {
      clearProblems(file);
      return;
    }
    if (!isToBeHighlighted(file)) return;
    synchronized (myProblems) {
      boolean hasProblemsBefore = myProblems.remove(file) != null;
      ProblemFileInfo storedProblems = new ProblemFileInfo();
      myProblems.put(file, storedProblems);
      for (Problem problem : problems) {
        storedProblems.problems.add(problem);
        storedProblems.hasSyntaxErrors |= ((ProblemImpl)problem).isSyntaxOnly();
        myCheckingQueue.addIfAbsent(file);
      }
      if (!hasProblemsBefore) {
        fireProblemListeners.problemsChanged(Collections.singletonList(file), Collections.<VirtualFile>emptyList());
      }
    }
  }

  private static TextRange getTextRange(final VirtualFile virtualFile, final int line, final int column) {
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    int offset = document.getLineStartOffset(line - 1) + column - 1;
    return new TextRange(offset, offset);
  }

  private static TextRange getTextRange(final CompilerMessage message) {
    Navigatable navigatable = message.getNavigatable();
    if (navigatable instanceof OpenFileDescriptor) {
      int offset = ((OpenFileDescriptor)navigatable).getOffset();
      return new TextRange(offset, offset);
    }
    return new TextRange(0,0);
  }

  private static HighlightInfoType convertToHighlightInfoType(final CompilerMessage message) {
    CompilerMessageCategory category = message.getCategory();
    switch(category) {
      case ERROR: return HighlightInfoType.ERROR;
      case WARNING: return HighlightInfoType.WARNING;
      case INFORMATION: return HighlightInfoType.INFORMATION;
      case STATISTICS: return HighlightInfoType.INFORMATION;
    }
    return null;
  }
}
