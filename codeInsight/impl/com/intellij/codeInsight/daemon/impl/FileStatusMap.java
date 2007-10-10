
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FileStatusMap {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.FileStatusMap");
  private final Project myProject;
  private final Map<Document,FileStatus> myDocumentToStatusMap = new WeakHashMap<Document, FileStatus>(); // all dirty if absent
  private static final Key<RefCountHolder> REF_COUND_HOLDER_IN_FILE_KEY = Key.create("DaemonCodeAnalyzerImpl.REF_COUND_HOLDER_IN_FILE_KEY");
  private final AtomicInteger myClearModificationCount = new AtomicInteger();

  public FileStatusMap(@NotNull Project project) {
    myProject = project;
  }

  static TextRange getDirtyTextRange(Editor editor, int part) {
    Document document = editor.getDocument();

    PsiElement dirtyScope = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(editor.getProject())).getFileStatusMap().getFileDirtyScope(document, part);
    if (dirtyScope == null || !dirtyScope.isValid()) {
      return null;
    }
    PsiFile file = dirtyScope.getContainingFile();
    if (file.getTextLength() != document.getTextLength()) {
      LOG.error("Length wrong! dirtyScope:" + dirtyScope,
                "file length:" + file.getTextLength(),
                "document length:" + document.getTextLength(),
                "file stamp:" + file.getModificationStamp(),
                "document stamp:" + document.getModificationStamp(),
                "file text     :" + file.getText(),
                "document text:" + document.getText());
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Dirty block optimization works");
    }
    return dirtyScope.getTextRange();
  }

  public void setErrorFoundFlag(Document document, boolean errorFound) {
    //GHP has found error. Flag is used by ExternalToolPass to decide whether to run itself or not
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        if (!errorFound) return;
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        status = new FileStatus(file);
        myDocumentToStatusMap.put(document, status);
      }
      status.errorFound = errorFound;
    }
  }
  
  public boolean wasErrorFound(Document document) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null && status.errorFound;
    }
  }

  private static class FileStatus {
    private PsiElement dirtyScope; //Q: use WeakReference?
    private PsiElement overridenDirtyScope;
    private PsiElement localInspectionsDirtyScope;
    public boolean defensivelyMarked; // file marked dirty without knowlesdge of specific dirty region. Subsequent markScopeDirty can refine dirty scope, not extend it
    private boolean wolfPassFinfished;
    private PsiElement externalDirtyScope;
    private boolean errorFound;

    private FileStatus(PsiElement dirtyScope) {
      this.dirtyScope = dirtyScope;
      overridenDirtyScope = dirtyScope;
      localInspectionsDirtyScope = dirtyScope;
      externalDirtyScope = dirtyScope;
    }
  }

  public void markAllFilesDirty() {
    synchronized(myDocumentToStatusMap){
      myDocumentToStatusMap.clear();
    }
    myClearModificationCount.incrementAndGet();
  }

  public void markFileUpToDate(@NotNull Document document, int passId) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        status = new FileStatus(file);
        myDocumentToStatusMap.put(document, status);
      }
      status.defensivelyMarked=false;
      switch (passId) {
        case Pass.UPDATE_ALL:
        case Pass.POST_UPDATE_ALL:
          status.dirtyScope = null;
          break;
        case Pass.UPDATE_OVERRIDEN_MARKERS:
          status.overridenDirtyScope = null;
          break;
        case Pass.LOCAL_INSPECTIONS:
          status.localInspectionsDirtyScope = null;
          break;
        case WolfPassFactory.PASS_ID:
          status.wolfPassFinfished = true;
          break;
        case Pass.EXTERNAL_TOOLS:
          status.externalDirtyScope = null;
          break;
        default:
          //LOG.error("unknown id "+passId);
          break;
      }
    }
  }

  /**
   * @param document
   * @param passId
   * @return null for processed file, whole file for untouched or entirely dirty file, PsiElement(usually code block) for dirty region (optimization)
   */
  public PsiElement getFileDirtyScope(@NotNull Document document, int passId) {
    synchronized(myDocumentToStatusMap){
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null){
        return PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      }
      if (status.defensivelyMarked) {
        status.dirtyScope = status.localInspectionsDirtyScope = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        status.defensivelyMarked = false;
      }
      switch (passId) {
        case Pass.UPDATE_ALL:
          return status.dirtyScope;
        case Pass.UPDATE_OVERRIDEN_MARKERS:
          return status.overridenDirtyScope;
        case Pass.LOCAL_INSPECTIONS:
          return status.localInspectionsDirtyScope;
        case Pass.EXTERNAL_TOOLS:
          return status.externalDirtyScope;
        default:
          LOG.assertTrue(false);
          return null;
      }
    }
  }

  public void markFileScopeDirtyDefensively(@NotNull PsiFile file) {
    // mark whole file dirty in case no subsequent PSI events will come, but file requires rehighlighting nevertheless
    // e.g. in the case of quick typing/backspacing char
    synchronized(myDocumentToStatusMap){
      Document document = PsiDocumentManager.getInstance(myProject).getCachedDocument(file);
      if (document == null) return;
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) return; // all dirty already
      status.defensivelyMarked = true;
    }
  }

  public void markFileScopeDirty(@NotNull Document document, @NotNull PsiElement scope) {
    synchronized(myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      if (status == null) return; // all dirty already
      if (status.defensivelyMarked) {
        status.defensivelyMarked = false;
      }
      final PsiElement combined1 = combineScopes(status.dirtyScope, scope);
      status.dirtyScope = combined1 == null ? PsiDocumentManager.getInstance(myProject).getPsiFile(document) : combined1;
      final PsiElement combined2 = combineScopes(status.localInspectionsDirtyScope, scope);
      status.localInspectionsDirtyScope = combined2 == null ? PsiDocumentManager.getInstance(myProject).getPsiFile(document) : combined2;
      status.overridenDirtyScope = combineScopes(status.overridenDirtyScope, scope);
      status.externalDirtyScope = combineScopes(status.externalDirtyScope, scope);
    }
  }

  private static PsiElement combineScopes(PsiElement scope1, PsiElement scope2) {
    if (scope1 == null) return scope2;
    if (scope2 == null) return scope1;
    if (!scope1.isValid() || !scope2.isValid()) return null;
    final PsiElement commonParent = PsiTreeUtil.findCommonParent(scope1, scope2);
    if (commonParent instanceof PsiDirectory) return null;
    return commonParent;
  }

  @NotNull
  public RefCountHolder getRefCountHolder(@NotNull PsiFile file) {
    RefCountHolder refCountHolder = file.getUserData(REF_COUND_HOLDER_IN_FILE_KEY);
    UserDataHolderEx holder = (UserDataHolderEx)file;
    if (refCountHolder == null) {
      refCountHolder = holder.putUserDataIfAbsent(REF_COUND_HOLDER_IN_FILE_KEY, new RefCountHolder(file));
    }
    return refCountHolder;
  }

  public boolean allDirtyScopesAreNull(final Document document) {
    synchronized (myDocumentToStatusMap) {
      FileStatus status = myDocumentToStatusMap.get(document);
      return status != null
             && !status.defensivelyMarked
             && status.dirtyScope == null
             && status.overridenDirtyScope == null
             && status.localInspectionsDirtyScope == null
             && status.externalDirtyScope == null
             && status.wolfPassFinfished
        ;
    }
  }
}