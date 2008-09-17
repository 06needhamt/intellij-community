/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author peter
 */
public class InspectionDescriptionLinkHandler extends TooltipLinkHandler {
  public void handleLink(@NotNull final String descriptionSuffix, @NotNull final Editor editor, @NotNull final JEditorPane hintComponent) {
    showDescription(descriptionSuffix, editor, hintComponent);
  }

  @Nullable
  public String getDescription(final String shortName) {
    final InspectionProfileEntry tool =
      ((InspectionProfile)InspectionProfileManager.getInstance().getRootProfile()).getInspectionTool(shortName);
    if (tool == null) return null;

    String description;
    description = tool.loadDescription();
    if (description == null) {
      description = InspectionsBundle.message("inspection.tool.description.under.construction.text");
    }
    return description;
  }

  private void showDescription(final String shortName, final Editor editor, final JEditorPane tooltip) {
    final String description = getDescription(shortName);
    if (description == null) return;
    final JEditorPane pane = LineTooltipRenderer.initPane(description);
    pane.select(0, 0);
    pane.setPreferredSize(new Dimension(3 * tooltip.getPreferredSize().width /2, 200));
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(pane);
    scrollPane.setBorder(null);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, scrollPane).createPopup();
    pane.addMouseListener(new MouseAdapter(){
      public void mousePressed(final MouseEvent e) {
        final Component contentComponent = editor.getContentComponent();
        MouseEvent newMouseEvent = SwingUtilities.convertMouseEvent(e.getComponent(), e, contentComponent);
        popup.cancel();
        contentComponent.dispatchEvent(newMouseEvent);
      }
    });
    popup.showUnderneathOf(tooltip);
  }

}
