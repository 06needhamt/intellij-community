package com.intellij.openapi.wm.impl;

import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class TitlePanel extends JPanel {
  private static final int DELAY = 5; // Delay between frames
  private static final int TOTAL_FRAME_COUNT = 7; // Total number of frames in animation sequence

  private int myCurrentFrame;

  private final static Color CNT_ENABLE_COLOR = new Color(105, 128, 180);
  private final static Color BND_ENABLE_COLOR = CNT_ENABLE_COLOR.brighter();

  private final static Color BND_DISABLE_COLOR = new Color(184, 184, 184); //new Color(160, 160, 160);
  private final static Color CNT_DISABLE_COLOR = new Color(184, 184, 184);


  private float myBndStartRed, myBndStartGreen, myBndStartBlue; // start boundary color for animation
  private float myBndEndRed, myBndEndGreen, myBndEndBlue; // end boundary color for animation
  private Color myBndColor; // current boundary color

  private float myCntStartRed, myCntStartGreen, myCntStartBlue; // start center color for animation
  private float myCntEndRed, myCntEndGreen, myCntEndBlue; // end center color for animation
  private Color myCntColor; // current center color

  private final Alarm myFrameTicker; // Determines moments of rendering of next frame
  private final MyAnimator myAnimator; // Renders panel's color
  private boolean myActive = true;
  private JComponent mySideButtonsComponent;
  private JComponent mySideButtons;

  public static final Color BUTTON_SEPARATOR_COLOR = Color.white;
  private final Icon mySeparatorActive = IconLoader.getIcon("/general/separator.png");
  private final Icon mySeparatorInactive = IconLoader.getIcon("/general/inactiveSeparator.png");

  private final static Color ACTIVE_SIDE_BUTTON_BG = new Color(179, 197, 231);
  private final static Color INACTIVE_SIDE_BUTTON_BG = new Color(200, 200, 200);

  TitlePanel() {
    super(new BorderLayout());

    myFrameTicker = new Alarm();
    myAnimator = new MyAnimator();
    setLayout(new BorderLayout());

    setupColors(false);

    myCurrentFrame = TOTAL_FRAME_COUNT;
    updateColor();
  }


  public void addTitle(JComponent component) {
    add(component, BorderLayout.CENTER);
  }

  public final void setActive(final boolean active, boolean animate) {
    if (active == myActive) {
      return;
    }
    myActive = active;
    myFrameTicker.cancelAllRequests();
    if (myCurrentFrame > 0) { // reverse rendering
      myCurrentFrame = TOTAL_FRAME_COUNT - myCurrentFrame;
    }

    setupColors(active);

    if (animate) {
      myFrameTicker.addRequest(myAnimator, DELAY);
    }
    else {
      myCurrentFrame = TOTAL_FRAME_COUNT;
      updateColor();
    }
  }

  private void setupColors(final boolean active) {
    if (active) {

      // Boundary color

      myBndStartRed = BND_DISABLE_COLOR.getRed();
      myBndStartGreen = BND_DISABLE_COLOR.getGreen();
      myBndStartBlue = BND_DISABLE_COLOR.getBlue();

      myBndEndRed = BND_ENABLE_COLOR.getRed();
      myBndEndGreen = BND_ENABLE_COLOR.getGreen();
      myBndEndBlue = BND_ENABLE_COLOR.getBlue();

      // Center color

      myCntStartRed = CNT_DISABLE_COLOR.getRed();
      myCntStartGreen = CNT_DISABLE_COLOR.getGreen();
      myCntStartBlue = CNT_DISABLE_COLOR.getBlue();

      myCntEndRed = CNT_ENABLE_COLOR.getRed();
      myCntEndGreen = CNT_ENABLE_COLOR.getGreen();
      myCntEndBlue = CNT_ENABLE_COLOR.getBlue();
    }
    else {

      // Boundary color

      myBndStartRed = BND_ENABLE_COLOR.getRed();
      myBndStartGreen = BND_ENABLE_COLOR.getGreen();
      myBndStartBlue = BND_ENABLE_COLOR.getBlue();

      myBndEndRed = BND_DISABLE_COLOR.getRed();
      myBndEndGreen = BND_DISABLE_COLOR.getGreen();
      myBndEndBlue = BND_DISABLE_COLOR.getBlue();

      // Center color

      myCntStartRed = CNT_ENABLE_COLOR.getRed();
      myCntStartGreen = CNT_ENABLE_COLOR.getGreen();
      myCntStartBlue = CNT_ENABLE_COLOR.getBlue();

      myCntEndRed = CNT_DISABLE_COLOR.getRed();
      myCntEndGreen = CNT_DISABLE_COLOR.getGreen();
      myCntEndBlue = CNT_DISABLE_COLOR.getBlue();
    }
  }

  private void updateColor() {

    // Update boundary color

    final int bndRed = (int) (myBndStartRed + (float) myCurrentFrame * (myBndEndRed - myBndStartRed) / TOTAL_FRAME_COUNT);
    final int bndGreen = (int) (myBndStartGreen + (float) myCurrentFrame * (myBndEndGreen - myBndStartGreen) / TOTAL_FRAME_COUNT);
    final int bndBlue = (int) (myBndStartBlue + (float) myCurrentFrame * (myBndEndBlue - myBndStartBlue) / TOTAL_FRAME_COUNT);
    myBndColor = new Color(Math.max(0, Math.min(bndRed, 255)),
                           Math.max(0, Math.min(bndGreen, 255)),
                           Math.max(0, Math.min(bndBlue, 255)));

    // Update center color

    final int cntRed = (int) (myCntStartRed + (float) myCurrentFrame * (myCntEndRed - myCntStartRed) / TOTAL_FRAME_COUNT);
    final int cntGreen = (int) (myCntStartGreen + (float) myCurrentFrame * (myCntEndGreen - myCntStartGreen) / TOTAL_FRAME_COUNT);
    final int cntBlue = (int) (myCntStartBlue + (float) myCurrentFrame * (myCntEndBlue - myCntStartBlue) / TOTAL_FRAME_COUNT);
    myCntColor = new Color(Math.max(0, Math.min(cntRed, 255)),
                           Math.max(0, Math.min(cntGreen, 255)),
                           Math.max(0, Math.min(cntBlue, 255)));
  }


  @Override
  protected void addImpl(Component comp, Object constraints, int index) {
    UIUtil.removeQuaquaVisualMarginsIn(comp);
    super.addImpl(comp, constraints, index);
  }

  protected final void paintComponent(final Graphics g) {
    super.paintComponent(g);

    final Graphics2D g2d = (Graphics2D) g;

    g2d.setPaint(new GradientPaint(0, 0, myBndColor, 0, getHeight(), myCntColor));
    if (mySideButtons.isVisible()) {
      final Rectangle sideRec = SwingUtilities.convertRectangle(mySideButtonsComponent.getParent(), mySideButtonsComponent.getBounds(), this);
      g2d.fillRect(0, 0, getWidth() - sideRec.width, getHeight());

      g2d.setColor(ActivatableLineBorder.INACTIVE_COLOR);
      final Color buttonInnerColor = myActive ? ACTIVE_SIDE_BUTTON_BG : INACTIVE_SIDE_BUTTON_BG;
      g2d.setPaint(new GradientPaint(sideRec.x, sideRec.y + 1, Color.white, sideRec.x, (int)sideRec.getMaxY() - 1, buttonInnerColor));
      g2d.fillRect(sideRec.x + 2, sideRec.y + 1, sideRec.width - 2, sideRec.height);


      Icon separator = myActive ? mySeparatorActive : mySeparatorInactive;
      separator.paintIcon(this, g, sideRec.x, sideRec.y);

    } else {
      g2d.fillRect(0, 0, getWidth(), getHeight());
    }
  }
  public JComponent getSideButtonsComponent() {
    return mySideButtonsComponent;
  }

  public void addButtons(final JComponent buttons, JComponent sideButtons) {
    mySideButtons = sideButtons;

    mySideButtonsComponent = new Wrapper(sideButtons);
    mySideButtonsComponent.setOpaque(false);
    mySideButtonsComponent.setBorder(new EmptyBorder(0, 6, 0, 6));

    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);

    UIUtil.removeQuaquaVisualMarginsIn(buttons);
    UIUtil.removeQuaquaVisualMarginsIn(sideButtons);

    wrapper.add(buttons, BorderLayout.CENTER);
    wrapper.add(mySideButtonsComponent, BorderLayout.EAST);

    add(wrapper, BorderLayout.EAST);
  }

  private final class MyAnimator implements Runnable {
    public void run() {
      updateColor();
      paintImmediately(0, 0, getWidth(), getHeight());
      if (myCurrentFrame <= TOTAL_FRAME_COUNT) {
        myCurrentFrame++;
        myFrameTicker.addRequest(this, DELAY);
      }
      else {
        myFrameTicker.cancelAllRequests();
      }
    }
  }

}
