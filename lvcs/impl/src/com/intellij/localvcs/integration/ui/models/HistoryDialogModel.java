package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.Reverter;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public abstract class HistoryDialogModel {
  protected ILocalVcs myVcs;
  protected VirtualFile myFile;
  protected IdeaGateway myGateway;
  private int myRightRevision;
  private int myLeftRevision;
  private List<Revision> myRevisionsCache;

  public HistoryDialogModel(VirtualFile f, ILocalVcs vcs, IdeaGateway gw) {
    myVcs = vcs;
    myFile = f;
    myGateway = gw;
  }

  public List<Revision> getRevisions() {
    if (myRevisionsCache == null) initRevisionsCache();
    return myRevisionsCache;
  }

  private void initRevisionsCache() {
    myGateway.registerUnsavedDocuments(myVcs);
    myRevisionsCache = myVcs.getRevisionsFor(myFile.getPath());
  }

  protected Revision getLeftRevision() {
    return getRevisions().get(myLeftRevision);
  }

  protected Revision getRightRevision() {
    return getRevisions().get(myRightRevision);
  }

  protected Entry getLeftEntry() {
    return getLeftRevision().getEntry();
  }

  protected Entry getRightEntry() {
    return getRightRevision().getEntry();
  }

  public void selectRevisions(int first, int second) {
    if (first == second) {
      myRightRevision = 0;
      myLeftRevision = first == -1 ? 0 : first;
    }
    else {
      myRightRevision = first;
      myLeftRevision = second;
    }
  }

  public boolean revert() {
    return Reverter.revert(myGateway, getLeftRevision(), getLeftEntry(), getRightEntry());
  }

  public boolean canRevert() {
    if (myRightRevision != 0 || myLeftRevision == 0) return false;
    return !getLeftEntry().hasUnavailableContent();
  }
}
