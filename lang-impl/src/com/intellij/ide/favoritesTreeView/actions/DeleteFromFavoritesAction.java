package com.intellij.ide.favoritesTreeView.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.favoritesTreeView.FavoritesTreeNodeDescriptor;
import com.intellij.ide.favoritesTreeView.FavoritesTreeViewPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Feb 23, 2005
 */
public class DeleteFromFavoritesAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#" + DeleteFromFavoritesAction.class.getName());

  public DeleteFromFavoritesAction() {
    super(IdeBundle.message("action.remove.from.current.favorites"));
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    FavoritesTreeNodeDescriptor[] roots = (FavoritesTreeNodeDescriptor[])dataContext.getData(FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS);
    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    assert roots != null;
    assert listName != null;
    for (FavoritesTreeNodeDescriptor root : roots) {
      final Object value = root.getElement().getValue();
      LOG.assertTrue(value != null, root.getElement());
      favoritesManager.removeRoot(listName, value);
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    final String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    FavoritesTreeNodeDescriptor[] roots = (FavoritesTreeNodeDescriptor[])dataContext.getData(FavoritesTreeViewPanel.CONTEXT_FAVORITES_ROOTS);
    e.getPresentation().setEnabled(listName != null && roots != null && roots.length != 0);
  }
}
