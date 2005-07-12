package com.intellij.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.wm.ex.LayoutFocusTraversalPolicyExt;
import gnu.trove.THashMap;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.*;
import java.util.EventListener;
import java.util.EventObject;
import java.util.Map;

public class LightweightHint implements Hint, UserDataHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.LightweightHint");

  private final MyResizableComponent myComponent;
  private JComponent myFocusBackComponent;
  private final Map<Key,Object> myUserMap = new THashMap<Key, Object>(1);
  private final EventListenerList myListenerList = new EventListenerList();
  private MyEscListener myEscListener;

  public LightweightHint(final JComponent component) {
    LOG.assertTrue(component != null);
    myComponent = new MyResizableComponent(component);
  }

  /**
   * Shows the hint in the layered pane. Coordinates <code>x</code> and <code>y</code>
   * are in <code>parentComponent</code> coordinate system. Note that the component
   * appears on 250 layer.
   */
  public void show(final JComponent parentComponent, final int x, final int y, final JComponent focusBackComponent) {
    LOG.assertTrue(parentComponent != null);

    myFocusBackComponent = focusBackComponent;

    final Dimension preferredSize = myComponent.getComponent().getPreferredSize();

    LOG.assertTrue(parentComponent.isShowing());
    myEscListener = new MyEscListener();
    myComponent.registerKeyboardAction(myEscListener,
                                       KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                       JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    final JLayeredPane layeredPane = parentComponent.getRootPane().getLayeredPane();
    final Point layeredPanePoint = SwingUtilities.convertPoint(parentComponent, x, y, layeredPane);

    myComponent.setBounds(layeredPanePoint.x, layeredPanePoint.y, preferredSize.width, preferredSize.height);

    layeredPane.add(myComponent, new Integer(250 + layeredPane.getComponentCount()));

    myComponent.validate();
    myComponent.repaint();
  }

  private void fireHintHidden() {
    final EventListener[] listeners = myListenerList.getListeners(HintListener.class);
    for (int i = 0; i < listeners.length; i++) {
      final HintListener listener = (HintListener)listeners[i];
      listener.hintHidden(new EventObject(this));
    }
  }

  /**
   * Sets location of the hint in the layered pane coordinate system.
   */
  public final void setLocation(final int x, final int y) {
    myComponent.setLocation(x, y);
    myComponent.validate();
    myComponent.repaint();
  }

  /**
   * x and y are in layered pane coordinate system
   */
  public final void setBounds(final int x, final int y, final int width, final int height) {
    myComponent.setBounds(x, y, width, height);
    myComponent.setLocation(x, y);
    myComponent.validate();
    myComponent.repaint();
  }

  /**
   * @return bounds of hint component in the layered pane.
   */
  public final Rectangle getBounds() {
    return myComponent.getBounds();
  }

  public boolean isVisible() {
    return myComponent.isShowing();
  }

  public void hide() {
    if (isVisible()) {
      final Rectangle bounds = myComponent.getBounds();
      final JLayeredPane layeredPane = myComponent.getRootPane().getLayeredPane();

      try {
        if(myFocusBackComponent != null){
          LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(myFocusBackComponent);
        }
        layeredPane.remove(myComponent);
      }
      finally {
        LayoutFocusTraversalPolicyExt.setOverridenDefaultComponent(null);
      }
      layeredPane.paintImmediately(bounds.x, bounds.y, bounds.width, bounds.height);
    }
    if (myEscListener != null) {
      myComponent.unregisterKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
    }
    fireHintHidden();
  }

  public final JComponent getComponent() {
    return myComponent.getComponent();
  }

  public <T> T getUserData(final Key<T> key) {
    return (T)myUserMap.get(key);
  }

  public <T> void putUserData(final Key<T> key, final T value) {
    if (value != null) {
      myUserMap.put(key, value);
    }
    else {
      myUserMap.remove(key);
    }
  }

  public final void addHintListener(final HintListener listener) {
    myListenerList.add(HintListener.class, listener);
  }

  public final void removeHintListener(final HintListener listener) {
    myListenerList.remove(HintListener.class, listener);
  }

  private final class MyEscListener implements ActionListener {
    public final void actionPerformed(final ActionEvent e) {
      LightweightHint.this.hide();
    }
  }

  private final class MyResizableComponent extends JPanel{
    private final JComponent myComponent;
    private Point myLastPoint;
    private Rectangle myBounds;


    public MyResizableComponent(final JComponent component) {
      myComponent = component;
      setLayout(new BorderLayout());
      add(myComponent, BorderLayout.CENTER);
      enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    public JComponent getComponent() {
      return myComponent;
    }

    protected void processMouseEvent(MouseEvent e) {
      if (e.getID() == MouseEvent.MOUSE_PRESSED) {
           myLastPoint = e.getPoint();
           myBounds = myComponent.getBounds();
         }
         else if(e.getID()==MouseEvent.MOUSE_DRAGGED){
           final int dx = e.getX() - myLastPoint.x;
            myBounds.x += dx;
            myBounds.width -= dx;

           final Dimension minSize = myComponent.getMinimumSize();

           final Rectangle newBounds = myComponent.getBounds();

           // Component's bounds cannot be less the some minimum size
           if (myBounds.width >= minSize.width) {
             newBounds.x = myBounds.x;
             newBounds.width = myBounds.width;
           }

           final Dimension size = newBounds.getSize();
           newBounds.width = size.width;
           newBounds.height = size.height;

           myComponent.setBounds(newBounds);


           myLastPoint=e.getPoint();
         }
      super.processMouseEvent(e);
    }
  }
}