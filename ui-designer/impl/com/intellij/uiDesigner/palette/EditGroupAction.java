/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.palette;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.CommonBundle;

import java.util.ArrayList;

/**
 * @author yole
 */
public class EditGroupAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    GroupItem groupToBeEdited = (GroupItem)e.getDataContext().getData(GroupItem.class.getName());
    if (groupToBeEdited == null || project == null) return;

    // Ask group name
    final String groupName = Messages.showInputDialog(
      project,
      UIDesignerBundle.message("edit.enter.group.name"),
      UIDesignerBundle.message("title.edit.group"),
      Messages.getQuestionIcon(),
      groupToBeEdited.getName(),
      null
    );
    if(groupName == null || groupName.equals(groupToBeEdited.getName())){
      return;
    }

    Palette palette = Palette.getInstance(project);
    final ArrayList<GroupItem> groups = palette.getGroups();
    for(int i = groups.size() - 1; i >= 0; i--){
      if(groupName.equals(groups.get(i).getName())){
        Messages.showErrorDialog(project, UIDesignerBundle.message("error.group.name.unique"),
                                 CommonBundle.getErrorTitle());
        return;
      }
    }

    groupToBeEdited.setName(groupName);
    palette.fireGroupsChanged();
  }

  @Override public void update(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    GroupItem groupItem = (GroupItem) e.getDataContext().getData(GroupItem.class.getName());
    e.getPresentation().setEnabled(project != null && groupItem != null && !groupItem.isReadOnly());
  }
}
