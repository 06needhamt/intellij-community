/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui;

import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

/**
 * User: anna
 * Date: 13-Mar-2006
 */
public class MoveComponentListener extends MouseAdapter implements MouseMotionListener {
  private final CaptionPanel myComponent;
  private Point myStartPoint = null;

  public MoveComponentListener(final CaptionPanel component) {
    myComponent = component;
  }

  private void endOperation() {
    setCursor();
    myStartPoint = null;
  }

  public void mousePressed(MouseEvent e) {
    myStartPoint = new RelativePoint(e).getScreenPoint();
    final Point titleOffset = RelativePoint.getNorthWestOf(myComponent).getScreenPoint();
    myStartPoint.x -= titleOffset.x;
    myStartPoint.y -= titleOffset.y;
  }

  public void mouseClicked(MouseEvent e) {
    endOperation();
  }

  public void mouseReleased(MouseEvent e) {
    endOperation();
  }

  private void setCursor() {
    final Window wnd = SwingUtilities.getWindowAncestor(myComponent);
    if (wnd != null) {
      wnd.setCursor(Cursor.getDefaultCursor());
    }
  }

  public void mouseMoved(MouseEvent e) {
    if (e.isConsumed()) return;
    setCursor();
  }

  public void mouseDragged(MouseEvent e) {
    if (e.isConsumed()) return;
    setCursor();
    if (myStartPoint != null) {
      final Point draggedTo = new RelativePoint(e).getScreenPoint();
      draggedTo.x -= myStartPoint.x;
      draggedTo.y -= myStartPoint.y;

      final Window wnd = SwingUtilities.getWindowAncestor(myComponent);
      wnd.setLocation(draggedTo);
      e.consume();
    }
  }

  public void mouseEntered(MouseEvent e) {
    myComponent.setActive(true);
  }

  public void mouseExited(MouseEvent e) {
    myComponent.setActive(false);
  }
}
