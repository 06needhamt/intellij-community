package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.pom.Navigatable;

public abstract class BaseNavigateToSourceAction extends AnAction {
  private final boolean myFocusEditor;

  public BaseNavigateToSourceAction(boolean focusEditor) {
    myFocusEditor = focusEditor;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Navigatable[] navigatables = (Navigatable[])dataContext.getData(DataConstants.NAVIGATABLE_ARRAY);
    if (navigatables != null) {
      for (int i = 0; i < navigatables.length; i++) {
        Navigatable navigatable = navigatables[i];
        if (navigatable.canNavigateToSource()) navigatable.navigate(myFocusEditor);
      }
    }
  }


  public void update(AnActionEvent event){
    DataContext dataContext = event.getDataContext();
    event.getPresentation().setEnabled(isEnabled(dataContext));
  }

  private boolean isEnabled(final DataContext dataContext) {
    Navigatable[] navigatables = (Navigatable[])dataContext.getData(DataConstants.NAVIGATABLE_ARRAY);
    if (navigatables != null) {
      for (int i = 0; i < navigatables.length; i++) {
        Navigatable navigatable = navigatables[i];
        if (navigatable.canNavigateToSource()) return true;
      }
    }
    return false;    
  }

}
