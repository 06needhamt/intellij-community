
package com.intellij.psi.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.JspxFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.lang.ASTNode;

import java.util.*;

public class PsiToDocumentSynchronizer extends PsiTreeChangeAdapter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiToDocumentSynchronizer");

  private final SmartPointerManagerImpl mySmartPointerManager;
  private PsiDocumentManagerImpl myPsiDocumentManager;

  public PsiToDocumentSynchronizer(PsiDocumentManagerImpl psiDocumentManager, SmartPointerManagerImpl smartPointerManager) {
    mySmartPointerManager = smartPointerManager;
    myPsiDocumentManager = psiDocumentManager;
  }

  public DocumentChangeTransaction getTransaction(final Document document) {
    return myTransactionsMap.get(document);
  }

  private static interface DocSyncAction {
    void syncDocument(Document document, PsiTreeChangeEventImpl event);
  }
  private void doSync(PsiTreeChangeEvent event, DocSyncAction syncAction) {
    if (!toProcessPsiEvent()) {
      return;
    }
    PsiFile psiFile = event.getFile();
    if (psiFile == null) return;
    DocumentEx document = getCachedDocument(psiFile);
    if (document == null) return;

    TextBlock textBlock = getTextBlock(document);
    if (!textBlock.isEmpty()) {
      LOG.error("Attempt to modify PSI for non-commited Document!");
      textBlock.clear();
    }

    if (!isOriginal(event.getParent(), psiFile, document)) {
      return;
    }

    myPsiDocumentManager.setProcessDocumentEvents(false);
    syncAction.syncDocument(document, (PsiTreeChangeEventImpl)event);
    myPsiDocumentManager.setProcessDocumentEvents(true);

    final boolean insideTransaction = myTransactionsMap.containsKey(document);
    if(!insideTransaction){
      document.setModificationStamp(psiFile.getModificationStamp());
      mySmartPointerManager.synchronizePointers(psiFile);
      if (LOG.isDebugEnabled()) {
        PsiDocumentManagerImpl.checkConsistency(psiFile, document);
        if (psiFile instanceof JspxFileImpl) {
          ((JspxFileImpl)psiFile).checkAllConsistent();
        }
      }
    }
  }

  private boolean isOriginal(final PsiElement changeScope, final PsiFile psiFile, final DocumentEx document) {
    boolean original = true;
    if(changeScope != null){
      ASTNode element = SourceTreeToPsiMap.psiElementToTree(changeScope);
      while(element != null && !(element instanceof FileElement)) {
        element = element.getTreeParent();
      }
      PsiFile fileForDoc = PsiDocumentManager.getInstance(psiFile.getProject()).getPsiFile(document);

      original = element != null ? fileForDoc == SourceTreeToPsiMap.treeElementToPsi(element) : false;
      LOG.debug("DOCSync: " + original + "; document=" + document+"; file="+psiFile.getName() + ":" +
                psiFile.getClass() +"; file for doc="+fileForDoc.getName()+"; virtualfile="+psiFile.getVirtualFile());
    }
    return original;
  }

  public void childAdded(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        insertString(document, event.getOffset(), event.getChild().getText());
      }
    });
  }

  public void childRemoved(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        deleteString(document, event.getOffset(), event.getOffset() + event.getOldLength());
      }
    });
  }

  public void childReplaced(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        replaceString(document, event.getOffset(), event.getOffset() + event.getOldLength(), event.getNewChild().getText());
      }
    });
  }

  public void childrenChanged(final PsiTreeChangeEvent event) {
    doSync(event, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        replaceString(document, event.getOffset(), event.getOffset() + event.getOldLength(), event.getParent().getText());
      }
    });
  }

  public void beforeChildReplacement(PsiTreeChangeEvent event) {
    processBeforeEvent(event);
  }

  public void beforeChildAddition(PsiTreeChangeEvent event) {
    processBeforeEvent(event);
  }

  public void beforeChildRemoval(PsiTreeChangeEvent event) {
    processBeforeEvent(event);
  }

  private void processBeforeEvent(PsiTreeChangeEvent event) {
    if (toProcessPsiEvent()) {
      PsiFile psiFile = event.getParent().getContainingFile();
      if (psiFile == null) return;

      //TODO: get red of this?
      mySmartPointerManager.fastenBelts(psiFile);
      mySmartPointerManager.unfastenBelts(psiFile);
    }
  }

  private static boolean toProcessPsiEvent() {
    Application application = ApplicationManager.getApplication();
    return application.getCurrentWriteAction(CommitToPsiFileAction.class) == null
           && application.getCurrentWriteAction(PsiExternalChangeAction.class) == null;
  }


  private Map<Document, DocumentChangeTransaction> myTransactionsMap = new HashMap<Document, DocumentChangeTransaction>();

  public void replaceString(Document document, int startOffset, int endOffset, String s) {
    final DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null) {
      documentChangeTransaction.replace(startOffset, endOffset - startOffset, s);
    }
    else {
      DocumentEx ex = (DocumentEx) document;
      ex.suppressGuardedExceptions();
      try {
        boolean isReadOnly = !document.isWritable();
        ex.setReadOnly(false);
        ex.replaceString(startOffset, endOffset, s);
        ex.setReadOnly(isReadOnly);
      }
      finally {
        ex.unSuppressGuardedExceptions();
      }
    }
  }

  public void insertString(Document document, int offset, String s) {
    final DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null){
      documentChangeTransaction.replace(offset, 0, s);
    }
    else {
      DocumentEx ex = (DocumentEx) document;
      ex.suppressGuardedExceptions();
      try {
        boolean isReadOnly = !ex.isWritable();
        ex.setReadOnly(false);
        ex.insertString(offset, s);
        ex.setReadOnly(isReadOnly);
      }
      finally {
        ex.unSuppressGuardedExceptions();
      }
    }
  }

  public void deleteString(Document document, int startOffset, int endOffset){
    final DocumentChangeTransaction documentChangeTransaction = getTransaction(document);
    if(documentChangeTransaction != null){
      documentChangeTransaction.replace(startOffset, endOffset - startOffset, "");
    }
    else {
      DocumentEx ex = (DocumentEx) document;
      ex.suppressGuardedExceptions();
      try {
        boolean isReadOnly = !ex.isWritable();
        ex.setReadOnly(false);
        ex.deleteString(startOffset, endOffset);
        ex.setReadOnly(isReadOnly);
      }
      finally {
        ex.unSuppressGuardedExceptions();
      }
    }
  }

  private DocumentEx getCachedDocument(PsiFile file) {
    return (DocumentEx)myPsiDocumentManager.getCachedDocument(file);
  }

  private TextBlock getTextBlock(Document document) {
    return myPsiDocumentManager.getTextBlock(document);
  }

  public void startTransaction(Document doc, PsiElement scope) {
    if(!myTransactionsMap.containsKey(doc)){
      LOG.assertTrue(doc.getText().equals(scope.getContainingFile().getText()),
                     "Document and PSI not synchronized on transaction start (don't send to IK)");
      myTransactionsMap.put(doc, new DocumentChangeTransaction(doc, scope));
    }
  }

  public void commitTransaction(Document document){
    final DocumentChangeTransaction documentChangeTransaction = removeTransaction(document);
    if(documentChangeTransaction == null) return;
    if(documentChangeTransaction.getAffectedFragments().size() == 0) return; // Nothing to do

    final PsiElement changeScope = documentChangeTransaction.getChangeScope();
    final PsiTreeChangeEventImpl fakeEvent = new PsiTreeChangeEventImpl(changeScope.getManager());
    fakeEvent.setParent(changeScope);
    fakeEvent.setFile(changeScope.getContainingFile());
    doSync(fakeEvent, new DocSyncAction() {
      public void syncDocument(Document document, PsiTreeChangeEventImpl event) {
        doCommitTransaction(document, documentChangeTransaction);
      }
    });
  }

  public void doCommitTransaction(final Document document){
    doCommitTransaction(document, getTransaction(document));
  }

  private static void doCommitTransaction(final Document document, final DocumentChangeTransaction documentChangeTransaction) {
    DocumentEx ex = (DocumentEx) document;
    ex.suppressGuardedExceptions();
    try {
      boolean isReadOnly = !document.isWritable();
      ex.setReadOnly(false);
      final Set<Pair<MutableTextRange, StringBuffer>> affectedFragments = documentChangeTransaction.getAffectedFragments();
      final Iterator<Pair<MutableTextRange, StringBuffer>> iterator = affectedFragments.iterator();
      while (iterator.hasNext()) {
        final Pair<MutableTextRange, StringBuffer> pair = iterator.next();
        final StringBuffer replaceBuffer = pair.getSecond();
        final MutableTextRange range = pair.getFirst();
        if(replaceBuffer.length() == 0){
          ex.deleteString(range.getStartOffset(), range.getEndOffset());
        }
        else if(range.getLength() == 0){
          ex.insertString(range.getStartOffset(), replaceBuffer);
        }
        else{
          ex.replaceString(range.getStartOffset(),
                           range.getEndOffset(),
                           replaceBuffer);
        }
      }
      ex.setReadOnly(isReadOnly);
      if(documentChangeTransaction.getChangeScope() != null && documentChangeTransaction.getChangeScope().getContainingFile() != null)
        LOG.assertTrue(document.getText().equals(documentChangeTransaction.getChangeScope().getContainingFile().getText()),
                       "Psi to document synchronization failed (send to IK)");
    }
    finally {
      ex.unSuppressGuardedExceptions();
    }
  }

  public DocumentChangeTransaction removeTransaction(Document doc) {
    return myTransactionsMap.remove(doc);
  }

  public static class DocumentChangeTransaction{
    private final Set<Pair<MutableTextRange,StringBuffer>> myAffectedFragments = new TreeSet<Pair<MutableTextRange, StringBuffer>>(new Comparator<Pair<MutableTextRange, StringBuffer>>() {
      public int compare(final Pair<MutableTextRange, StringBuffer> o1,
                         final Pair<MutableTextRange, StringBuffer> o2) {
        return o1.getFirst().getStartOffset() - o2.getFirst().getStartOffset();
      }
    });
    private final Document myDocument;
    private final PsiElement myChangeScope;

    public DocumentChangeTransaction(final Document doc, PsiElement scope) {
      myDocument = doc;
      myChangeScope = scope;
    }

    public Set<Pair<MutableTextRange, StringBuffer>> getAffectedFragments() {
      return myAffectedFragments;
    }

    public PsiElement getChangeScope() {
      return myChangeScope;
    }

    public void replace(int start, int length, String str){
      final int startInFragment;
      final StringBuffer fragmentReplaceText;

      { // calculating fragment
        { // minimize replace
          final int oldStart = start;
          int end = start + length;

          final int newStringLength = str.length();
          final String chars = getText(start, end);
          int newStartInString = 0;
          int newEndInString = newStringLength;
          while (newStartInString < newStringLength &&
                 start < end &&
                 str.charAt(newStartInString) == chars.charAt(start - oldStart)) {
            start++;
            newStartInString++;
          }
          while (end > start &&
                 newEndInString > newStartInString &&
                 str.charAt(newEndInString - 1) == chars.charAt(end - oldStart - 1)) {
            newEndInString--;
            end--;
          }

          str = str.substring(newStartInString, newEndInString);
          length = end - start;
        }

        final Pair<MutableTextRange, StringBuffer> fragment = getFragmentByRange(start, length);
        fragmentReplaceText = fragment.getSecond();
        startInFragment = start - fragment.getFirst().getStartOffset();
        { // text range adjustment
          final int lengthDiff = str.length() - length;
          final Iterator<Pair<MutableTextRange, StringBuffer>> iterator = myAffectedFragments.iterator();
          boolean adjust = false;
          while (iterator.hasNext()) {
            final Pair<MutableTextRange, StringBuffer> pair = iterator.next();
            if(adjust) pair.getFirst().shift(lengthDiff);
            if(pair == fragment)
              adjust = true;
          }
        }
      }

      fragmentReplaceText.replace(startInFragment, startInFragment + length, str);
    }

    private String getText(final int start, final int end) {
      int documentOffset = 0;
      int effectiveOffset = 0;
      StringBuffer text = new StringBuffer();
      Iterator<Pair<MutableTextRange, StringBuffer>> iterator = myAffectedFragments.iterator();
      while (iterator.hasNext() && effectiveOffset < end) {
        final Pair<MutableTextRange, StringBuffer> pair = iterator.next();
        final MutableTextRange range = pair.getFirst();
        final StringBuffer buffer = pair.getSecond();
        final int effectiveFragmentEnd = range.getStartOffset() + buffer.length();

        if(range.getStartOffset() <= start && effectiveFragmentEnd >= end){
          return buffer.substring(start - range.getStartOffset(), end - range.getStartOffset());
        }

        if(range.getStartOffset() >= start){
          final int effectiveStart = Math.max(effectiveOffset, start);
          text.append(myDocument.getChars(),
                                effectiveStart - effectiveOffset + documentOffset,
                                Math.min(range.getStartOffset(), end) - effectiveStart);
          if(end > range.getStartOffset()){
            text.append(buffer.substring(0, Math.min(end - range.getStartOffset(), buffer.length())));
          }
        }

        documentOffset += range.getEndOffset() - effectiveOffset;
        effectiveOffset = range.getStartOffset() + buffer.length();
      }

      if(effectiveOffset < end){
        final int effectiveStart = Math.max(effectiveOffset, start);
        text.append(myDocument.getChars(),
                              effectiveStart - effectiveOffset + documentOffset,
                              end - effectiveStart);
      }

      return text.toString();
    }

    private Pair<MutableTextRange, StringBuffer> getFragmentByRange(int start, final int length) {
      final StringBuffer fragmentBuffer = new StringBuffer();
      int end = start + length;

      {
        // restoring buffer and remove all subfragments from the list
        int documentOffset = 0;
        int effectiveOffset = 0;

        Iterator<Pair<MutableTextRange, StringBuffer>> iterator = myAffectedFragments.iterator();
        while (iterator.hasNext() && effectiveOffset < end) {
          final Pair<MutableTextRange, StringBuffer> pair = iterator.next();
          final MutableTextRange range = pair.getFirst();
          final StringBuffer buffer = pair.getSecond();
          int effectiveFragmentEnd = range.getStartOffset() + buffer.length();

          if(range.getStartOffset() <= start && effectiveFragmentEnd >= end) return pair;

          if(effectiveFragmentEnd >= start){
            final int effectiveStart = Math.max(effectiveOffset, start);
            if(range.getStartOffset() > start){
              fragmentBuffer.append(myDocument.getChars(),
                                    effectiveStart - effectiveOffset + documentOffset,
                                    Math.min(range.getStartOffset(), end) - effectiveStart);
            }
            if(end >= range.getStartOffset()){
              fragmentBuffer.append(buffer);
              end = end > effectiveFragmentEnd ? end - (buffer.length() - range.getLength()) : range.getEndOffset();
              effectiveFragmentEnd = range.getEndOffset();
              start = Math.min(start, range.getStartOffset());
              iterator.remove();
            }
          }

          documentOffset += range.getEndOffset() - effectiveOffset;
          effectiveOffset = effectiveFragmentEnd;
        }

        if(effectiveOffset < end){
          final int effectiveStart = Math.max(effectiveOffset, start);
          fragmentBuffer.append(myDocument.getChars(),
                                effectiveStart - effectiveOffset + documentOffset,
                                end - effectiveStart);
        }

      }

      final Pair<MutableTextRange, StringBuffer> pair = new Pair<MutableTextRange, StringBuffer>(new MutableTextRange(start, end), fragmentBuffer);
      myAffectedFragments.add(pair);
      return pair;
    }
  }

  public static class MutableTextRange{
    private int myLength;
    private int myStartOffset;

    public MutableTextRange(final int startOffset, final int endOffset) {
      myStartOffset = startOffset;
      myLength = endOffset - startOffset;
    }

    public int getStartOffset() {
      return myStartOffset;
    }

    public int getEndOffset() {
      return myStartOffset + myLength;
    }

    public int getLength() {
      return myLength;
    }

    public String toString() {
      return "[" + getStartOffset() + ", " + getEndOffset() + "]";
    }

    public void shift(final int lengthDiff) {
      myStartOffset += lengthDiff;
    }
  }
}
