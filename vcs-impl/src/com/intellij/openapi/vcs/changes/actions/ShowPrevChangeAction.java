package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diff.SimpleDiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.ChangeRequestChain;

/**
 * @author yole
*/
public class ShowPrevChangeAction extends AnAction {
  public ShowPrevChangeAction() {
    setEnabledInModalContext(true);
  }

  public void update(AnActionEvent e) {
    final ChangeRequestChain chain = e.getData(VcsDataKeys.DIFF_REQUEST_CHAIN);
    e.getPresentation().setEnabled((chain != null) && (chain.canMoveBack()));
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final ChangeRequestChain chain = e.getData(VcsDataKeys.DIFF_REQUEST_CHAIN);
    if ((project == null) || (chain == null) || (! chain.canMoveBack())) {
      return;
    }

    final SimpleDiffRequest request = chain.moveBack();
    if (request != null) {
      ShowDiffAction.showNextPrevDiffForChange(e, request);
    }
  }
}