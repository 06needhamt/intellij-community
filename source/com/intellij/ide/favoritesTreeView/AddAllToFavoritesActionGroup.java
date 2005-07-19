package com.intellij.ide.favoritesTreeView;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: Mar 3, 2005
 */
public class AddAllToFavoritesActionGroup extends ActionGroup {
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return AnAction.EMPTY_ARRAY;
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null){
      return AnAction.EMPTY_ARRAY;
    }
    final String[] availableFavoritesLists = FavoritesViewImpl.getInstance(project).getAvailableFavoritesLists();
    if (availableFavoritesLists == null) return AnAction.EMPTY_ARRAY;
    AnAction[] actions = new AnAction[availableFavoritesLists.length + 2];
    int idx = 0;
    for (String favoritesList : availableFavoritesLists) {
      actions[idx++] = new AddAllOpenFilesToFavorites(favoritesList);
    }
    actions[idx++] = Separator.getInstance();
    actions[idx] = new AddAllOpenFilesToNewFavoritesListAction();
    return actions;
  }
}
