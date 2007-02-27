package com.intellij.find.impl;

import com.intellij.find.FindBundle;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.impl.UsageViewImpl;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * @author cdr
 */
public class ShowRecentFindUsagesGroup extends ActionGroup {
  public void update(final AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);    
    e.getPresentation().setEnabled(project != null);
  }

  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    Project project = e.getData(DataKeys.PROJECT);
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    List<FindUsagesManager.SearchData> history = new ArrayList<FindUsagesManager.SearchData>(findUsagesManager.getFindUsageHistory());
    Collections.reverse(history);

    String description =
      ActionManager.getInstance().getAction(UsageViewImpl.SHOW_RECENT_FIND_USAGES_ACTION_ID).getTemplatePresentation().getDescription();

    List<AnAction> children = new ArrayList<AnAction>(history.size());
    for (final FindUsagesManager.SearchData data : history) {
      if (data.myElements == null) {
        continue;
      }
      PsiElement psiElement = data.myElements[0].getElement();
      if (psiElement == null) continue;
      String scopeString = data.myOptions.searchScope == null ? null : data.myOptions.searchScope.getDisplayName();
      String text = FindBundle.message("recent.find.usages.action.popup", UsageViewUtil.capitalize(UsageViewUtil.getType(psiElement)),
                                       UsageViewUtil.getDescriptiveName(psiElement),
                                       scopeString == null ? psiElement.getProject().getAllScope().getDisplayName() : scopeString);
      AnAction action = new AnAction(text, description, psiElement.getIcon(0)) {
        public void actionPerformed(final AnActionEvent e) {
          findUsagesManager.rerunAndRecallFromHistory(data);
        }
      };
      children.add(action);
    }
    return children.toArray(new AnAction[children.size()]);
  }
}
