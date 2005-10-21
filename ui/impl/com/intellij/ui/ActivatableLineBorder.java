package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import java.awt.*;

public class ActivatableLineBorder implements Border {
  private static Color ACTIVE_COLOR = new Color(160, 186, 213);
  private static Color INACTIVE_COLOR = new Color(128, 128, 128);

  private boolean active = false;

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Insets getBorderInsets(Component c) {
    return new Insets(1, 1, 1, 1);
  }

  public boolean isBorderOpaque() {
    return false;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    g.setColor(active ? ACTIVE_COLOR : INACTIVE_COLOR);
    UIUtil.drawLine(g, x + 1, y, x + width - 2, y);
    UIUtil.drawLine(g, x + 1, y + height - 1, x + width - 2, y + height - 1);
    UIUtil.drawLine(g, x, y + 1, x, y + height - 2);
    UIUtil.drawLine(g, x + width - 1, y + 1, x + width - 1, y + height - 2);
  }
}
