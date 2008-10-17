package com.intellij.openapi.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class Banner extends NonOpaquePanel implements PropertyChangeListener{

  private int myBannerMinHeight;
  private JComponent myText = new MyText();

  private NonOpaquePanel myActionsPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));

  private Map<Action, LinkLabel> myActions = new HashMap<Action, LinkLabel>();

  public Banner() {
    setLayout(new BorderLayout());

    setBorder(new EmptyBorder(2, 6, 2, 4));

    add(myText, BorderLayout.CENTER);
    add(myActionsPanel, BorderLayout.EAST);
  }

  public void addAction(final Action action) {
    action.addPropertyChangeListener(this);
    final LinkLabel label = new LinkLabel(null, null, new LinkListener() {
      public void linkSelected(final LinkLabel aSource, final Object aLinkData) {
        action.actionPerformed(new ActionEvent(Banner.this, ActionEvent.ACTION_PERFORMED, Action.ACTION_COMMAND_KEY));
      }
    });
    myActions.put(action, label);
    myActionsPanel.add(label);
    updateAction(action);
  }

  void updateAction(Action action) {
    final LinkLabel label = myActions.get(action);
    label.setVisible(action.isEnabled());
    label.setText((String)action.getValue(Action.NAME));
    label.setToolTipText((String)action.getValue(Action.SHORT_DESCRIPTION));
  }

  public void propertyChange(final PropertyChangeEvent evt) {
    final Object source = evt.getSource();
    if (source instanceof Action) {
      updateAction(((Action)source));
    }
  }

  public void clearActions() {
    final Set<Action> actions = myActions.keySet();
    for (Iterator<Action> iterator = actions.iterator(); iterator.hasNext();) {
      Action each = iterator.next();
      each.removePropertyChangeListener(this);
    }
    myActions.clear();
    myActionsPanel.removeAll();
  }

  public Dimension getMinimumSize() {
    final Dimension size = super.getMinimumSize();
    size.height = myBannerMinHeight > 0 ? myBannerMinHeight : size.height;
    return size;
  }

  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    size.height = getMinimumSize().height;
    return size;
  }

  public void setMinHeight(final int height) {
    myBannerMinHeight = height;
    revalidate();
    repaint();
  }

  public void setText(@Nullable final String... text) {
    myText.removeAll();
    if (text == null) return;

    for (int i = 0; i < text.length; i++) {
      final JLabel eachLabel = new JLabel(text[i]);
      eachLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
      eachLabel.setVerticalTextPosition(JLabel.TOP);
      eachLabel.setFont(eachLabel.getFont().deriveFont(Font.BOLD, eachLabel.getFont().getSize()));
      myText.add(eachLabel);
      if (i < text.length - 1) {
        final JLabel eachIcon = new JLabel(IconLoader.getIcon("/general/comboArrowRight.png"));
        eachIcon.setBorder(new EmptyBorder(0, 0, 0, 4));
        myText.add(eachIcon);
      }
    }
  }

  public void updateActions() {
    final Set<Action> actions = myActions.keySet();
    for (Iterator<Action> iterator = actions.iterator(); iterator.hasNext();) {
      updateAction(iterator.next());
    }
  }


  private static class MyText extends NonOpaquePanel {
    @Override
    public void doLayout() {
      int x = 0;
      for (int i = 0; i < getComponentCount(); i++) {
        final Component each = getComponent(i);
        final Dimension eachSize = each.getPreferredSize();
        each.setBounds(x, 0, eachSize.width, getHeight());
        x += each.getBounds().width;
      }
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = new Dimension();
      for (int i = 0; i < getComponentCount(); i++) {
        final Component each = getComponent(i);
        final Dimension eachSize = each.getPreferredSize();
        size.width += eachSize.width;
        size.height = Math.max(size.height, eachSize.height);
      }

      return size;
    }
  }
}