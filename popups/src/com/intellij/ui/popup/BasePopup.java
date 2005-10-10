/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.Popup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.TreePopupStep;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.labels.BoldLabel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.ui.popup.util.ElementFilter;
import com.intellij.ui.popup.util.MnemonicsSearch;
import com.intellij.ui.popup.util.SpeedSearch;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ui.BlockBorder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public abstract class BasePopup implements ActionListener, ElementFilter, Popup {

  private static final int AUTO_POPUP_DELAY = 1000;
  protected static final Color TITLE_BACKGROUND = new Color(180, 193, 215);
  protected static final Dimension MAX_SIZE = new Dimension(Integer.MAX_VALUE, 400);

  protected static final int STEP_X_PADDING = 2;

  protected javax.swing.Popup myPopup;

  protected BasePopup myParent;

  protected JPanel myContainer;

  protected PopupStep myStep;
  protected BasePopup myChild;

  protected JScrollPane myScrollPane;
  protected JLabel myTitle;

  protected JComponent myContent;

  private Timer myAutoSelectionTimer = new Timer(AUTO_POPUP_DELAY, this);

  private SpeedSearch mySpeedSearch = new SpeedSearch() {
    protected void update() {
      onSpeedSearchPatternChanged();
      mySpeedSearchPane.update();
    }
  };

  private SpeedSearchPane mySpeedSearchPane;

  private MnemonicsSearch myMnemonicsSearch;
  protected Object myParentValue;
  private Component myOldFocusOwner;

  public BasePopup(PopupStep aStep) {
    this(null, aStep);
  }

  public BasePopup(Popup aParent, PopupStep aStep) {
    myParent = (BasePopup) aParent;
    myStep = aStep;

    if (myStep.isSpeedSearchEnabled() && myStep.isMnemonicsNavigationEnabled()) {
      throw new IllegalArgumentException("Cannot have both options enabled at the same time: speed search and mnemonics navigation");
    }

    mySpeedSearch.setEnabled(myStep.isSpeedSearchEnabled());

    myContainer = new MyContainer();

    mySpeedSearchPane = new SpeedSearchPane(this);

    myContent = createContent();

    myScrollPane = new JScrollPane(myContent);
    myScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.getHorizontalScrollBar().setBorder(null);

    myScrollPane.getActionMap().get("unitScrollLeft").setEnabled(false);
    myScrollPane.getActionMap().get("unitScrollRight").setEnabled(false);

    myScrollPane.setBorder(null);
    myContainer.add(myScrollPane, BorderLayout.CENTER);

    if (!SystemInfo.isMac) {
      myContainer.setBorder(new BlockBorder());
    }

    myTitle = new BoldLabel(aStep.getTitle());
    myTitle.setOpaque(true);
    myTitle.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
    myTitle.setBackground(TITLE_BACKGROUND);

    myContainer.add(myTitle, BorderLayout.NORTH);

    registerAction("disposeAll", KeyEvent.VK_ESCAPE, KeyEvent.SHIFT_MASK, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (mySpeedSearch.isHoldingFilter()) {
          mySpeedSearch.reset();
        }
        else {
          disposeAll();
        }
      }
    });

    AbstractAction goBackAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        goBack();
      }
    };

    registerAction("goBack3", KeyEvent.VK_ESCAPE, 0, goBackAction);

    myMnemonicsSearch = new MnemonicsSearch(this) {
      protected void select(Object value) {
        BasePopup.this.onSelectByMnemonic(value);
      }
    };



  }

  private void disposeAll() {
    BasePopup root = PopupDispatcher.getActiveRoot();
    disposeAllParents();
    root.getStep().canceled();
  }

  protected void goBack() {
    if (mySpeedSearch.isHoldingFilter()) {
      mySpeedSearch.reset();
      return;
    }

    if (myParent != null) {
      myParent.disposeChildren();
    }
    else {
      disposeAll();
    }
  }

//  public void setModal(boolean isModal) {
//    myPopup.setModal(true);
//  }

  protected abstract JComponent createContent();

  protected void dispose() {
    myAutoSelectionTimer.stop();

    if (myPopup == null) return;

    myPopup.hide();
    mySpeedSearchPane.dispose();

    PopupDispatcher.unsetShowing(this);
    PopupDispatcher.clearRootIfNeeded(this);

    myPopup = null;
    myContainer = null;

    if (myOldFocusOwner != null && myOldFocusOwner.isShowing()) {
      myOldFocusOwner.requestFocus();
    }
  }

  public void disposeChildren() {
    if (myChild != null) {
      myChild.disposeChildren();
      myChild.dispose();
      myChild = null;
    }
  }

  public final void show(RelativePoint aPoint) {
    show((JComponent) aPoint.getComponent(), aPoint.getScreenPoint().x, aPoint.getScreenPoint().y);
  }

  protected void beforeShow() {

  }

  protected void afterShow() {

  }

  public final void show(Component owner, int aScreenX, int aScreenY) {
    if (myContainer == null) {
      throw new IllegalStateException("Wizard dialog was already disposed. Recreate a new instance to show the wizard again");
    }

    myOldFocusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    beforeShow();

    Rectangle targetBounds = new Rectangle(new Point(aScreenX, aScreenY), myContainer.getPreferredSize());
    ScreenUtil.moveRectangleToFitTheScreen(targetBounds);

    if (getParent() != null) {
      if (getParent().getBounds().intersects(targetBounds)) {
        targetBounds.x = getParent().getBounds().x - targetBounds.width - STEP_X_PADDING;
      }
    }

    if (getParent() == null) {
      PopupDispatcher.setActiveRoot(this);
    }
    else {
      PopupDispatcher.setShowing(this);
    }

    myPopup = PopupFactory.getSharedInstance().getPopup(owner, myContainer, targetBounds.x, targetBounds.y);
    myPopup.show();

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        requestFocus();
        afterShow();
      }
    });
  }

  protected abstract void requestFocus();

  public void cancel() {
    disposeChildren();
    dispose();
    getStep().canceled();
  }

  public void showInCenterOf(Component aContainer) {
    final JComponent component = getTargetComponent(aContainer);

    Point containerScreenPoint = component.getVisibleRect().getLocation();
    SwingUtilities.convertPointToScreen(containerScreenPoint, aContainer);

    final Point popupPoint = getCenterPoint(new Rectangle(containerScreenPoint, component.getVisibleRect().getSize()), myContainer.getPreferredSize());
    show(aContainer, popupPoint.x, popupPoint.y);
  }

  private JComponent getTargetComponent(Component aComponent) {
    if (aComponent instanceof JComponent) {
      return (JComponent) aComponent;
    }
    else if (aComponent instanceof JFrame) {
      return ((JFrame) aComponent).getRootPane();
    }
    else {
      return ((JDialog) aComponent).getRootPane();
    }
  }

  public void showUnderneathOf(Component aComponent) {
    final JComponent component = getTargetComponent(aComponent);

    final Point point = aComponent.getLocationOnScreen();
    point.y += component.getVisibleRect().height + 4;
    show(aComponent, point.x, point.y);
  }

  private Point getCenterPoint(Rectangle aContainerRec, Dimension aPopupSize) {
    Point result = new Point();

    Point containerLocation = aContainerRec.getLocation();
    Dimension containerSize = aContainerRec.getSize();

    result.x = containerLocation.x + ((containerSize.width / 2) - aPopupSize.width / 2);
    result.y = containerLocation.y + ((containerSize.height / 2) - aPopupSize.height / 2);

    return result;
  }

  protected void disposeAllParents() {
    dispose();
    if (myParent != null) {
      myParent.disposeAllParents();
    }
  }

  protected final void registerAction(String aActionName, int aKeyCode, int aModifier, Action aAction) {
    getInputMap().put(KeyStroke.getKeyStroke(aKeyCode, aModifier), aActionName);
    getActionMap().put(aActionName, aAction);
  }

  protected abstract InputMap getInputMap();
  protected abstract ActionMap getActionMap();

  protected Dimension getContentPreferredSize() {
    return myContent.getPreferredSize();
  }

  protected final void setParentValue(Object parentValue) {
    myParentValue = parentValue;
  }

  private class MyContainer extends OpaquePanel implements DataProvider {
    MyContainer() {
      super(new BorderLayout(), Color.white);
      setFocusCycleRoot(true);
    }

    public Object getData(String dataId) {
      return null;
    }

    public Dimension getPreferredSize() {
      final Dimension ofContent = getContentPreferredSize();

      ofContent.height += 30;
      ofContent.width = Math.max(ofContent.width, myTitle.getPreferredSize().width);
      ofContent.width += myScrollPane.getVerticalScrollBar().getPreferredSize().width + 4;
      return computeNotBiggerDimension(ofContent);
    }

    private Dimension computeNotBiggerDimension(Dimension ofContent) {
      int resultWidth = (ofContent.width > MAX_SIZE.width) ? MAX_SIZE.width : ofContent.width;
      int resultHeight = (ofContent.height > MAX_SIZE.height) ? MAX_SIZE.height : ofContent.height;

      return new Dimension(resultWidth, resultHeight);
    }

  }

  public BasePopup getParent() {
    return myParent;
  }

  public PopupStep getStep() {
    return myStep;
  }

  public final void dispatch(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED) {
      return;
    }
    final KeyStroke stroke = KeyStroke.getKeyStroke(event.getKeyChar(), event.getModifiers(), false);
    if (getInputMap().get(stroke) != null) {
      final Action action = getActionMap().get(getInputMap().get(stroke));
      if (action.isEnabled()) {
        action.actionPerformed(new ActionEvent(myContent, event.getID(), "", event.getWhen(), event.getModifiers()));
        return;
      }
    }

    process(event);
    mySpeedSearch.process(event);
    myMnemonicsSearch.process(event);
  }

  protected void process(KeyEvent aEvent) {

  }

  public Rectangle getBounds() {
    return new Rectangle(myContainer.getLocationOnScreen(), myContainer.getSize());
  }

//  public Window getWindow() {
//    return myPopup;
//  }

  public JComponent getContainer() {
    return myContainer;
  }

  protected BasePopup createPopup(BasePopup parent, PopupStep step, Object parentValue) {
    if (step instanceof ListPopupStep) {
      return new ListPopupImpl(parent, step, parentValue);
    }
    else if (step instanceof TreePopupStep) {
      return new TreePopupImpl(parent, (TreePopupStep) step, parentValue);
    }
    else {
      throw new IllegalArgumentException(step.getClass().toString());
    }
  }

  public final void actionPerformed(ActionEvent e) {
    myAutoSelectionTimer.stop();
    if (getStep().isAutoSelectionEnabled()) {
      onAutoSelectionTimer();
    }
  }

  protected final void restartTimer() {
    if (!myAutoSelectionTimer.isRunning()) {
      myAutoSelectionTimer.start();
    }
    else {
      myAutoSelectionTimer.restart();
    }
  }

  protected final void stopTimer() {
    myAutoSelectionTimer.stop();
  }

  protected void onAutoSelectionTimer() {

  }

  protected void onSpeedSearchPatternChanged() {
  }

  public boolean shouldBeShowing(Object value) {
    if (!myStep.isSpeedSearchEnabled()) return true;
    if (!myStep.getSpeedSearchFilter().canBeHidden(value)) return true;
    String text = myStep.getSpeedSearchFilter().getIndexedString(value);
    return mySpeedSearch.shouldBeShowing(text);
  }

  public SpeedSearch getSpeedSearch() {
    return mySpeedSearch;
  }

  JLabel getTitle() {
    return myTitle;
  }

  protected void onSelectByMnemonic(Object value) {

  }

  protected abstract void onChildSelectedFor(Object value);

  protected final void notifyParentOnChildSelection() {
    if (myParent == null || myParentValue == null) return;
    myParent.onChildSelectedFor(myParentValue);
  }

}
