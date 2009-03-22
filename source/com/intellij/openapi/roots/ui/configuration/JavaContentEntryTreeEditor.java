package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.ToggleExcludedStateAction;
import com.intellij.openapi.roots.ui.configuration.actions.ToggleSourcesStateAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class JavaContentEntryTreeEditor extends ContentEntryTreeEditor {
  public JavaContentEntryTreeEditor(Project project) {
    super(project);
  }

  @Override
  protected void createEditingActions() {
    ToggleSourcesStateAction markSourcesAction = new ToggleSourcesStateAction(myTree, this, false);
    markSourcesAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.ALT_MASK)), myTree);

    ToggleExcludedStateAction toggleExcludedAction = new ToggleExcludedStateAction(myTree, this);
    toggleExcludedAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_MASK)), myTree);

    ToggleSourcesStateAction markTestsAction = new ToggleSourcesStateAction(myTree, this, true);
    markTestsAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.ALT_MASK)), myTree);

    myEditingActionsGroup.add(toggleExcludedAction);
    myEditingActionsGroup.add(markSourcesAction);
    myEditingActionsGroup.add(markTestsAction);
  }
}
