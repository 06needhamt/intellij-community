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
package com.intellij.ide.navigationToolbar;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.popup.list.DottedBorder;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * User: anna
 * Date: 03-Nov-2005
 */
public class HorizontalList extends JPanel {
  private ArrayList<Object> myModel = new ArrayList<Object>();
  private ArrayList<JLabel> myList = new ArrayList<JLabel>();

  private int myFirstIndex = 0;
  private int mySelectedIndex = -1;

  private JButton myLeftButton = new JButton(IconLoader.getIcon("/general/splitLeft.png"));
  private JButton myRightButton = new JButton(IconLoader.getIcon("/general/splitRight.png"));
  private JPanel myScrollablePanel = new JPanel(new GridBagLayout());
  private JScrollPane myScrollPane = ScrollPaneFactory.createScrollPane(myScrollablePanel);

  public HorizontalList() {
    this(ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public HorizontalList(Object [] objects) {
    setLayout(new BorderLayout());
    setBackground(UIUtil.getListBackground());

    myScrollablePanel.setBackground(UIUtil.getListBackground());
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

    add(myScrollPane, BorderLayout.CENTER);
    add(myLeftButton, BorderLayout.WEST);
    add(myRightButton, BorderLayout.EAST);

    myModel.addAll(Arrays.asList(objects));

    myLeftButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (mySelectedIndex == -1 && !myModel.isEmpty()){
          mySelectedIndex = myModel.size() - 1;
          paintBorder();
          myList.get(mySelectedIndex).requestFocusInWindow();
        }
        scrollToVisible(1);
        shiftFocusToVisible(1);
      }
    });
    myLeftButton.setBorder(null);

    myRightButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (mySelectedIndex == -1 && !myModel.isEmpty()){
          mySelectedIndex = myModel.size() - 1;
          paintBorder();
          myList.get(mySelectedIndex).requestFocusInWindow();
        }
        scrollToVisible(-1);
        shiftFocusToVisible(-1);
      }
    });
    myRightButton.setBorder(null);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        shiftFocus(-1);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), JComponent.WHEN_FOCUSED);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        shiftFocus(1);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), JComponent.WHEN_FOCUSED);

    registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (mySelectedIndex != -1) {
          getCtrlClickHandler(mySelectedIndex).run();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_FOCUSED);

    final ActionListener dblClickAction = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (mySelectedIndex != -1) {
          final Runnable doubleClickHandler = getDoubleClickHandler(mySelectedIndex);
          if (doubleClickHandler != null) {
            doubleClickHandler.run();
          }
        }
      }
    };
    registerKeyboardAction(dblClickAction, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
    registerKeyboardAction(dblClickAction, KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), JComponent.WHEN_FOCUSED);

    /*registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        select();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.ALT_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);*/

    registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        clearBorder();
        mySelectedIndex = -1;
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);


    updateList();
  }

  public void select() {
    if (!myList.isEmpty()){
      clearBorder();
      mySelectedIndex = myModel.size() - 1;
      paintBorder();
      while (!myList.get(mySelectedIndex).isShowing()){
        scrollToVisible(1);
      }
      myList.get(myModel.size() - 1).requestFocusInWindow();
    }
  }

  private void shiftFocusToVisible(int direction){
    while (mySelectedIndex != -1 && !myList.get(mySelectedIndex).isShowing()) {
      shiftFocus(direction);
    }
  }

  private void scrollToVisible(int direction){
    myFirstIndex = getIndexByMode(myFirstIndex + direction);
    paintComponent();
  }

  /**
   * @return was model modified
   */
  protected boolean updateModel(){
    return false;
  }


  public void addElement(Object object){
    myModel.add(object);
  }

  public void removeAllElements(){
    myModel.clear();
  }

  public int getSelectedIndex(){
    return mySelectedIndex;
  }

  public Object getSelectedValue(){
    return getElement(mySelectedIndex);
  }

  public JLabel getItem(int index){
    if (index != -1 && index < myModel.size()){
      return myList.get(index);
    }
    return null;
  }

  public Object getElement(int index){
    if (index != -1 && index < myModel.size()){
      return myModel.get(index);
    }
    return null;
  }

  public int getModelSize(){
    return myModel.size();
  }



  /**
   * to be invoked by alarm
   */
  protected void updateList() {
    immediateUpdateList(updateModel());
  }

  protected void immediateUpdateList(boolean update){
    if (update){
      myFirstIndex = 0;
      mySelectedIndex = -1;
      myList.clear();
      int index = 0;
      for (final Object object : myModel) {
        final JLabel label = new JLabel(getPresentableText(object), getIcon(object), SwingUtilities.RIGHT);
        clearBorder(label);
        label.setOpaque(true);
        label.setBackground(UIUtil.getListBackground());
        myList.add(label);
        installActions(index);
        index ++;
      }
    }
    paintComponent();
  }

  private void clearBorder(JLabel label){
    label.setBorder(BorderFactory.createEmptyBorder(2,2,2,4));
  }

  private void installDottedBorder(JLabel label){
    label.setBorder(new DottedBorder(new Insets(2,2,2,4), UIUtil.getListForeground()));
  }


  private void installActions(final int index) {
    final JLabel label = myList.get(index);
    final Runnable doubleClickHandler = getDoubleClickHandler(index);
    if (doubleClickHandler != null){
      label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
        public boolean value(final MouseEvent e) {
          return !e.isConsumed() && !e.isPopupTrigger() && e.getClickCount() == 2;
        }
      }, doubleClickHandler, index));
    }

    final Runnable ctrlClickHandler = getCtrlClickHandler(index);
    if (ctrlClickHandler != null){
      label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
        public boolean value(final MouseEvent e) {
          // You cannot distinguish between 3rd mouse button released with Meta down or not. See SunBug: 4029159
          if (e.getID() != MouseEvent.MOUSE_PRESSED && SystemInfo.isMac) return false;

          final int ex = e.getModifiersEx();
          return !e.isConsumed() && !e.isPopupTrigger() && (ex & (SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK)) != 0;
        }
      }, ctrlClickHandler, index));
    }

    final Runnable rightClickHandler = getRightClickHandler(index);
    if (rightClickHandler != null){
      label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
        public boolean value(final MouseEvent e) {
          return !e.isConsumed() && e.isPopupTrigger();
        }
      }, rightClickHandler, index));
    }

    label.addMouseListener(getMouseListener(new Condition<MouseEvent>() {
      public boolean value(final MouseEvent e) {
        return !e.isConsumed() && e.getClickCount() == 1 && !e.isPopupTrigger();
      }
    }, new Runnable() {
      public void run() {
        //just select
      }
    }, index));
  }

  private MouseListener getMouseListener(final Condition<MouseEvent> condition, final Runnable handler, final int index){
    return new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        onClick(e);
      }

      public void mousePressed(MouseEvent e) {
        onClick(e);
      }

      public void mouseReleased(MouseEvent e) {
        onClick(e);
      }

      private void onClick(MouseEvent e){
        if (condition.value(e)){
          clearBorder();
          mySelectedIndex = index;
          paintBorder();
          myList.get(mySelectedIndex).requestFocusInWindow();
          handler.run();
          e.consume();
        }
      }
    };
  }


  private void paintComponent() {

    myScrollablePanel.removeAll();
    myScrollablePanel.revalidate();
    GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 1, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0),0,0);
    int width = 0;
    int widthToTheRight = 0;
    final JLabel toBeContLabel = new JLabel("...");
    clearBorder(toBeContLabel);
    final int additionalWidth = toBeContLabel.getFontMetrics(toBeContLabel.getFont()).stringWidth("...") + 6;
    int wholeWidth = getWidth() - 2 * myLeftButton.getWidth();
    if (mySelectedIndex != -1) {
      myScrollablePanel.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
      if (myFirstIndex > 0){
        final JLabel preList = new JLabel("...");
        clearBorder(preList);
        myScrollablePanel.add(preList, gc);
        wholeWidth -= additionalWidth;
      }
      for (int i = 0; i < myList.size(); i++) {
        final JLabel linkLabel = myList.get(i);
        final Icon icon = linkLabel.getIcon();
        final int labelWidth = linkLabel.getFontMetrics(linkLabel.getFont()).stringWidth(linkLabel.getText()) + (icon != null ?  icon.getIconWidth() + linkLabel.getIconTextGap() : 0) + 6;
        width += labelWidth;
        if (i + 1 > myFirstIndex){
          widthToTheRight += labelWidth;
          if ( widthToTheRight < wholeWidth) {
            myScrollablePanel.add(linkLabel, gc);
          } else {
            myScrollablePanel.add(toBeContLabel, gc);
            break;
          }
        }
      }
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      myScrollablePanel.add(Box.createHorizontalBox(), gc);
    } else if (!myModel.isEmpty()){
      myScrollablePanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      myScrollablePanel.add(Box.createHorizontalBox(), gc);

      gc.weightx = 0;
      gc.fill = GridBagConstraints.NONE;
      final JLabel preselected = myList.get(myModel.size() - 1);
      installDottedBorder(preselected);
      for (int i = myModel.size() - 1; i >= 0; i--){
        final JLabel linkLabel = myList.get(i);
        final Icon icon = linkLabel.getIcon();
        width += linkLabel.getFontMetrics(linkLabel.getFont()).stringWidth(linkLabel.getText()) + (icon != null ?  icon.getIconWidth() + linkLabel.getIconTextGap() : 0) + 6;
        if (width + additionalWidth < wholeWidth || (i == 0 && width < wholeWidth)){
          myScrollablePanel.add(linkLabel, gc);
        } else {
          myFirstIndex = i + 1;
          myScrollablePanel.add(toBeContLabel, gc);
          wholeWidth -= additionalWidth;
          break;
        }
      }
    }

    final boolean scrollBarVisible = width >= wholeWidth;
    myLeftButton.setVisible(scrollBarVisible);
    myRightButton.setVisible(scrollBarVisible);
    myLeftButton.setEnabled(widthToTheRight >= wholeWidth && myFirstIndex < myModel.size() - 2);
    myRightButton.setEnabled(myFirstIndex > 0);
    myScrollablePanel.revalidate();
    myScrollablePanel.repaint();
  }

  public Dimension getPreferredSize() {
    return new JButton("1").getPreferredSize();
  }

  private int getIndexByMode(int index){
    if (index < 0) return myModel.size() + index;
    if (index >= myModel.size()) return index % myModel.size();
    return index;
  }

  private void paintBorder(){
    final JLabel focusedLabel = myList.get(mySelectedIndex);
    installDottedBorder(focusedLabel);
    focusedLabel.setBackground(UIUtil.getListSelectionBackground());
    focusedLabel.setForeground(UIUtil.getListSelectionForeground());
  }

  private void clearBorder(){
    if (mySelectedIndex != -1) {
      final JLabel focusLostLabel = myList.get(mySelectedIndex);
      focusLostLabel.setBackground(UIUtil.getListBackground());
      focusLostLabel.setForeground(UIUtil.getListForeground());
      clearBorder(focusLostLabel);
    } else if (!myModel.isEmpty()) {
      clearBorder(myList.get(myModel.size() - 1));
    }
  }

  protected void shiftFocus(int direction){
    clearBorder();
    mySelectedIndex = getIndexByMode(mySelectedIndex + direction);
    paintBorder();
    while (!myList.get(mySelectedIndex).isShowing()){
      scrollToVisible(direction);
    }
  }

  protected String getPresentableText(Object object){
    return object.toString();
  }

  @Nullable
  protected Icon getIcon(Object object){
    return null;
  }

  @Nullable
  protected Runnable getDoubleClickHandler(int index){
    return null;
  }

  protected Runnable getCtrlClickHandler(int index){
    return null;
  }

  protected Runnable getRightClickHandler(int index){
    return null;
  }
}
