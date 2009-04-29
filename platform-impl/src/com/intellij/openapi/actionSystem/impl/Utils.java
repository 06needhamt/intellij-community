package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class Utils{
  private static final Logger LOG=Logger.getInstance("#com.intellij.openapi.actionSystem.impl.Utils");
  @NonNls public static final String NOTHING_HERE = "Nothing here";
  public static final AnAction EMPTY_MENU_FILLER = new AnAction(NOTHING_HERE) {

    {
      getTemplatePresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(false);
      super.update(e);
    }
  };

  private Utils() {}

  private static void handleUpdateException(AnAction action, Presentation presentation, Throwable exc) {
    String id = ActionManager.getInstance().getId(action);
    if (id != null) {
      LOG.error("update failed for AnAction with ID=" + id, exc);
    }
    else {
      LOG.error("update failed for ActionGroup: " + action + "[" + presentation.getText() + "]", exc);
    }
  }

  /**
   * @param actionManager manager
   * @param list this list contains expanded actions.
   */
  public static void expandActionGroup(@NotNull ActionGroup group,
                                       ArrayList<AnAction> list,
                                       PresentationFactory presentationFactory,
                                       DataContext context,
                                       String place, ActionManager actionManager){
    Presentation presentation = presentationFactory.getPresentation(group);
    AnActionEvent e = new AnActionEvent(
      null,
      context,
      place,
      presentation,
      actionManager,
      0
    );
    if (!doUpdate(group, e, presentation)) return;

    if(!presentation.isVisible()){ // don't process invisible groups
      return;
    }
    AnAction[] children=group.getChildren(e);
    for (int i = 0; i < children.length; i++) {
      AnAction child = children[i];
      if (child == null) {
        String groupId = ActionManager.getInstance().getId(group);
        LOG.assertTrue(false, "action is null: i=" + i + " group=" + group + " group id=" + groupId);
        continue;
      }

      presentation = presentationFactory.getPresentation(child);
      AnActionEvent e1 = new AnActionEvent(null, context, place, presentation, actionManager, 0);
      e1.setInjectedContext(child.isInInjectedContext());
      if (!doUpdate(child, e1, presentation)) continue;
      if (!presentation.isVisible()) { // don't create invisible items in the menu
        continue;
      }
      if (child instanceof ActionGroup) {
        ActionGroup actionGroup = (ActionGroup)child;
        if (actionGroup.isPopup()) { // popup menu has its own presentation
          // disable group if it contains no visible actions
          final boolean enabled = hasVisibleChildren(actionGroup, presentationFactory, context, place);
          presentation.setEnabled(enabled);
          list.add(child);
        }
        else {
          expandActionGroup((ActionGroup)child, list, presentationFactory, context, place, actionManager);
        }
      }
      else if (child instanceof Separator) {
        if (!list.isEmpty() && !(list.get(list.size() - 1) instanceof Separator)) {
          list.add(child);
        }
      }
      else {
        list.add(child);
      }
    }
  }

  // returns false if exception was thrown and handled
  private static boolean doUpdate(final AnAction action, final AnActionEvent e, final Presentation presentation) throws ProcessCanceledException {
    if (ApplicationManager.getApplication().isDisposed()) return false;

    long startTime = System.currentTimeMillis();
    final boolean result;
    try {
      result = !ActionUtil.performDumbAwareUpdate(action, e, false);
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (Throwable exc) {
      handleUpdateException(action, presentation, exc);
      return false;
    }
    long endTime = System.currentTimeMillis();
    if (endTime - startTime > 10 && LOG.isDebugEnabled()) {
      LOG.debug("Action " + action + ": updated in " + (endTime-startTime) + " ms");
    }
    return result;
  }

  private static boolean hasVisibleChildren(ActionGroup group, PresentationFactory factory, DataContext context, String place) {
    AnActionEvent event = new AnActionEvent(null, context, place, factory.getPresentation(group), ActionManager.getInstance(), 0);
    event.setInjectedContext(group.isInInjectedContext());
    AnAction[] children = group.getChildren(event);
    for (AnAction anAction : children) {
      if (anAction instanceof Separator) {
        continue;
      }
      if (DumbServiceImpl.getInstance().isDumb() && !(anAction instanceof DumbAware) && !(anAction instanceof ActionGroup)) {
        continue;
      }

      LOG.assertTrue(anAction != null, "Null action found in group " + group);

      if (anAction instanceof ActionGroup) {
        ActionGroup childGroup = (ActionGroup)anAction;
        final Presentation presentation = factory.getPresentation(childGroup);
        AnActionEvent event1 = new AnActionEvent(null, context, place, presentation, ActionManager.getInstance(), 0);
        event1.setInjectedContext(childGroup.isInInjectedContext());
        doUpdate(childGroup, event1, presentation);

        // popup menu must be visible itself
        if (childGroup.isPopup()) {
          if (!presentation.isVisible()) {
            continue;
          }
        }

        if (hasVisibleChildren(childGroup, factory, context, place)) {
          return true;
        }
      }
      else {
        final Presentation presentation = factory.getPresentation(anAction);
        AnActionEvent event1 = new AnActionEvent(null, context, place, presentation, ActionManager.getInstance(), 0);
        event1.setInjectedContext(anAction.isInInjectedContext());
        doUpdate(anAction, event1, presentation);
        if (presentation.isVisible()) {
          return true;
        }
      }
    }

    return false;
  }


  public static void fillMenu(@NotNull ActionGroup group,JComponent component, boolean enableMnemonics, PresentationFactory presentationFactory, DataContext context, String place){
    ArrayList<AnAction> list = new ArrayList<AnAction>();
    expandActionGroup(group, list, presentationFactory, context, place, ActionManager.getInstance());

    for (int i = 0; i < list.size(); i++) {
      AnAction action = list.get(i);
      if (action instanceof Separator) {
        if (i > 0 && i < list.size() - 1) {
          component.add(new JPopupMenu.Separator());
        }
      }
      else if (action instanceof ActionGroup) {
        component.add(new ActionMenu(context, place, (ActionGroup)action, presentationFactory, enableMnemonics));
      }
      else {
        component.add(new ActionMenuItem(action, presentationFactory.getPresentation(action), place, context, enableMnemonics));
      }
    }

    if (list.isEmpty()) {
      component.add(new ActionMenuItem(EMPTY_MENU_FILLER, presentationFactory.getPresentation(EMPTY_MENU_FILLER), place, context, enableMnemonics));
    }
  }
}
