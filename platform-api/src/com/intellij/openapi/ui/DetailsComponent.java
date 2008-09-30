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

package com.intellij.openapi.ui;

import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.GeneralPath;

public class DetailsComponent  {

  private JPanel myComponent;

  private JComponent myContentWrapper;


  private Banner myBannerLabel;

  private JLabel myEmptyContentLabel;
  private NonOpaquePanel myBanner;

  private String myBannerText;

  public DetailsComponent() {
    myComponent = new JPanel(new BorderLayout()) {
      protected void paintComponent(final Graphics g) {
        if (NullableComponent.Check.isNull(myContentWrapper)) return;

        GraphicsConfig c = new GraphicsConfig(g);
        c.setAntialiasing(true);

        int arc = 8;

        Insets insets = getInsets();
        if (insets == null) {
          insets = new Insets(0, 0, 0, 0);
        }

        g.setColor(UIUtil.getFocusedFillColor());

        final Rectangle banner = myBanner.getBounds();
        final GeneralPath header = new GeneralPath();

        final int leftX = insets.left;
        final int leftY = insets.top;
        final int rightX = insets.left + getWidth() - 1 - insets.right;
        final int rightY = banner.y + banner.height;

        header.moveTo(leftX, rightY);
        header.lineTo(leftX, leftY + arc);
        header.quadTo(leftX, leftY, leftX + arc, leftY);
        header.lineTo(rightX - arc, leftY);
        header.quadTo(rightX, leftY, rightX, leftY + arc);
        header.lineTo(rightX, rightY);
        header.closePath();

        c.getG().fill(header);

        g.setColor(UIUtil.getFocusedBoundsColor());

        c.getG().draw(header);

        final int down = getHeight() - insets.top - insets.bottom - 1;
        g.drawLine(leftX, rightY, leftX, down);
        g.drawLine(rightX, rightY, rightX, down);
        g.drawLine(leftX, down, rightX, down);

        c.restore();
      }
    };

    myComponent.setOpaque(false);

    myBanner = new NonOpaquePanel(new BorderLayout());
    myBannerLabel = new Banner();

    myBanner.add(myBannerLabel, BorderLayout.CENTER);

    myComponent.add(myBanner, BorderLayout.NORTH);
    myEmptyContentLabel = new JLabel("", JLabel.CENTER);
  }

  public void setBannerActions(Action[] actions) {
    myBannerLabel.clearActions();
    for (Action each : actions) {
      myBannerLabel.addAction(each);
    }

    myComponent.revalidate();
    myComponent.repaint();
  }

  public void setContent(@Nullable JComponent c) {
    if (myContentWrapper != null) {
      myComponent.remove(myContentWrapper);
    } 

    myContentWrapper = new MyWrapper(c);

    myContentWrapper.setOpaque(false);
    myContentWrapper.setBorder(new EmptyBorder(5, 5, 5, 5));
    myComponent.add(myContentWrapper, BorderLayout.CENTER);

    updateBanner();

    myComponent.revalidate();
    myComponent.repaint();
  }




  public void setText(String text) {
    myBannerText = text;
    updateBanner();
  }

  private void updateBanner() {
    if (NullableComponent.Check.isNull(myContentWrapper)) {
      myBannerLabel.setText(null);
    } else {
      myBannerLabel.setText(myBannerText);
    }
  }

  public DetailsComponent setEmptyContentText(@Nullable final String emptyContentText) {
    @NonNls final String s = "<html><body><center>" + (emptyContentText!= null ? emptyContentText : "") + "</center></body><html>";
    myEmptyContentLabel.setText(s);
    return this;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void setBannerMinHeight(final int height) {
    myBannerLabel.setMinHeight(height);
  }

  public void disposeUIResources() {
    setContent(null);
  }

  public static void main(String[] args) {
    final JFrame frame = new JFrame();
    frame.getContentPane().setLayout(new BorderLayout());
    final JPanel content = new JPanel(new BorderLayout());

    final DetailsComponent d = new DetailsComponent();
    content.add(d.getComponent(), BorderLayout.CENTER);

    d.setText("This is a Tree");
    final JTree c = new JTree();
    c.setBorder(new LineBorder(Color.red));
    d.setContent(c);

    frame.getContentPane().add(content, BorderLayout.CENTER);

    frame.setBounds(300, 300, 300, 300);
    frame.show();
  }

  public void updateBannerActions() {
    myBannerLabel.updateActions();
  }


  public static interface Facade {

    DetailsComponent getDetailsComponent();

  }

  private class MyWrapper extends Wrapper implements NullableComponent {
    public MyWrapper(final JComponent c) {
      super(c == null || NullableComponent.Check.isNull(c) ? DetailsComponent.this.myEmptyContentLabel : c);
    }

    public boolean isNull() {
      return getTargetComponent() == myEmptyContentLabel;
    }
  }

}
