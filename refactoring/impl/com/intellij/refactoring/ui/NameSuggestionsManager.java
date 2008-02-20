package com.intellij.refactoring.ui;

import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.util.containers.HashMap;
import com.intellij.refactoring.IdentifierCharFilter;

import javax.swing.*;
import java.awt.event.*;
import java.util.Set;

/**
 * @author dsl
 */
public class NameSuggestionsManager {
  private final TypeSelector myTypeSelector;
  private final NameSuggestionsField myNameField;
  private final NameSuggestionsGenerator myGenerator;
  private final Project myProject;

  private final HashMap<PsiType, SuggestedNameInfo> myTypesToSuggestions = new HashMap<PsiType, SuggestedNameInfo>();

  public NameSuggestionsManager(TypeSelector typeSelector, NameSuggestionsField nameField,
                                NameSuggestionsGenerator generator, Project project) {
    myTypeSelector = typeSelector;
    myNameField = nameField;
    myGenerator = generator;
    myProject = project;

    myTypeSelector.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateSuggestions(myTypeSelector.getSelectedType());
        }
      }
    });
    updateSuggestions(myTypeSelector.getSelectedType());

    myNameField.getComponent().registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        completeVariable(myNameField.getEditor());
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, KeyEvent.CTRL_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private void completeVariable(Editor editor) {
    String prefix = myNameField.getEnteredName();
    Pair<LookupItemPreferencePolicy, Set<LookupItem>> pair = myGenerator.completeVariableName(prefix, myTypeSelector.getSelectedType());
    LookupItem[] lookupItems = pair.second.toArray(new LookupItem[pair.second.size()]);
    editor.getCaretModel().moveToOffset(prefix.length());
    editor.getSelectionModel().removeSelection();
    LookupItemPreferencePolicy first = pair.first;
    LookupManager.getInstance(myProject).showLookup(editor, lookupItems, prefix, first, IdentifierCharFilter.INSTANCE);
  }

  public void nameSelected() {
    SuggestedNameInfo nameInfo = myTypesToSuggestions.get(myTypeSelector.getSelectedType());

    if (nameInfo != null) {
      nameInfo.nameChoosen(myNameField.getEnteredName());
    }
  }

  private void updateSuggestions(PsiType selectedType) {
    SuggestedNameInfo nameInfo = myTypesToSuggestions.get(selectedType);
    if (nameInfo == null) {
      nameInfo = myGenerator.getSuggestedNameInfo(selectedType);
      myTypesToSuggestions.put(selectedType, nameInfo);
    }
    myNameField.setSuggestions(nameInfo.names);
  }

  public void setLabelsFor(JLabel typeSelectorLabel, JLabel nameLabel) {
    if(myTypeSelector.getFocusableComponent() != null) {
      typeSelectorLabel.setLabelFor(myTypeSelector.getFocusableComponent());
    }

    if(myNameField.getFocusableComponent() != null) {
      nameLabel.setLabelFor(myNameField.getFocusableComponent());
    }
  }
}
