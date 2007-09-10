package com.intellij.openapi.actionSystem;

import java.util.HashMap;
import java.util.Map;

public class ActionGroupUtil {
  private static Presentation getPresentation(AnAction action, Map<AnAction, Presentation> action2presentation) {
    Presentation presentation = action2presentation.get(action);
    if (presentation == null) {
      presentation = (Presentation)action.getTemplatePresentation().clone();
      action2presentation.put(action, presentation);
    }
    return presentation;
  }

  public static boolean isGroupEmpty(ActionGroup actionGroup, AnActionEvent e) {
    return isGroupEmpty(actionGroup, e, new HashMap<AnAction, Presentation>());
  }

  private static boolean isGroupEmpty(ActionGroup actionGroup, AnActionEvent e, Map<AnAction, Presentation> action2presentation) {
    AnAction[] actions = actionGroup.getChildren(e);
    for (AnAction action : actions) {
      if (action instanceof Separator) {
        continue;
      }
      else if (action instanceof ActionGroup) {
        if (isActionEnabledAndVisible(e, action2presentation, action) && !isGroupEmpty((ActionGroup)action, e, action2presentation)) {
          return false;
        }
      } else {
        if(isActionEnabledAndVisible(e, action2presentation, action)) return false;
      }
    }
    return true;
  }

  private static boolean isActionEnabledAndVisible(final AnActionEvent e, final Map<AnAction, Presentation> action2presentation,
                                         final AnAction action) {
    Presentation presentation = getPresentation(action, action2presentation);
    AnActionEvent event = new AnActionEvent(e.getInputEvent(),
                                            e.getDataContext(),
                                            ActionPlaces.UNKNOWN,
                                            presentation,
                                            ActionManager.getInstance(),
                                            e.getModifiers());
    event.setInjectedContext(action.isInInjectedContext());
    action.update(event);

    return presentation.isEnabled() && presentation.isVisible();
  }
}
