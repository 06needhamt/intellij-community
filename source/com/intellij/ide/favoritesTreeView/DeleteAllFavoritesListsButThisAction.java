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
public class DeleteAllFavoritesListsButThisAction extends AnAction {
  public DeleteAllFavoritesListsButThisAction() {
    super(IdeBundle.message("action.delete.all.favorites.lists.but.this",""));
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    FavoritesManager favoritesManager = FavoritesManager.getInstance(project);
    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    String[] lists = favoritesManager.getAvailableFavoritesLists();
    for (String list : lists) {
      if (!list.equals(listName)) {
        favoritesManager.removeFavoritesList(list);
      }
    }
  }

  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    String listName = (String)dataContext.getData(FavoritesTreeViewPanel.FAVORITES_LIST_NAME);
    e.getPresentation().setEnabled(listName != null);
    if (listName != null) {
      e.getPresentation().setText(IdeBundle.message("action.delete.all.favorites.lists.but.this",listName));
      e.getPresentation().setDescription(IdeBundle.message("action.delete.all.favorites.lists.but.this",listName));
    }
  }
}
