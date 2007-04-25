package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vcs.RepositoryLocation;

import java.util.List;

/**
 * @author yole
 */
public interface CommittedChangesListener {
  void changesLoaded(RepositoryLocation location, List<CommittedChangeList> changes);
  void incomingChangesUpdated();
}
