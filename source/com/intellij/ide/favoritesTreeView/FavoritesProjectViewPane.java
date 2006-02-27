package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author cdr
 */
public class FavoritesProjectViewPane extends AbstractProjectViewPane implements ProjectComponent {
  @NonNls static final String ID = "Favorites";
  private FavoritesTreeViewPanel myViewPanel;
  private final ProjectView myProjectView;
  private final FavoritesManager myFavoritesManager;
  private final FavoritesManager.FavoritesListener myFavoritesListener;

  protected FavoritesProjectViewPane(final Project project, ProjectView projectView, FavoritesManager favoritesManager) {
    super(project);
    myProjectView = projectView;
    myFavoritesManager = favoritesManager;
    myFavoritesListener = new FavoritesManager.FavoritesListener() {
      public void rootsChanged(String listName) {
      }
      public void listAdded(String listName) {
        refreshMySubIdsAndSelect(listName);
      }

      public void listRemoved(String listName) {
        String selectedSubId = getSubId();
        refreshMySubIdsAndSelect(selectedSubId);
      }

      private void refreshMySubIdsAndSelect(String listName) {
        myFavoritesManager.removeFavoritesListener(myFavoritesListener);
        myProjectView.removeProjectPane(FavoritesProjectViewPane.this);
        myProjectView.addProjectPane(FavoritesProjectViewPane.this);
        myFavoritesManager.addFavoritesListener(myFavoritesListener);

        if (ArrayUtil.find(myFavoritesManager.getAvailableFavoritesLists(), listName) == -1) {
          listName = null;
        }
        myProjectView.changeView(ID, listName);
      }
    };
  }
  public static FavoritesProjectViewPane getInstance(Project project) {
    return project.getComponent(FavoritesProjectViewPane.class);
  }

  public String getTitle() {
    return IdeBundle.message("action.toolwindow.favorites");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/toolWindowFavorites.png");
  }

  @NotNull
  public String getId() {
    return ID;
  }

  public JComponent createComponent() {
    myViewPanel = new FavoritesTreeViewPanel(myProject, null, getSubId());
    myTree = myViewPanel.getTree();
    myTreeBuilder = myViewPanel.getBuilder();
    myTreeStructure = myViewPanel.getFavoritesTreeStructure();
    return myViewPanel;
  }

  public void dispose() {
    myViewPanel = null;
    super.dispose();
  }

  @Nullable
  public String[] getSubIds() {
    return myFavoritesManager.getAvailableFavoritesLists();
  }

  @NotNull
  public String getPresentableSubIdName(@NotNull final String subId) {
    return subId;
  }

  public void updateFromRoot(boolean restoreExpandedPaths) {
    myTreeBuilder.updateFromRoot();
  }

  public void select(Object object, VirtualFile file, boolean requestFocus) {
    if (!(object instanceof PsiElement)) return;
    PsiElement element = (PsiElement)object;
    PsiFile psiFile = element.getContainingFile();
    if (psiFile != null) {
      element = psiFile;
    }

    if (element instanceof PsiJavaFile) {
      final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
      if (classes.length > 0) {
        element = classes[0];
      }
    }

    final PsiElement originalElement = element.getOriginalElement();
    final VirtualFile virtualFile = PsiUtil.getVirtualFile(originalElement);
    final String list = FavoritesViewSelectInTarget.findSuitableFavoritesList(virtualFile, myProject, getSubId());
    if (list == null) return;
    if (!list.equals(getSubId())) {
      ProjectView.getInstance(myProject).changeView(ID, list);
    }
    myViewPanel.selectElement(originalElement, virtualFile);
  }

  public int getWeight() {
    return 4;
  }

  public SelectInTarget createSelectInTarget() {
    return new FavoritesViewSelectInTarget(myProject);
  }

  public void addToolbarActions(final DefaultActionGroup group) {
    group.add(ActionManager.getInstance().getAction(IdeActions.RENAME_FAVORITES_LIST));
    group.add(ActionManager.getInstance().getAction(IdeActions.REMOVE_FROM_FAVORITES));
    group.add(ActionManager.getInstance().getAction(IdeActions.REMOVE_FAVORITES_LIST));
    group.add(ActionManager.getInstance().getAction(IdeActions.REMOVE_ALL_FAVORITES_LISTS_BUT_THIS));
  }

  //project component related
  public void projectOpened() {
    myProjectView.addProjectPane(this);
    myFavoritesManager.addFavoritesListener(myFavoritesListener);
  }

  public void projectClosed() {
  }

  @NonNls
  public String getComponentName() {
    return "FavoritesProjectViewPane";
  }

  public void initComponent() {

  }

  public void disposeComponent() {
    myFavoritesManager.removeFavoritesListener(myFavoritesListener);
  }
}
