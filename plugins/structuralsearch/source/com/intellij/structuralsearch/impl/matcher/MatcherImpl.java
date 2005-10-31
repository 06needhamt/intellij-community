package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectRootType;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.FileIndexImplUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.impl.matcher.iterators.ArrayBackedNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;
import com.intellij.structuralsearch.plugin.util.CollectingMatchResultSink;
import com.intellij.util.IncorrectOperationException;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;

/**
 * This class makes program structure tree matching:
 */
public class MatcherImpl {
  // project being worked on
  private Project project;

  // context of matching
  private MatchContext matchContext;
  private boolean isTesting;

  // visitor to delegate the real work
  private MatchingVisitor visitor = new MatchingVisitor();
  private ProgressIndicator progress;
  private TaskScheduler scheduler = new TaskScheduler();

  private int totalFilesToScan;
  private int scannedFilesCount;
  private static CompiledPattern lastPattern;
  private static MatchOptions lastOptions;

  protected MatcherImpl(Project _project) {
    project = _project;
    matchContext = new MatchContext();
    matchContext.setMatcher(visitor);
  }

  public static void validate(Project project, MatchOptions _options) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    synchronized(MatcherImpl.class) {
      lastPattern =  PatternCompiler.compilePattern(project,_options);
      lastOptions = _options;
    }
    
    class ValidatingVisitor extends PsiRecursiveElementVisitor {
      public void visitAnnotation(PsiAnnotation annotation) {
        final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
        
        if (nameReferenceElement == null || 
            !nameReferenceElement.getText().equals(MatchOptions.MODIFIER_ANNOTATION_NAME)) {
          return;
        }
        
        for(PsiNameValuePair pair:annotation.getParameterList().getAttributes()) {
          final PsiAnnotationMemberValue value = pair.getValue();
  
          if (value instanceof PsiArrayInitializerMemberValue) {
            for(PsiAnnotationMemberValue v:((PsiArrayInitializerMemberValue)value).getInitializers()) {
              final String name = StringUtil.stripQuotesAroundValue(v.getText());
              checkModifier(name);
            }
  
          } else {
            final String name = StringUtil.stripQuotesAroundValue(value.getText());
            checkModifier(name);
          }
        }
      }

      private void checkModifier(final String name) {
        if (!MatchOptions.INSTANCE_MODIFIER_NAME.equals(name) &&
            !MatchOptions.PACKAGE_LOCAL_MODIFIER_NAME.equals(name) &&
            Arrays.binarySearch(MatchingVisitor.MODIFIERS, name) < 0
           ) {
          throw new MalformedPatternException(SSRBundle.message("invalid.modifier.type",name));
        }
      }
    };
    
    ValidatingVisitor visitor = new ValidatingVisitor();
    final NodeIterator nodes = lastPattern.getNodes();
    while(nodes.hasNext()) {
      nodes.current().accept( visitor );
      nodes.advance();
    }
    nodes.reset();
  }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @throws com.intellij.structuralsearch.MalformedPatternException
   * @throws com.intellij.structuralsearch.UnsupportedPatternException
   */
  protected void findMatches(MatchResultSink sink, final MatchOptions _options) throws MalformedPatternException, UnsupportedPatternException
  {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    matchContext.clear();
    matchContext.setSink(
      new MatchConstraintsSink(
        sink,
        _options.getMaxMatchesCount(),
        _options.isDistinct(),
        _options.isCaseSensitiveMatch()
      )
    );
    matchContext.setOptions(_options);
    matchContext.setMatcher(visitor);

    CompiledPattern compiledPattern = null;

    synchronized(getClass()) {
      if (_options==lastOptions) {
        compiledPattern = lastPattern;
      }
      lastOptions = null;
      lastPattern = null;
    }

    if (compiledPattern==null) {
      compiledPattern =  PatternCompiler.compilePattern(project,_options);
    }

    if (compiledPattern!=null) {
      matchContext.setPattern(compiledPattern);
      matchContext.getSink().setMatchingProcess( scheduler );
      scheduler.init();
      progress = matchContext.getSink().getProgressIndicator();
      visitor.setMatchContext(matchContext);

      if(isTesting) {
        // testing mode;
        final PsiElement[] elements = ((LocalSearchScope)_options.getScope()).getScope();

        for (PsiElement element : elements) {
          match(element);
        }

        matchContext.getSink().matchingFinished();
        return;
      }

      SearchScope searchScope = compiledPattern.getScope();
      if (searchScope==null) searchScope = _options.getScope();

      if (searchScope instanceof GlobalSearchScope) {
        final GlobalSearchScope scope = (GlobalSearchScope)searchScope;
        final ContentIterator ci = new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            if (!fileOrDir.isDirectory()) {
              final PsiFile file = PsiManager.getInstance(project).findFile(fileOrDir);

              if ((_options.getFileType() == StdFileTypes.JAVA && file instanceof PsiJavaFile) ||
                  (_options.getFileType() != StdFileTypes.JAVA && file instanceof XmlFile)
                 ) {
                final PsiFile[] psiRoots = file.getPsiRoots();

                for(PsiFile root:psiRoots) {
                  ++totalFilesToScan;
                  scheduler.addOneTask( new MatchOneFile(root) );
                }
              }
            }
            return true;
          }
        };

        final ProjectRootManager instance = ProjectRootManager.getInstance(project);
        ProjectFileIndex projectFileIndex = instance.getFileIndex();

        final VirtualFile[] rootFiles = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile[]>() {
          public VirtualFile[] compute() {
            return (_options.getFileType() == StdFileTypes.JAVA)?
                   instance.getRootFiles(ProjectRootType.SOURCE):
                   instance.getContentRoots()
            ;
          }
        });

        HashSet<VirtualFile> visited = new HashSet<VirtualFile>(rootFiles.length);
        final VirtualFileFilter filter = new VirtualFileFilter() {
          public boolean accept(VirtualFile file) {
            return scope.contains(file);
          }
        };

        for (final VirtualFile rootFile : rootFiles) {
          if (visited.contains(rootFile)) continue;
          if (projectFileIndex.isInLibrarySource(rootFile) && !scope.isSearchInLibraries()) {
            continue;
          }

          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              FileIndexImplUtil.iterateRecursively(
                rootFile,
                filter,
                ci
              );
            }
          });

          visited.add(rootFile);
        }

        /* @ todo factor out handlers, etc*/
      } else {
        final PsiElement[] elementsToScan = ((LocalSearchScope)searchScope).getScope();
        totalFilesToScan = elementsToScan.length;

        for (PsiElement anElementsToScan : elementsToScan) {
          scheduler.addOneTask(new MatchOneFile(anElementsToScan));
        }
      }

      if (scheduler.getTaskQueueEndAction()==null) {
        scheduler.setTaskQueueEndAction(
          new Runnable() {
            public void run() {
              matchContext.getSink().matchingFinished();
            }
          }
        );
      }

      scheduler.executeNext();

    }
  }

  /**
     * Finds the matches of given pattern starting from given tree element.
     * @param sink match result destination
     * @throws MalformedPatternException
     * @throws UnsupportedPatternException
     */
    protected void testFindMatches(MatchResultSink sink, MatchOptions options)
      throws MalformedPatternException, UnsupportedPatternException {
      isTesting = true;
      try {
        findMatches(sink,options);
      } finally {
        isTesting = false;
      }
    }

  /**
   * Finds the matches of given pattern starting from given tree element.
   * @param source string for search
   * @param pattern to be searched
   * @return list of matches found
   * @throws MalformedPatternException
   * @throws UnsupportedPatternException
   */
  protected List testFindMatches(String source,String pattern, MatchOptions options, boolean filePattern)
    throws MalformedPatternException, UnsupportedPatternException {

    CollectingMatchResultSink sink = new CollectingMatchResultSink();

    try {
      PsiElement[] elements = MatcherImplUtil.createTreeFromText(source, filePattern, options.getFileType(), project);

      options.setSearchPattern(pattern);
      options.setScope( new LocalSearchScope(elements) );
      testFindMatches(sink,options);
    } catch (IncorrectOperationException e) {
      throw new MalformedPatternException();
    }

    return sink.getMatches();
  }

  private class TaskScheduler implements MatchingProcess {
    private LinkedList<Runnable> tasks = new LinkedList<Runnable>();
    private boolean ended;
    private Runnable taskQueueEndAction;

    private boolean suspended;
    private LinkedList<Runnable> tempList = new LinkedList<Runnable>();

    public LinkedList<Runnable> getTempList() {
      return tempList;
    }

    public void stop() {
      ended = true;
    }

    public void pause() {
      suspended = true;
    }

    public void resume() {
      if (!suspended) return;
      suspended = false;
      executeNext();
    }

    public boolean isSuspended() {
      return suspended;
    }

    public boolean isEnded() {
      return ended;
    }

    void setTaskQueueEndAction(Runnable _taskQueueEndAction) {
      taskQueueEndAction = _taskQueueEndAction;
    }
    Runnable getTaskQueueEndAction () {
      return taskQueueEndAction;
    }

    void addOneTask(Runnable runnable) {
      tasks.add(runnable);
    }

    void addNestedTasks(List<Runnable> list) {
      tasks.addAll(0,list);
    }

    private void executeNext() {
      while(!suspended && !ended) {
        if (tasks.size() == 0) {
          ended = true;
          break;
        }

        Runnable task = tasks.removeFirst();
        task.run();
      }

      if (ended) clearSchedule();
    }

    void init() {
      ended = false;
      suspended = false;
      PsiManager.getInstance(project).startBatchFilesProcessingMode();
    }

    private void clearSchedule() {
      if (tasks != null) {
        if (tasks.size()!=0) tasks.clear();
        taskQueueEndAction.run();

        PsiManager.getInstance(project).finishBatchFilesProcessingMode();
        tasks = null;
      }
    }

    public void addEndTasks(LinkedList<Runnable> tempList) {
      tasks.addAll(tempList);
    }
  }

  class ScanSelectedFiles implements Runnable {
    private LocalSearchScope scope;

    ScanSelectedFiles(LocalSearchScope _scope) {
      scope = _scope;
    }

    public void run() {
      final LinkedList<Runnable> tempList = scheduler.getTempList();
      final PsiElement[] elementsToScan = scope.getScope();
      totalFilesToScan = elementsToScan.length;

      for (PsiElement anElementsToScan : elementsToScan) {
        tempList.add(new MatchOneFile(anElementsToScan));
      }
      scheduler.addNestedTasks(tempList);
      tempList.clear();
    }
  }

  class MatchOneFile implements Runnable {
    private PsiElement file;

    MatchOneFile(PsiElement _file) {
      file = _file;
    }

    public void run() {
      final PsiFile psiFile = file.getContainingFile();

      if (psiFile!=null) {
        ApplicationManager.getApplication().invokeAndWait(
          new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(
                new Runnable() {
                  public void run() {
                    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
                    manager.commitDocument( manager.getDocument( psiFile ) );
                  }
                }
              );
            }
          },
          ModalityState.defaultModalityState()
        );

      }

      if (file instanceof PsiFile) {
        matchContext.getSink().processFile((PsiFile)file);
      }

      if (progress!=null) {
        progress.setFraction((double)scannedFilesCount/totalFilesToScan);
      }

      ++scannedFilesCount;
      if (file instanceof PsiIdentifier) {
        // Searching in previous results
        file = file.getParent();
      }

      ApplicationManager.getApplication().runReadAction(
        new Runnable() {
          public void run() {
            match(file);
          }
        }
      );

      file = null;
    }
  }

  // Initiates the matching process for given element
  // @param element the current search tree element
  private void match(PsiElement element) {
    MatchingStrategy strategy = matchContext.getPattern().getStrategy();

    if (strategy.continueMatching(element)) {
      visitor.matchContext(new ArrayBackedNodeIterator(new PsiElement[] {element}));
      return;
    }
    for(PsiElement el=element.getFirstChild();el!=null;el=el.getNextSibling()) {
      match(el);
    }
  }
}
