/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class TextFieldWithHistory extends JPanel {

  private int myHistorySize = 5;
  private final MyModel myModel;
  private final TextFieldWithProcessing myTextField;

  private JBPopup myPopup;
  private JLabel myClearFieldLabel;
  private JLabel myToggleHistoryLabel;
  private JPopupMenu myNativeSearchPopup;

  private boolean myFreaze = false;
  private final boolean myCropList;

  private KeyListener myListener = null;

  public TextFieldWithHistory() {
    this(true);
  }

  public TextFieldWithHistory(final boolean cropList) {
    super(new BorderLayout());
    myCropList = cropList;
    myModel = new MyModel();

    myTextField = new TextFieldWithProcessing();
    myTextField.setColumns(15);
    add(myTextField, BorderLayout.CENTER);

    if (hasNativeLeopardSearchControl()) {
      myTextField.putClientProperty("JTextField.variant", "search");
      myNativeSearchPopup = new JPopupMenu();
      myTextField.putClientProperty("JTextField.Search.FindPopup", myNativeSearchPopup);
    }
    else {
      myToggleHistoryLabel = new JLabel(IconLoader.findIcon("/actions/search.png"));
      myToggleHistoryLabel.setOpaque(true);
      myToggleHistoryLabel.setBackground(myTextField.getBackground());
      myToggleHistoryLabel.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          togglePopup();
        }
      });
      add(myToggleHistoryLabel, BorderLayout.WEST);

      myClearFieldLabel = new JLabel(IconLoader.findIcon("/actions/clean.png"));
      myClearFieldLabel.setOpaque(true);
      myClearFieldLabel.setBackground(myTextField.getBackground());
      add(myClearFieldLabel, BorderLayout.EAST);
      myClearFieldLabel.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          myTextField.setText("");
        }
      });

      final Border originalBorder;
      if (SystemInfo.isMac) {
        originalBorder = BorderFactory.createLoweredBevelBorder();
      }
      else {
        originalBorder = myTextField.getBorder();
      }

      setBorder(new CompoundBorder(IdeBorderFactory.createEmptyBorder(4, 0, 4, 0), originalBorder));

      myTextField.setOpaque(true);
      myTextField.setBorder(IdeBorderFactory.createEmptyBorder(0, 5, 0, 5));

      // myTextField.addKeyListener(new HistoricalValuesHighlighter());
      myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
        protected void textChanged(DocumentEvent e) {
          if (!cropList) return;
          if (myFreaze) return; //do not suggest during batch update
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              String text = getTextEditor().getText();
              myModel.setSelectedItemAndCropList(text);
            }
          });

          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (0 == myModel.getSize()) {
                hidePopup();
                myModel.uncropList();
              }
              else {
                refreshPopup();
              }
            }
          });
        }

        private void refreshPopup() {
          Runnable hider = new Runnable() {
            public void run() {
              hidePopup();
            }
          };

          Runnable shower = new Runnable() {
            public void run() {
              showPopup();
            }
          };

          if (myModel.croppedListEnlarged()) {
            SwingUtilities.invokeLater(hider);
          }

          SwingUtilities.invokeLater(shower);
        }
      });
    }

    final ActionManager actionManager = ActionManager.getInstance();
    if (actionManager != null) {
      final AnAction clearTextAction = actionManager.getAction(IdeActions.ACTION_CLEAR_TEXT);
      if (clearTextAction.getShortcutSet().getShortcuts().length == 0) {
        clearTextAction.registerCustomShortcutSet(CommonShortcuts.ESCAPE, this);
      }
    }
  }

  private static boolean hasNativeLeopardSearchControl() {
    return SystemInfo.isMacOSLeopard && LafManager.getInstance().isUnderAquaLookAndFeel();
  }

  public void addDocumentListener(DocumentListener listener) {
    getTextEditor().getDocument().addDocumentListener(listener);
  }

  public void removeDocumentListener(DocumentListener listener) {
    getTextEditor().getDocument().removeDocumentListener(listener);
  }

  public void addKeyboardListener(final KeyListener listener) {
    getTextEditor().addKeyListener(listener);
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    final Color bg = enabled ? UIUtil.getTextFieldBackground() : UIUtil.getPanelBackground();
    myToggleHistoryLabel.setBackground(bg);
    myClearFieldLabel.setBackground(bg);
  }

  public void setHistorySize(int aHistorySize) {
    myHistorySize = aHistorySize;
  }

  public void setHistory(List<String> aHistory) {
    myModel.setItems(aHistory);
    if (myNativeSearchPopup != null) {
      myNativeSearchPopup.removeAll();
      for (final String item : aHistory) {
        addMenuItem(item);
      }
    }
  }

  public List getHistory() {
    return myModel.getItems();
  }

  public int getHistorySize() {
    return myHistorySize;
  }

  public void setText(String aText) {
    myFreaze = true;
    getTextEditor().setText(aText);
    myFreaze = false;
  }

  public String getText() {
    return getTextEditor().getText();
  }

  public void removeNotify() {
    super.removeNotify();
    hidePopup();
  }

  public void addCurrentTextToHistory() {
    final String item = getText();
    myModel.addElement(item);
  }

  private void addMenuItem(final String item) {
    if (myNativeSearchPopup != null) {
      final JMenuItem menuItem = new JMenuItem(item);
      myNativeSearchPopup.add(menuItem);
      menuItem.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myTextField.setText(item);
        }
      });
    }
  }

  public void selectText() {
    getTextEditor().selectAll();
  }

  public JTextField getTextEditor() {
    return myTextField;
  }


  public boolean requestFocusInWindow() {
    return myTextField.requestFocusInWindow();
  }

  public void requestFocus() {
    getTextEditor().requestFocus();
  }

  public class MyModel extends AbstractListModel {

    private List<String> myFullList = new ArrayList<String>();
    private List<String> myCroppedList = new ArrayList<String>();
    private String myCroppedListElementsPrefix = "";
    private int myLastCroppedListSize = 0;

    private Object mySelectedItem;

    public boolean croppedListEnlarged() {
      return myLastCroppedListSize < getSize();
    }

    public Object getElementAt(int index) {
      return myCropList ? myCroppedList.get(index) : myFullList.get(index);
    }

    public int getSize() {
      return myCropList ? myCroppedList.size() : myFullList.size();
    }

    public void addElement(Object obj) {
      String newItem = ((String)obj).trim();

      if (0 == newItem.length()) {
        return;
      }

      if (!contains(newItem)) {
        if (getSize() >= getHistorySize()) {
          insertElementAt(newItem, 0);
          removeElementAt(getSize() - 1);
        }
        else {
          myFullList.add(newItem);
        }

        refreshCroppedList();

        addMenuItem(newItem);
      }
    }

    public void insertElementAt(Object obj, int index) {
      myFullList.add(index, (String)obj);
      refreshCroppedList();
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
    public void removeElement(Object obj) {
      myFullList.remove(obj);
      refreshCroppedList();
    }

    public void removeElementAt(int index) {
      myFullList.remove(index);
      refreshCroppedList();
    }

    public Object getSelectedItem() {
      return mySelectedItem;
    }

    public void setSelectedItem(Object anItem) {
      mySelectedItem = anItem;
      refreshCroppedList();
    }

    private void refreshCroppedList() {
      if (myCropList) {
        if (null == getSelectedItem() && !myCroppedList.isEmpty()) {
          return;
        }
        myLastCroppedListSize = myCroppedList.size();

        myCroppedList = new ArrayList<String>();
        for (String item : myFullList) {
          if (item.startsWith(getCroppedListElementsPrefix()) && !item.equals(getCroppedListElementsPrefix())) {
            myCroppedList.add(item);
          }
        }
      }

      fireContentsChanged();
    }

    public void setSelectedItemAndCropList(String aItem) {
      setSelectedItem(aItem);
      myCroppedListElementsPrefix = aItem;
      refreshCroppedList();
    }

    public void uncropList() {
      myCroppedListElementsPrefix = "";
      refreshCroppedList();
    }

    public void fireContentsChanged() {
      fireContentsChanged(this, -1, -1);
    }

    public String getCroppedListElementsPrefix() {
      return myCroppedListElementsPrefix;
    }

    public boolean contains(String aNewValue) {
      return myFullList.contains(aNewValue);
    }

    public void setItems(List<String> aList) {
      myFullList = aList;
      uncropList();
      fireContentsChanged();
    }

    public List getItems() {
      return myFullList;
    }
  }

  private void hidePopup() {
    if (myPopup != null) {
      myPopup.cancel();
      myPopup = null;
    }
  }

  private void showPopup() {
    if (myPopup == null) {
      final JList list = new JList(myModel);
      if (myListener != null) {
        removeKeyListener(myListener);
      }
      final Runnable chooseRunnable = new Runnable() {
        public void run() {
          final String value = (String)list.getSelectedValue();
          getTextEditor().setText(value != null ? value : "");
          if (myPopup != null) {
            myPopup.cancel();
            myPopup = null;
          }
        }
      };
      myListener = new KeyAdapter() {
        public void keyPressed(KeyEvent e) {
          if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            if (list.getSelectedIndex() < list.getModel().getSize() - 1) {
              list.setSelectedIndex(list.getSelectedIndex() + 1);
            }
          }
          else if (e.getKeyCode() == KeyEvent.VK_UP) {
            if (list.getSelectedIndex() > 0) {
              list.setSelectedIndex(list.getSelectedIndex() - 1);
            }
          }
          else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (list.getSelectedIndex() > -1) {
              chooseRunnable.run();
            }
          }
        }
      };
      addKeyboardListener(myListener);
      myPopup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setMovable(false)
        .setRequestFocus(false)
        .setItemChoosenCallback(chooseRunnable).createPopup();

      if (isShowing()) myPopup.showUnderneathOf(this);
    }
  }

  private void togglePopup() {
    if (myPopup == null) {
      myModel.uncropList();
      showPopup();
    }
    else {
      hidePopup();
    }
  }

  public void setSelectedItem(final String s) {
    myFreaze = true;
    getTextEditor().setText(s);
    myFreaze = false;
  }

  public int getSelectedIndex() {
    return myModel.myCroppedList.indexOf(getText());
  }

  protected static class TextFieldWithProcessing extends JTextField {
    public void processKeyEvent(KeyEvent e) {
      super.processKeyEvent(e);
    }
  }
}