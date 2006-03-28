package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.project.Project;
import com.intellij.ide.IdeBundle;

/**
 * User: anna
 * Date: Feb 23, 2005
 */
public class DeleteFavoritesListAction extends AnAction {
  public DeleteFavoritesListAction() {
    super(IdeBundle.message("action.delete.favorites.list",""));
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    favoritesManager.removeFavoritesList(listName);
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    e.getPresentation().setEnabled(listName != null && !listName.equals(project.getName()));
    if (listName != null) {
      e.getPresentation().setText(IdeBundle.message("action.delete.favorites.list",listName));
      e.getPresentation().setDescription(IdeBundle.message("action.delete.favorites.list",listName));
    }
  }
}
