package com.intellij.cvsSupport2;

import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.cvsSupport2.ui.SelectFileVersionDialog;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.Nullable;

public class CvsRevisionSelector implements RevisionSelector {


  private final Project myProject;

  public CvsRevisionSelector(final Project project) {
    myProject = project;
  }

  @Nullable public VcsRevisionNumber selectNumber(VirtualFile file) {
    final SelectFileVersionDialog selector = new SelectFileVersionDialog(
      PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file),
      myProject);

    selector.show();

    if (selector.isOK()) {
      return new CvsRevisionNumber(selector.getRevisionOrDate());
    } else {
      return null;
    }
  }
}
