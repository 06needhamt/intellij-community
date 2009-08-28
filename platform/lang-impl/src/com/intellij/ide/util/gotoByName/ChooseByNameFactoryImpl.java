package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.GotoActionBase;
import com.intellij.openapi.project.Project;

/**
 * User: anna
 * Date: Jan 26, 2005
 */
public class ChooseByNameFactoryImpl extends ChooseByNameFactory {
  private final Project myProject;

  public ChooseByNameFactoryImpl(final Project project) {
    myProject = project;
  }

  public ChooseByNamePopup createChooseByNamePopupComponent(final ChooseByNameModel model) {
    return ChooseByNamePopup.createPopup(myProject, model, GotoActionBase.getPsiContext(myProject));  
  }
}
