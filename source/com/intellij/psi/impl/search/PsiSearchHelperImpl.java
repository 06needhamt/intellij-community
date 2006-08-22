package com.intellij.psi.impl.search;

import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiSearchHelperImpl implements PsiSearchHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.PsiSearchHelperImpl");

  private final PsiManagerImpl myManager;
  private static final TodoItem[] EMPTY_TODO_ITEMS = new TodoItem[0];

  static {
    ReferencesSearch.INSTANCE.registerExecutor(new CachesBasedRefSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new PsiAnnotationMethodReferencesSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new ConstructorReferencesSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new SimpleAccessorReferenceSearcher());
    ReferencesSearch.INSTANCE.registerExecutor(new PropertyReferenceSearcher());

    DirectClassInheritorsSearch.INSTANCE.registerExecutor(new JavaDirectInheritorsSearcher());

    OverridingMethodsSearch.INSTANCE.registerExecutor(new JavaOverridingMethodsSearcher());

    AllOverridingMethodsSearch.INSTANCE.registerExecutor(new JavaAllOverridingMethodsSearcher());

    MethodReferencesSearch.INSTANCE.registerExecutor(new MethodUsagesSearcher());

    AnnotatedMembersSearch.INSTANCE.registerExecutor(new AnnotatedMembersSearcher());

    SuperMethodsSearch.SUPER_METHODS_SEARCH_INSTANCE.registerExecutor(new MethodSuperSearcher());
    DeepestSuperMethodsSearch.DEEPEST_SUPER_METHODS_SEARCH_INSTANCE.registerExecutor(new MethodDeepestSuperSearcher());

    IndexPatternSearch.INDEX_PATTERN_SEARCH_INSTANCE = new IndexPatternSearchImpl();
  }

  @NotNull
  public SearchScope getUseScope(PsiElement element) {
    final GlobalSearchScope maximalUseScope = myManager.getFileManager().getUseScope(element);
    if (element instanceof PsiPackage) {
      return maximalUseScope;
    }
    else if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        return new LocalSearchScope(element);
      }
      PsiFile file = element.getContainingFile();
      if (PsiUtil.isInJspFile(file)) return maximalUseScope;
      PsiClass aClass = (PsiClass)element;
      if (aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return maximalUseScope;
      }
      else if (aClass.hasModifierProperty(PsiModifier.PROTECTED)) {
        return maximalUseScope;
      }
      else if (aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiClass topClass = PsiUtil.getTopLevelClass(aClass);
        return new LocalSearchScope(topClass == null ? aClass.getContainingFile() : topClass);
      }
      else {
        PsiPackage aPackage = null;
        if (file instanceof PsiJavaFile) {
          aPackage = element.getManager().findPackage(((PsiJavaFile)file).getPackageName());
        }

        if (aPackage == null) {
          PsiDirectory dir = file.getContainingDirectory();
          if (dir != null) {
            aPackage = dir.getPackage();
          }
        }

        if (aPackage != null) {
          SearchScope scope = GlobalSearchScope.packageScope(aPackage, false);
          scope = scope.intersectWith(maximalUseScope);
          return scope;
        }

        return new LocalSearchScope(file);
      }
    }
    else if (element instanceof PsiMethod || element instanceof PsiField) {
      PsiMember member = (PsiMember) element;
      PsiFile file = element.getContainingFile();
      if (PsiUtil.isInJspFile(file)) return maximalUseScope;

      PsiClass aClass = member.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) {
        //member from anonymous class can be called from outside the class
        PsiElement methodCallExpr = PsiTreeUtil.getParentOfType(aClass, PsiMethodCallExpression.class);
        return new LocalSearchScope(methodCallExpr != null ? methodCallExpr : aClass);
      }

      if (member.hasModifierProperty(PsiModifier.PUBLIC)) {
        return maximalUseScope;
      }
      else if (member.hasModifierProperty(PsiModifier.PROTECTED)) {
        return maximalUseScope;
      }
      else if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
        PsiClass topClass = PsiUtil.getTopLevelClass(member);
        return topClass != null ? new LocalSearchScope(topClass) : new LocalSearchScope(file);
      }
      else {
        PsiPackage aPackage = file instanceof PsiJavaFile ? myManager.findPackage(((PsiJavaFile) file).getPackageName()) : null;
        if (aPackage != null) {
          SearchScope scope = GlobalSearchScope.packageScope(aPackage, false);
          scope = scope.intersectWith(maximalUseScope);
          return scope;
        }

        return maximalUseScope;
      }
    }
    else if (element instanceof ImplicitVariable) {
      return new LocalSearchScope(((ImplicitVariable)element).getDeclarationScope());
    }
    else if (element instanceof PsiLocalVariable) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiDeclarationStatement) {
        return new LocalSearchScope(parent.getParent());
      }
      else {
        return maximalUseScope;
      }
    }
    else if (element instanceof PsiParameter) {
      return new LocalSearchScope(((PsiParameter)element).getDeclarationScope());
    }
    else if (element instanceof PsiLabeledStatement) {
      return new LocalSearchScope(element);
    }
    else {
      return maximalUseScope;
    }
  }


  public PsiSearchHelperImpl(PsiManagerImpl manager) {
    myManager = manager;
  }

  public PsiReference[] findReferences(PsiElement element, SearchScope searchScope, boolean ignoreAccessScope) {
    LOG.assertTrue(searchScope != null);

    PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements();
    processReferences(processor, element, searchScope, ignoreAccessScope);
    return processor.toArray(PsiReference.EMPTY_ARRAY);
  }

  public boolean processReferences(final PsiReferenceProcessor processor,
                                   final PsiElement refElement,
                                   SearchScope originalScope,
                                   boolean ignoreAccessScope) {
    LOG.assertTrue(originalScope != null);

    final Query<PsiReference> query = ReferencesSearch.search(refElement, originalScope, ignoreAccessScope);
    return query.forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference t) {
        return processor.execute(t);
      }
    });
  }

  public PsiMethod[] findOverridingMethods(PsiMethod method, SearchScope searchScope, boolean checkDeep) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiMethod> processor = new PsiElementProcessor.CollectElements<PsiMethod>();
    processOverridingMethods(processor, method, searchScope, checkDeep);

    return processor.toArray(PsiMethod.EMPTY_ARRAY);
  }

  public boolean processOverridingMethods(final PsiElementProcessor<PsiMethod> processor,
                                          final PsiMethod method,
                                          SearchScope searchScope,
                                          final boolean checkDeep) {
    return OverridingMethodsSearch.search(method, searchScope, checkDeep).forEach(new Processor<PsiMethod>() {
      public boolean process(final PsiMethod t) {
        return processor.execute(t);
      }
    });
  }

  public PsiReference[] findReferencesIncludingOverriding(final PsiMethod method,
                                                          SearchScope searchScope,
                                                          boolean isStrictSignatureSearch) {
    LOG.assertTrue(searchScope != null);

    PsiReferenceProcessor.CollectElements processor = new PsiReferenceProcessor.CollectElements();
    processReferencesIncludingOverriding(processor, method, searchScope, isStrictSignatureSearch);
    return processor.toArray(PsiReference.EMPTY_ARRAY);
  }

  public boolean processReferencesIncludingOverriding(final PsiReferenceProcessor processor,
                                                      final PsiMethod method,
                                                      SearchScope searchScope) {
    return processReferencesIncludingOverriding(processor, method, searchScope, true);
  }

  public boolean processReferencesIncludingOverriding(final PsiReferenceProcessor processor,
                                                      final PsiMethod method,
                                                      SearchScope searchScope,
                                                      final boolean isStrictSignatureSearch) {
    LOG.assertTrue(searchScope != null);

    return MethodReferencesSearch.search(method, searchScope, isStrictSignatureSearch).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference t) {
        return processor.execute(t);
      }
    });
  }

  @NotNull
  public PsiClass[] findInheritors(PsiClass aClass, SearchScope searchScope, boolean checkDeep) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiClass> processor = new PsiElementProcessor.CollectElements<PsiClass>();
    processInheritors(processor, aClass, searchScope, checkDeep);
    return processor.toArray(PsiClass.EMPTY_ARRAY);
  }

  public boolean processInheritors(PsiElementProcessor<PsiClass> processor,
                                   PsiClass aClass,
                                   SearchScope searchScope,
                                   boolean checkDeep) {
    return processInheritors(processor, aClass, searchScope, checkDeep, true);
  }

  public boolean processInheritors(final PsiElementProcessor<PsiClass> processor,
                                   PsiClass aClass,
                                   SearchScope searchScope,
                                   boolean checkDeep,
                                   boolean checkInheritance) {
    return ClassInheritorsSearch.search(aClass, searchScope, checkDeep, checkInheritance).forEach(new Processor<PsiClass>() {
      public boolean process(final PsiClass t) {
        return processor.execute(t);
      }
    });
  }

  public PsiFile[] findFilesWithTodoItems() {
    return myManager.getCacheManager().getFilesWithTodoItems();
  }

  public TodoItem[] findTodoItems(PsiFile file) {
    return doFindTodoItems(file);
  }

  public TodoItem[] findTodoItems(PsiFile file, int startOffset, int endOffset) {
    return doFindTodoItems(file);
  }

  private static TodoItem[] doFindTodoItems(final PsiFile file) {
    final Collection<IndexPatternOccurrence> occurrences = IndexPatternSearch.search(file, TodoConfiguration.getInstance()).findAll();
    if (occurrences.isEmpty()) {
      return EMPTY_TODO_ITEMS;
    }

    TodoItem[] items = new TodoItem[occurrences.size()];
    int index = 0;
    for(IndexPatternOccurrence occurrence: occurrences) {
      items [index++] = new TodoItemImpl(occurrence.getFile(),
                                         occurrence.getTextRange().getStartOffset(),
                                         occurrence.getTextRange().getEndOffset(),
                                         mapPattern(occurrence.getPattern()));
    }

    return items;
  }

  private static TodoPattern mapPattern(final IndexPattern pattern) {
    for(TodoPattern todoPattern: TodoConfiguration.getInstance().getTodoPatterns()) {
      if (todoPattern.getIndexPattern() == pattern) {
        return todoPattern;
      }
    }
    LOG.assertTrue(false, "Could not find matching TODO pattern for index pattern " + pattern.getPatternString());
    return null;
  }

  public int getTodoItemsCount(PsiFile file) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile(), TodoConfiguration.getInstance());
    if (count != -1) return count;
    return findTodoItems(file).length;
  }

  public int getTodoItemsCount(PsiFile file, TodoPattern pattern) {
    int count = myManager.getCacheManager().getTodoCount(file.getVirtualFile(), pattern.getIndexPattern());
    if (count != -1) return count;
    TodoItem[] items = findTodoItems(file);
    count = 0;
    for (TodoItem item : items) {
      if (item.getPattern().equals(pattern)) count++;
    }
    return count;
  }

  public PsiIdentifier[] findIdentifiers(String identifier, SearchScope searchScope, short searchContext) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiIdentifier> processor = new PsiElementProcessor.CollectElements<PsiIdentifier>();
    processIdentifiers(processor, identifier, searchScope, searchContext);
    return processor.toArray(PsiIdentifier.EMPTY_ARRAY);
  }

  public boolean processIdentifiers(final PsiElementProcessor<PsiIdentifier> processor,
                                    final String identifier,
                                    SearchScope searchScope,
                                    short searchContext) {
    LOG.assertTrue(searchScope != null);

    TextOccurenceProcessor processor1 = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element instanceof PsiIdentifier) {
          return processor.execute((PsiIdentifier)element);
        }
        return true;
      }
    };
    return processElementsWithWord(processor1, searchScope, identifier, searchContext, true);
  }

  private static final TokenSet COMMENT_BIT_SET = TokenSet.create(JavaDocTokenType.DOC_COMMENT_DATA, JavaDocTokenType.DOC_TAG_VALUE_TOKEN,
                                                                  JavaTokenType.C_STYLE_COMMENT, JavaTokenType.END_OF_LINE_COMMENT);

  public PsiElement[] findCommentsContainingIdentifier(String identifier, SearchScope searchScope) {
    LOG.assertTrue(searchScope != null);

    final ArrayList<PsiElement> results = new ArrayList<PsiElement>();
    TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element.getNode() != null && !COMMENT_BIT_SET.contains(element.getNode().getElementType())) return true;
        if (element.findReferenceAt(offsetInElement) == null) {
          results.add(element);
        }
        return true;
      }
    };
    processElementsWithWord(processor, searchScope, identifier, UsageSearchContext.IN_COMMENTS, true);
    return results.toArray(new PsiElement[results.size()]);
  }

  public PsiLiteralExpression[] findStringLiteralsContainingIdentifier(String identifier, SearchScope searchScope) {
    LOG.assertTrue(searchScope != null);

    final ArrayList<PsiLiteralExpression> results = new ArrayList<PsiLiteralExpression>();
    TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element instanceof PsiLiteralExpression) {
          results.add((PsiLiteralExpression)element);
        }
        return true;
      }
    };
    processElementsWithWord(processor,
                            searchScope,
                            identifier,
                            UsageSearchContext.IN_STRINGS,
                            true);
    return results.toArray(new PsiLiteralExpression[results.size()]);
  }

  public boolean processAllClasses(final PsiElementProcessor<PsiClass> processor, SearchScope searchScope) {
    if (searchScope instanceof GlobalSearchScope) {
      return processAllClassesInGlobalScope((GlobalSearchScope)searchScope, processor);
    }

    PsiElement[] scopeRoots = ((LocalSearchScope)searchScope).getScope();
    for (final PsiElement scopeRoot : scopeRoots) {
      if (!processScopeRootForAllClasses(scopeRoot, processor)) return false;
    }
    return true;
  }

  private static boolean processScopeRootForAllClasses(PsiElement scopeRoot, final PsiElementProcessor<PsiClass> processor) {
    if (scopeRoot == null) return true;
    final boolean[] stopped = new boolean[]{false};

    scopeRoot.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (!stopped[0]) {
          visitElement(expression);
        }
      }

      public void visitClass(PsiClass aClass) {
        stopped[0] = !processor.execute(aClass);
        super.visitClass(aClass);
      }
    });

    return !stopped[0];
  }

  private boolean processAllClassesInGlobalScope(final GlobalSearchScope searchScope, final PsiElementProcessor<PsiClass> processor) {
    myManager.getRepositoryManager().updateAll();

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();
    fileIndex.iterateContent(new ContentIterator() {
      public boolean processFile(VirtualFile fileOrDir) {
        if (!fileOrDir.isDirectory() && searchScope.contains(fileOrDir)) {
          final PsiFile psiFile = myManager.findFile(fileOrDir);
          if (psiFile instanceof PsiJavaFile) {
            long fileId = myManager.getRepositoryManager().getFileId(fileOrDir);
            if (fileId >= 0) {
              long[] allClasses = myManager.getRepositoryManager().getFileView().getAllClasses(fileId);
              for (long allClass : allClasses) {
                PsiClass psiClass = (PsiClass)myManager.getRepositoryElementsManager().findOrCreatePsiElementById(allClass);
                if (!processor.execute(psiClass)) return false;
              }
            }
            else {
              if (!processScopeRootForAllClasses(psiFile, processor)) return false;
            }
          }
        }
        return true;
      }
    });

    return true;
  }

  public PsiClass[] findAllClasses(SearchScope searchScope) {
    LOG.assertTrue(searchScope != null);

    PsiElementProcessor.CollectElements<PsiClass> processor = new PsiElementProcessor.CollectElements<PsiClass>();
    processAllClasses(processor, searchScope);
    return processor.toArray(PsiClass.EMPTY_ARRAY);
  }

  public boolean processElementsWithWord(TextOccurenceProcessor processor,
                                          SearchScope searchScope,
                                          String text,
                                          short searchContext,
                                          boolean caseSensitively) {
    LOG.assertTrue(searchScope != null);

    if (searchScope instanceof GlobalSearchScope) {
      StringSearcher searcher = new StringSearcher(text);
      searcher.setCaseSensitive(caseSensitively);

      return processElementsWithTextInGlobalScope(processor,
                                                  (GlobalSearchScope)searchScope,
                                                  searcher,
                                                  searchContext, caseSensitively);
    }
    else {
      LocalSearchScope _scope = (LocalSearchScope)searchScope;
      PsiElement[] scopeElements = _scope.getScope();

      for (final PsiElement scopeElement : scopeElements) {
        if (!processElementsWithWordInScopeElement(scopeElement, processor, text, caseSensitively)) return false;
      }
      return true;
    }
  }

  private static boolean processElementsWithWordInScopeElement(PsiElement scopeElement,
                                                               TextOccurenceProcessor processor,
                                                               String word,
                                                               boolean caseSensitive) {
    StringSearcher searcher = new StringSearcher(word);
    searcher.setCaseSensitive(caseSensitive);

    return LowLevelSearchUtil.processElementsContainingWordInElement(processor, scopeElement, searcher);
  }

  private boolean processElementsWithTextInGlobalScope(TextOccurenceProcessor processor,
                                                       GlobalSearchScope scope,
                                                       StringSearcher searcher,
                                                       short searchContext,
                                                       final boolean caseSensitively) {

    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }
    myManager.startBatchFilesProcessingMode();

    try {
      String[] words = StringUtil.getWordsIn(searcher.getPattern()).toArray(ArrayUtil.EMPTY_STRING_ARRAY);
      if(words.length == 0) return true;

      Set<PsiFile> fileSet = new THashSet<PsiFile>();
      for (String word : words) {
        List<PsiFile> psiFiles = Arrays.asList(myManager.getCacheManager().getFilesWithWord(word, searchContext, scope, caseSensitively));
        if (fileSet.isEmpty()) {
          fileSet.addAll(psiFiles);
        }
        else {
          fileSet.retainAll(psiFiles);
        }
        if (fileSet.isEmpty()) break;
      }
      PsiFile[] files = fileSet.toArray(new PsiFile[fileSet.size()]);

      if (progress != null) {
        progress.setText(PsiBundle.message("psi.search.for.word.progress", searcher.getPattern()));
      }

      for (int i = 0; i < files.length; i++) {
        ProgressManager.getInstance().checkCanceled();

        PsiFile file = files[i];
        PsiElement[] psiRoots = file.getPsiRoots();
        Set<PsiElement> processed = new HashSet<PsiElement>(psiRoots.length * 2, (float)0.5);
        for (PsiElement psiRoot : psiRoots) {
          if(processed.contains(psiRoot)) continue;
          processed.add(psiRoot);
          if (!LowLevelSearchUtil.processElementsContainingWordInElement(processor, psiRoot, searcher)) {
            return false;
          }
        }

        if (progress != null) {
          double fraction = (double)i / files.length;
          progress.setFraction(fraction);
        }

        myManager.dropResolveCaches();
      }
    }
    finally {
      if (progress != null) {
        progress.popState();
      }
      myManager.finishBatchFilesProcessingMode();
    }

    return true;
  }

  public PsiFile[] findFilesWithPlainTextWords(String word) {
    return myManager.getCacheManager().getFilesWithWord(word,
                                                        UsageSearchContext.IN_PLAIN_TEXT,
                                                        GlobalSearchScope.projectScope(myManager.getProject()), true);
  }


  public void processUsagesInNonJavaFiles(String qName,
                                          PsiNonJavaFileReferenceProcessor processor,
                                          GlobalSearchScope searchScope) {
    processUsagesInNonJavaFiles(null, qName, processor, searchScope);
  }

  public void processUsagesInNonJavaFiles(@Nullable PsiElement originalElement,
                                          String qName,
                                          PsiNonJavaFileReferenceProcessor processor,
                                          GlobalSearchScope searchScope) {
    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator progress = progressManager.getProgressIndicator();

    int dotIndex = qName.lastIndexOf('.');
    int dollarIndex = qName.lastIndexOf('$');
    int maxIndex = Math.max(dotIndex, dollarIndex);
    String wordToSearch = maxIndex >= 0 ? qName.substring(maxIndex + 1) : qName;
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(wordToSearch, UsageSearchContext.IN_PLAIN_TEXT, searchScope, true);

    StringSearcher searcher = new StringSearcher(qName);
    searcher.setCaseSensitive(true);
    searcher.setForwardDirection(true);

    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.search.in.non.java.files.progress"));
    }

    AllFilesLoop:
    for (int i = 0; i < files.length; i++) {
      ProgressManager.getInstance().checkCanceled();

      PsiFile psiFile = files[i];
      char[] text = psiFile.textToCharArray();
      for (int index = LowLevelSearchUtil.searchWord(text, 0, text.length, searcher); index >= 0;) {
        PsiReference referenceAt = psiFile.findReferenceAt(index);
        if (referenceAt == null ||
            originalElement != null && !PsiSearchScopeUtil.isInScope(getUseScope(originalElement).intersectWith(searchScope), psiFile)) {
          if (!processor.process(psiFile, index, index + searcher.getPattern().length())) break AllFilesLoop;
        }

        index = LowLevelSearchUtil.searchWord(text, index + searcher.getPattern().length(), text.length, searcher);
      }

      if (progress != null) {
        progress.setFraction((double)(i + 1) / files.length);
      }
    }

    if (progress != null) {
      progress.popState();
    }
  }

  public PsiFile[] findFormsBoundToClass(String className) {
    if (className == null) return PsiFile.EMPTY_ARRAY;
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myManager.getProject());
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(className, UsageSearchContext.IN_FOREIGN_LANGUAGES,
                                                                   projectScope, true);
    List<PsiFile> boundForms = new ArrayList<PsiFile>(files.length);
    for (PsiFile psiFile : files) {
      if (psiFile.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;

      String text = psiFile.getText();
      try {
        String boundClass = Utils.getBoundClassName(text);
        if (className.equals(boundClass)) boundForms.add(psiFile);
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }

    return boundForms.toArray(new PsiFile[boundForms.size()]);
  }

  public boolean isFieldBoundToForm(PsiField field) {
    PsiClass aClass = field.getContainingClass();
    if (aClass != null && aClass.getQualifiedName() != null) {
      PsiFile[] formFiles = findFormsBoundToClass(aClass.getQualifiedName());
      for (PsiFile file : formFiles) {
        final PsiReference[] references = file.getReferences();
        for (final PsiReference reference : references) {
          if (reference.isReferenceTo(field)) return true;
        }
      }
    }

    return false;
  }

  public void processAllFilesWithWord(String word, GlobalSearchScope scope, Processor<PsiFile> processor, final boolean caseSensitively) {
    myManager.getCacheManager().processFilesWithWord(processor,word, UsageSearchContext.IN_CODE, scope, caseSensitively);
  }

  public void processAllFilesWithWordInComments(String word, GlobalSearchScope scope, Processor<PsiFile> processor) {
    myManager.getCacheManager().processFilesWithWord(processor, word, UsageSearchContext.IN_COMMENTS, scope, true);
  }

  public void processAllFilesWithWordInLiterals(String word, GlobalSearchScope scope, Processor<PsiFile> processor) {
    myManager.getCacheManager().processFilesWithWord(processor, word, UsageSearchContext.IN_STRINGS, scope, true);
  }

}
