/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
* User: Maxim.Mossienko
* Date: Jul 6, 2006
* Time: 5:08:37 PM
* To change this template use File | Settings | File Templates.
*/
public class AddHtmlTagOrAttributeToCustomsIntention implements IntentionAction {
  private final String myName;
  private final int myType;
  private final String myInspectionName;

  public AddHtmlTagOrAttributeToCustomsIntention(String shortName, String name, int type) {
    myInspectionName = shortName;
    myName = name;
    myType = type;
  }

  @NotNull
  public String getText() {
    if (myType == XmlEntitiesInspection.UNKNOWN_TAG) {
      return XmlBundle.message("add.custom.html.tag", myName);
    }

    if (myType == XmlEntitiesInspection.UNKNOWN_ATTRIBUTE) {
      return XmlBundle.message("add.custom.html.attribute", myName);
    }

    if (myType == XmlEntitiesInspection.NOT_REQUIRED_ATTRIBUTE) {
      return XmlBundle.message("add.optional.html.attribute", myName);
    }

    return getFamilyName();
  }

  @NotNull
  public String getFamilyName() {
    return XmlBundle.message("fix.html.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final InspectionProjectProfileManager profileManager = InspectionProjectProfileManager.getInstance(project);
    final InspectionProfile inspectionProfile = profileManager.getInspectionProfile();
    final ModifiableModel model = inspectionProfile.getModifiableModel();
    final LocalInspectionToolWrapper wrapper = (LocalInspectionToolWrapper)model.getInspectionTool(myInspectionName, file);
    final XmlEntitiesInspection xmlEntitiesInspection = (XmlEntitiesInspection)wrapper.getTool();
    xmlEntitiesInspection.setAdditionalEntries(myType, appendName(xmlEntitiesInspection.getAdditionalEntries(myType)));
    model.isProperSetting(HighlightDisplayKey.find(myInspectionName));//update map with non-default settings
    try {
      model.commit();
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, e.getMessage(), CommonBundle.getErrorTitle());
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  private String appendName(String toAppend) {
    if (toAppend.length() > 0) {
      toAppend += "," + myName;
    }
    else {
      toAppend = myName;
    }
    return toAppend;
  }

}
