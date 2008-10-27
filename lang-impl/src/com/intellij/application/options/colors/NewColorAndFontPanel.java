package com.intellij.application.options.colors;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class NewColorAndFontPanel extends JPanel {
  private SchemesPanel mySchemesPanel;
  private OptionsPanel myOptionsPanel;
  private PreviewPanel myPreviewPanel;
  private final String myCategory;

  public NewColorAndFontPanel(final SchemesPanel schemesPanel,
                              final OptionsPanel optionsPanel,
                              final PreviewPanel previewPanel,
                              final String category) {
    super(new BorderLayout());
    mySchemesPanel = schemesPanel;
    myOptionsPanel = optionsPanel;
    myPreviewPanel = previewPanel;
    myCategory = category;

    JPanel top = new JPanel(new BorderLayout());

    top.add(mySchemesPanel, BorderLayout.NORTH);
    top.add(myOptionsPanel.getPanel(), BorderLayout.CENTER);

    if (myPreviewPanel.getPanel() != null) {
      add(top, BorderLayout.NORTH);
      add(myPreviewPanel.getPanel(), BorderLayout.CENTER);
    }
    else {
      add(top, BorderLayout.CENTER);
    }

    previewPanel.addListener(new ColorAndFontSettingsListener.Abstract() {
      @Override
      public void selectionInPreviewChanged(final String typeToSelect) {
        optionsPanel.selectOption(typeToSelect);
      }
    });

    optionsPanel.addListener(new ColorAndFontSettingsListener.Abstract() {
      @Override
      public void settingsChanged() {
        if (schemesPanel.updateDescription(true)) {
          optionsPanel.applyChangesToScheme();
          previewPanel.updateView();
        }
      }

      public void selectedOptionChanged(final Object selected) {
        myPreviewPanel.blinkSelectedHighlightType(selected);
      }

    });
    mySchemesPanel.addListener(new ColorAndFontSettingsListener.Abstract() {
      public void schemeChanged(final Object source) {
        myOptionsPanel.updateOptionsList();
        myPreviewPanel.updateView();
      }
    });

  }

  public static NewColorAndFontPanel create(final PreviewPanel previewPanel, String category, final ColorAndFontOptions options) {
    final SchemesPanel schemesPanel = new SchemesPanel(options);

    final ColorAndFontDescriptionPanel descriptionPanel = new ColorAndFontDescriptionPanel();
    final OptionsPanel optionsPanel = new OptionsPanelImpl(descriptionPanel, options, schemesPanel, category);


    return new NewColorAndFontPanel(schemesPanel, optionsPanel, previewPanel, category);
  }

  public Runnable showOption(final String option) {
    return myOptionsPanel.showOption(option);
  }

  public boolean areSchemesLoaded() {
    return mySchemesPanel.areSchemesLoaded();
  }

  @NotNull
  public Map<String, String> processListOptions() {
    return myOptionsPanel.processListOptions();
  }


  public String getDisplayName() {
    return myCategory;
  }

  public void reset(Object source) {
    resetSchemesCombo(source);
  }

  public void disposeUIResources() {
    myPreviewPanel.disposeUIResources();
  }

  public void addSchemesListener(final ColorAndFontSettingsListener schemeListener) {
    mySchemesPanel.addListener(schemeListener);
  }

  private void resetSchemesCombo(Object source) {
    mySchemesPanel.resetSchemesCombo(source);
  }

  public boolean contains(final EditorSchemeAttributeDescriptor descriptor) {
    return descriptor.getGroup().equals(myCategory);
  }

  public JComponent getPanel() {
    return this;
  }

  public void updatePreview() {
    myPreviewPanel.updateView();
  }

  public void addDescriptionListener(final ColorAndFontSettingsListener listener) {
    myOptionsPanel.addListener(listener);
  }

  public boolean containsFontOptions() {
    return false;
  }
}
