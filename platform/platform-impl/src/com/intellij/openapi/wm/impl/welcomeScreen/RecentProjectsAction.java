package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.UIBundle;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Mar 2, 2005
 * Time: 4:02:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class RecentProjectsAction extends WelcomePopupAction {
  protected void fillActions(final DefaultActionGroup group) {
    final AnAction[] recentProjectActions = RecentProjectsManagerBase.getInstance().getRecentProjectsActions(false);
    for (AnAction action : recentProjectActions) {
      group.add(action);
    }
  }

  protected String getTextForEmpty() {
    return UIBundle.message("welcome.screen.recent.projects.action.no.recent.projects.to.display.action.name");
  }

  protected String getCaption() {
    return "";
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(RecentProjectsManagerBase.getInstance().getRecentProjectsActions(false).length > 0);
  }
}
