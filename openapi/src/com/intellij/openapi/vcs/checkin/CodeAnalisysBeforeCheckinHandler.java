package com.intellij.openapi.vcs.checkin;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeSmellInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class CodeAnalisysBeforeCheckinHandler extends CheckinHandler {

  private final Project myProject;
  private final CheckinProjectPanel myCheckinPanel;

  public CodeAnalisysBeforeCheckinHandler(final Project project, CheckinProjectPanel panel) {
    myProject = project;
    myCheckinPanel = panel;
  }

  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    final JCheckBox checkBox = new JCheckBox(VcsBundle.message("before.checkin.standard.options.check.smells"));
    return new RefreshableOnComponent() {
      public JComponent getComponent() {
        return checkBox;
      }

      public void refresh() {
      }

      public void saveState() {
        getSettings().CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT = checkBox.isSelected();
      }

      public void restoreState() {
        checkBox.setSelected(getSettings().CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT);
      }
    };
  }

  private VcsConfiguration getSettings() {
    return VcsConfiguration.getInstance(myProject);
  }

  private ReturnResult processFoundCodeSmells(final List<CodeSmellInfo> codeSmells) {
    int errorCount = collectErrors(codeSmells);
    int warningCount = codeSmells.size() - errorCount;

    final int answer = Messages.showDialog(
      VcsBundle.message("before.commit.files.contain.code.smells.edit.them.confirm.text", errorCount, warningCount),
      VcsBundle.message("code.smells.error.messages.tab.name"), new String[]{VcsBundle.message("code.smells.review.button"),
      VcsBundle.message("code.smells.commit.button"), CommonBundle.getCancelButtonText()}, 0, UIUtil.getWarningIcon());
    if (answer == 0) {
      AbstractVcsHelper.getInstance(myProject).showCodeSmellErrors(codeSmells);
      return ReturnResult.CLOSE_WINDOW;
    }
    else if (answer == 2 || answer == -1) {
      return ReturnResult.CANCEL;
    }
    else {
      return ReturnResult.COMMIT;
    }
  }

  private int collectErrors(final List<CodeSmellInfo> codeSmells) {
    int result = 0;
    for (CodeSmellInfo codeSmellInfo : codeSmells) {
      if (codeSmellInfo.getSeverity() == HighlightSeverity.ERROR) result++;
    }
    return result;
  }

  public ReturnResult beforeCheckin() {
    if (getSettings().CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT) {
      try {
        final List<CodeSmellInfo> codeSmells =
          AbstractVcsHelper.getInstance(myProject).findCodeSmells(new ArrayList<VirtualFile>(myCheckinPanel.getVirtualFiles()));
        if (!codeSmells.isEmpty()) {
          return processFoundCodeSmells(codeSmells);
        }
        else {
          return ReturnResult.COMMIT;
        }
      }
      catch (ProcessCanceledException e) {
        return ReturnResult.CANCEL;
      }

    }
    else {
      return ReturnResult.COMMIT;
    }
  }
}
