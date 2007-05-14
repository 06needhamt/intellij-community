/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.treetable.TreeTableCellRenderer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author max
 */
public class UIUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.ui.UIUtil");
  public static final @NonNls String HTML_MIME = "text/html";
  public static final char MNEMONIC = 0x1B;
  @NonNls public static final String JSLIDER_ISFILLED = "JSlider.isFilled";
  @NonNls public static final String ARIAL_FONT_NAME = "Arial";
  @NonNls public static final String TABLE_FOCUS_CELL_BACKGROUND_PROPERTY = "Table.focusCellBackground";

  private UIUtil() {}

  public static boolean isReallyTypedEvent(KeyEvent e) {
    char c = e.getKeyChar();
    if (!(c >= 0x20 && c != 0x7F)) return false;

    int modifiers = e.getModifiers();
    if (SystemInfo.isMac) {
      return !e.isMetaDown() && !e.isControlDown();
    }

    return (modifiers & ActionEvent.ALT_MASK) == (modifiers & ActionEvent.CTRL_MASK);
  }

  public static void setEnabled(Component component, boolean enabled, boolean recursively) {
    component.setEnabled(enabled);
    if (recursively) {
      if (component instanceof Container) {
        final Container container = ((Container)component);
        final int subComponentCount = container.getComponentCount();
        for (int i = 0; i < subComponentCount; i++) {
          setEnabled(container.getComponent(i), enabled, recursively);
        }

      }
    }
  }

  public static void updateFrameIcon(final Frame frame) {
    final Image image = ImageLoader.loadFromResource("/icon.png");
    frame.setIconImage(image);
  }

  public static void drawLine(Graphics g, int x1, int y1, int x2, int y2) {
    g.drawLine(x1, y1, x2, y2);
  }

  public static void setActionNameAndMnemonic(String text, Action action) {
    int mnemoPos = text.indexOf("&");
    if (mnemoPos >= 0 && mnemoPos < text.length() - 2) {
      String mnemoChar = text.substring(mnemoPos + 1, mnemoPos + 2).trim();
      if (mnemoChar.length() == 1) {
        action.putValue(Action.MNEMONIC_KEY, Integer.valueOf((int)mnemoChar.charAt(0)));
      }
    }

    text = text.replaceAll("&", "");
    action.putValue(Action.NAME, text);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Font getLabelFont() {
    return UIManager.getFont("Label.font");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getLabelBackground() {
    return UIManager.getColor("Label.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getLabelForeground() {
    return UIManager.getColor("Label.foreground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Icon getOptionPanelWarningIcon() {
    return UIManager.getIcon("OptionPane.warningIcon");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Icon getOptionPanelQuestionIcon() {
    return UIManager.getIcon("OptionPane.questionIcon");
  }

  public static String replaceMnemonicAmpersand(final String value) {
    if (value.indexOf('&') >= 0) {
      boolean useMacMnemonic = value.indexOf("&&") >= 0;
      StringBuffer realValue = new StringBuffer();
      int i = 0;
      while (i < value.length()) {
        char c = value.charAt(i);
        if (c == '\\') {
          if (i < value.length() - 1 && value.charAt(i + 1) == '&') {
            realValue.append('&');
            i++;
          } else {
            realValue.append(c);
          }
        } else if (c == '&') {
          if (i < value.length() - 1 && value.charAt(i + 1) == '&') {
            if (SystemInfo.isMac) {
              realValue.append(MNEMONIC);
            }
            i++;
          } else {
            if (!SystemInfo.isMac || !useMacMnemonic) {
              realValue.append(MNEMONIC);
            }
          }
        } else {
          realValue.append(c);
        }
        i++;
      }

      return realValue.toString();
    }
    return value;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTableHeaderBackground() {
    return UIManager.getColor("TableHeader.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTreeTextForeground() {
    return UIManager.getColor("Tree.textForeground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTreeSelectonForeground() {
    return UIManager.getColor("Tree.selectionForeground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTreeSelectionBackground() {
    return UIManager.getColor("Tree.selectionBackground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTreeTextBackground() {
    return UIManager.getColor("Tree.textBackground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getListSelectionForeground() {
     return UIManager.getColor("List.selectionForeground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getFieldForegroundColor() {
    return UIManager.getColor("field.foreground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTableSelectionBackground() {
    return UIManager.getColor("Table.selectionBackground");
  }


  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getActiveTextColor() {
    return UIManager.getColor("textActiveText");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getInactiveTextColor() {
    return UIManager.getColor("textInactiveText");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Font getTreeFont() {
    return UIManager.getFont("Tree.font");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Font getListFont() {
    return UIManager.getFont("List.font");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTreeSelectionForeground() {
    return UIManager.getColor("Tree.selectionForeground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTextInactiveTextColor() {
    return UIManager.getColor("textInactiveText");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void installPopupMenuColorAndFonts(final JComponent contentPane) {
    LookAndFeel.installColorsAndFont(contentPane, "PopupMenu.background", "PopupMenu.foreground", "PopupMenu.font");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void installPopupMenuBorder(final JComponent contentPane) {
    LookAndFeel.installBorder(contentPane, "PopupMenu.border");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isMotifLookAndFeel() {
    return "Motif".equals(UIManager.getLookAndFeel().getID());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTreeSelectionBorderColor() {
    return UIManager.getColor("Tree.selectionBorderColor");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Object getTreeRightChildIndent() {
    return UIManager.get("Tree.rightChildIndent");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Object getTreeLeftChildIndent() {
    return UIManager.get("Tree.leftChildIndent");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  static public Color getToolTipBackground() {
    return UIManager.getColor("ToolTip.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  static public Color getToolTipForeground() {
    return UIManager.getColor("ToolTip.foreground");
  }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public static Color getComboBoxDisabledForeground() {
    return UIManager.getColor("ComboBox.disabledForeground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getComboBoxDisabledBackground() {
    return UIManager.getColor("ComboBox.disabledBackground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getButtonSelectColor() {
    return UIManager.getColor("Button.select");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Integer getPropertyMaxGutterIconWidth(final String propertyPrefix) {
    return (Integer)UIManager.get(propertyPrefix + ".maxGutterIconWidth");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getMenuItemDisabledForeground() {
    return UIManager.getColor("MenuItem.disabledForeground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Object getMenuItemDisabledForegroundObject() {
    return UIManager.get("MenuItem.disabledForeground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Object getTabbedPanePaintContentBorder(final JComponent c) {
    return c.getClientProperty("TabbedPane.paintContentBorder");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isMenuCrossMenuMnemonics() {
    return UIManager.getBoolean("Menu.crossMenuMnemonic");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTableBackground() {
    return UIManager.getColor("Table.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTableSelectionForeground() {
    return UIManager.getColor("Table.selectionForeground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTableForeground() {
    return UIManager.getColor("Table.foreground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getListBackground() {
     return UIManager.getColor("List.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getListForeground() {
    return UIManager.getColor("List.foreground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getPanelBackground() {
    return UIManager.getColor("Panel.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTreeForeground() {
    return UIManager.getColor("Tree.foreground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTableFocusCellBackground() {
     return UIManager.getColor("Table.focusCellBackground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getListSelectionBackground() {
    return UIManager.getColor("List.selectionBackground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTextFieldForeground() {
    return UIManager.getColor("TextField.foreground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTextFieldBackground() {
    return UIManager.getColor("TextField.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Font getButtonFont() {
    return UIManager.getFont("Button.font");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Font getToolTipFont() {
    return UIManager.getFont("ToolTip.font");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTabbedPaneBackground() {
    return UIManager.getColor("TabbedPane.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void setSliderIsFilled(final JSlider slider, final boolean value) {
    slider.putClientProperty("JSlider.isFilled", Boolean.valueOf(value));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getLabelTextForeground() {
    return UIManager.getColor("Label.textForeground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getControlColor() {
    return UIManager.getColor("control");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Font getOptionPaneMessageFont() {
    return UIManager.getFont("OptionPane.messageFont");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getSeparatorShadow() {
    return  UIManager.getColor("Separator.shadow");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Font getMenuFont() {
    return UIManager.getFont("Menu.font");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getSeparatorHighlight() {
    return UIManager.getColor("Separator.highlight");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Border getTableFocusCellHighlightBorder() {
    return  UIManager.getBorder("Table.focusCellHighlightBorder");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void setLineStyleAngled(final TreeTableCellRenderer component) {
      component.putClientProperty("JTree.lineStyle", "Angled");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void setLineStyleAngled(final JTree component) {
      component.putClientProperty("JTree.lineStyle", "Angled");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTableFocusCellForeground() {
    return UIManager.getColor("Table.focusCellForeground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getPanelBackgound() {
    return UIManager.getColor("Panel.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Border getTextFieldBorder() {
    return UIManager.getBorder("TextField.border");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Border getButtonBorder() {
    return UIManager.getBorder("Button.border");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Icon getErrorIcon() {
    return UIManager.getIcon("OptionPane.errorIcon");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Icon getInformationIcon() {
    return UIManager.getIcon("OptionPane.informationIcon");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Icon getQuestionIcon() {
    return UIManager.getIcon("OptionPane.questionIcon");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Icon getWarningIcon() {
    return UIManager.getIcon("OptionPane.warningIcon");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Icon getRadioButtonIcon() {
    return UIManager.getIcon("RadioButton.icon");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Icon getTreeCollapsedIcon() {
    return UIManager.getIcon("Tree.collapsedIcon");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Icon getTreeExpandedIcon() {
    return UIManager.getIcon("Tree.expandedIcon");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Border getTableHeaderCellBorder() {
    return UIManager.getBorder("TableHeader.cellBorder");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getWindowColor() {
    return UIManager.getColor("window");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getTextAreaForeground() {
    return UIManager.getColor("TextArea.foreground");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static Color getOptionPaneBackground() {
    return UIManager.getColor("OptionPane.background");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static boolean isUnderQuaquaLookAndFeel() {
    return UIManager.getLookAndFeel().getName().indexOf("Quaqua") >= 0;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void removeQuaquaVisualMarginsIn(Component component) {
    if (component instanceof JComponent ) {
      final JComponent jComponent = (JComponent)component;
      final Component[] children = jComponent.getComponents();
      for (Component child : children) {
        removeQuaquaVisualMarginsIn(child);
      }

      jComponent.putClientProperty("Quaqua.Component.visualMargin", new Insets(0, 0, 0, 0));
    }
  }

  public static boolean isControlKeyDown(MouseEvent mouseEvent) {
    return SystemInfo.isMac ? mouseEvent.isMetaDown() : mouseEvent.isControlDown();
  }

  public static String[] getValidFontNames(final boolean familyName) {
    Set<String> result = new TreeSet<String>();
    Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
    for (Font font : fonts) {
      // Adds fonts that can display symbols at [A, Z] + [a, z] + [0, 9]
      try {
        if (
          font.canDisplay('a') &&
          font.canDisplay('z') &&
          font.canDisplay('A') &&
          font.canDisplay('Z') &&
          font.canDisplay('0') &&
          font.canDisplay('1')
          ) {
          result.add(familyName ? font.getFamily() : font.getName());
        }
      }
      catch (Exception e) {
        // JRE has problems working with the font. Just skip.
      }
    }
    return result.toArray(new String[result.size()]);
  }

  public static String[] getStandardFontSizes() {
    return new String[]{"8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "26", "28", "36", "48", "72"};
  }

  public static void setupEnclosingDialogBounds(final JComponent component) {
    component.revalidate();
    component.repaint();
    final Window window = SwingUtilities.windowForComponent(component);
    if (window != null &&
        (window.getSize().height < window.getMinimumSize().height ||
         window.getSize().width < window.getMinimumSize().width)) {
      window.pack();
    }
  }

  public static String displayPropertiesToCSS(Font font, Color fg) {
    StringBuffer rule = new StringBuffer("body {");
    if (font != null) {
      rule.append(" font-family: ");
      rule.append(font.getFamily());
      rule.append(" ; ");
      rule.append(" font-size: ");
      rule.append(font.getSize());
      rule.append("pt ;");
      if (font.isBold()) {
        rule.append(" font-weight: 700 ; ");
      }
      if (font.isItalic()) {
        rule.append(" font-style: italic ; ");
      }
    }
    if (fg != null) {
      rule.append(" color: #");
      if (fg.getRed() < 16) {
        rule.append('0');
      }
      rule.append(Integer.toHexString(fg.getRed()));
      if (fg.getGreen() < 16) {
        rule.append('0');
      }
      rule.append(Integer.toHexString(fg.getGreen()));
      if (fg.getBlue() < 16) {
        rule.append('0');
      }
      rule.append(Integer.toHexString(fg.getBlue()));
      rule.append(" ; ");
    }
    rule.append(" }");
    return rule.toString();
  }

  /**
   * @param g
   * @param x top left X coordinate.
   * @param y top left Y coordinate.
   * @param x1 right bottom X coordinate.
   * @param y1 right bottom Y coordinate.
   */
  public static void drawDottedRectangle(Graphics g, int x, int y, int x1, int y1) {
    int i1;
    for(i1 = x; i1 <= x1; i1 += 2){
      drawLine(g, i1, y, i1, y);
    }

    for(i1 = i1 != x1 + 1 ? y + 2 : y + 1; i1 <= y1; i1 += 2){
      drawLine(g, x1, i1, x1, i1);
    }

    for(i1 = i1 != y1 + 1 ? x1 - 2 : x1 - 1; i1 >= x; i1 -= 2){
      drawLine(g, i1, y1, i1, y1);
    }

    for(i1 = i1 != x - 1 ? y1 - 2 : y1 - 1; i1 >= y; i1 -= 2){
      drawLine(g, x, i1, x, i1);
    }
  }

  public static void applyRenderingHints(final Graphics g) {
    Toolkit tk = Toolkit.getDefaultToolkit();
    //noinspection HardCodedStringLiteral
    Map map = (Map)(tk.getDesktopProperty("awt.font.desktophints"));
    if (map != null) {
        ((Graphics2D) g).addRenderingHints(map);
    }
  }

  public static void dispatchAllInvocationEvents() {
    final EventQueue eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue();
    while (true) {
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) break;
      try {
        AWTEvent event1 = eventQueue.getNextEvent();
        if (event1 instanceof InvocationEvent) {
          ((InvocationEvent)event1).dispatch();
        }
      }
      catch (Exception e) {
        LOG.error(e); //?
      }
    }
  }
  public static void pump() {
    assert !SwingUtilities.isEventDispatchThread();
    final BlockingQueue queue = new LinkedBlockingQueue();
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        queue.offer(queue);
      }
    });
    try {
      queue.take();
    }
    catch (InterruptedException e) {
      LOG.error(e);
    }
  }

  public static void addAwtListener(final AWTEventListener listener, long mask, Disposable parent) {
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, mask);
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
      }
    });
  }

  public static void drawVDottedLine(Graphics2D g, int lineX, int startY, int endY, final Color bgColor, final Color fgColor) {
    g.setColor(bgColor);
    drawLine(g, lineX, startY, lineX, endY);

    g.setColor(fgColor);

    for (int i = (startY / 2) * 2; i < endY; i += 2) {
      g.drawRect(lineX, i, 0, 0);
    }
  }

  public static void drawHDottedLine(Graphics2D g, int startX, int endX, int lineY, final Color bgColor, final Color fgColor) {
    g.setColor(bgColor);
    drawLine(g, startX, lineY, endX, lineY);

    g.setColor(fgColor);

    for (int i = (startX / 2) * 2; i < endX; i += 2) {
      g.drawRect(i, lineY, 0, 0);
    }
  }

  public static void drawDottedLine(Graphics2D g, int x1, int y1, int x2, int y2, final Color bgColor, final Color fgColor) {
    if (x1 == x2) drawVDottedLine(g, x1, y1, y2, bgColor, fgColor);
    else if (y1 == y2) drawHDottedLine(g, x1, x2, y1, bgColor, fgColor);
    else throw new IllegalArgumentException("Only vertical or horizontal lines are supported");
  }

  public static boolean isFocusAncestor(@NotNull final JComponent component) {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (owner == null) return false;
    if (owner == component) return true;
    return SwingUtilities.isDescendingFrom(owner, component);
  }
}

