/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 19, 2002
 * Time: 2:56:43 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.hint.*;
import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.PopupHandler;
import com.intellij.util.SmartList;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.*;
import java.util.List;
import java.util.Queue;

public class EditorMarkupModelImpl extends MarkupModelImpl implements EditorMarkupModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.impl.EditorMarkupModelImpl");

  private static final TooltipGroup ERROR_STRIPE_TOOLTIP_GROUP = new TooltipGroup("ERROR_STRIPE_TOOLTIP_GROUP", 0);
  private static final Icon ERRORS_FOUND_ICON = IconLoader.getIcon("/general/errorsFound.png");

  private final EditorImpl myEditor;
  private MyErrorPanel myErrorPanel;
  private ErrorStripeRenderer myErrorStripeRenderer = null;
  private final List<ErrorStripeListener> myErrorMarkerListeners = new ArrayList<ErrorStripeListener>();
  private ErrorStripeListener[] myCachedErrorMarkerListeners = null;
  private List<RangeHighlighter> myCachedSortedHighlighters = null;
  private final MarkSpots myMarkSpots = new MarkSpots();
  private int myScrollBarHeight;
  private static final Comparator<RangeHighlighter> LAYER_COMPARATOR = new Comparator<RangeHighlighter>() {
    public int compare(final RangeHighlighter o1, final RangeHighlighter o2) {
      return o1.getLayer() - o2.getLayer();
    }
  };

  private @NotNull ErrorStripTooltipRendererProvider myTooltipRendererProvider = new BasicTooltipRendererProvider();

  private int offsetToLine(int offset) {
    final Document document = myEditor.getDocument();
    if (offset > document.getTextLength()) {
      offset = document.getTextLength();
    }
    final int lineNumber = document.getLineNumber(offset);
    return myEditor.logicalToVisualPosition(new LogicalPosition(lineNumber, 0)).line;
  }

  private static class MarkSpot {
    private final int yStart;
    private int yEnd;
    // sorted by layers from bottom to top
    private RangeHighlighter[] highlighters = RangeHighlighter.EMPTY_ARRAY;

    public MarkSpot(final int yStart, final int yEnd) {
      this.yStart = yStart;
      this.yEnd = yEnd;
    }

    private boolean near(MouseEvent e, double width) {
      final int x = e.getX();
      final int y = e.getY();
      return 0 <= x && x < width && yStart - getMinHeight() <= y && y < yEnd + getMinHeight();
    }
  }

  private class MarkSpots {
    private List<MarkSpot> mySpots;
    private int myEditorScrollbarTop = -1;
    private int myEditorTargetHeight = -1;
    private int myEditorSourceHeight = -1;

    private void recalcEditorDimensions() {
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      myEditorScrollbarTop = scrollBar.getDecScrollButtonHeight() + 1;
      int bottom = scrollBar.getIncScrollButtonHeight();
      myEditorTargetHeight = myScrollBarHeight - myEditorScrollbarTop - bottom;
      myEditorSourceHeight = myEditor.getPreferredSize().height;
    }

    private void clear() {
      mySpots = null;
      myEditorScrollbarTop = -1;
      myEditorSourceHeight = -1;
      myEditorTargetHeight = -1;
    }

    public boolean showToolTipByMouseMove(final MouseEvent e, final double width) {
      recalcMarkSpots();
      final List<MarkSpot> nearestMarkSpots = getNearestMarkSpots(e, width);
      Set<RangeHighlighter> highlighters = new THashSet<RangeHighlighter>();
      for (MarkSpot markSpot : nearestMarkSpots) {
        highlighters.addAll(Arrays.asList(markSpot.highlighters));
      }
      TooltipRenderer bigRenderer = myTooltipRendererProvider.calcTooltipRenderer(highlighters);
      if (bigRenderer != null) {
        showTooltip(e, bigRenderer);
        return true;
      }
      return false;
    }

    private class PositionedRangeHighlighter {
      private final RangeHighlighter highlighter;
      private final int yStart;
      private final int yEnd;

      public PositionedRangeHighlighter(final RangeHighlighter highlighter, final int yStart, final int yEnd) {
        this.highlighter = highlighter;
        this.yStart = yStart;
        this.yEnd = yEnd;
      }

      @SuppressWarnings({"HardCodedStringLiteral"})
      public String toString() {
        return "PR[" + yStart + "-" + yEnd + ")";
      }
    }

    private PositionedRangeHighlighter getPositionedRangeHighlighter(RangeHighlighter mark) {
      int visStartLine = offsetToLine(mark.getStartOffset());
      int visEndLine = offsetToLine(mark.getEndOffset());
      int yStartPosition = visibleLineToYPosition(visStartLine);
      int yEndPosition = visibleLineToYPosition(visEndLine);
      if (yEndPosition - yStartPosition < getMinHeight()) {
        yEndPosition = yStartPosition + getMinHeight();
      }
      return new PositionedRangeHighlighter(mark, yStartPosition, yEndPosition);
    }

    private int visibleLineToYPosition(int lineNumber) {
      if (myEditorScrollbarTop == -1) {
        recalcEditorDimensions();
      }
      if (myEditorSourceHeight < myEditorTargetHeight) {
        return myEditorScrollbarTop + lineNumber * myEditor.getLineHeight();
      }
      else {
        final int lineCount = myEditorSourceHeight / myEditor.getLineHeight();
        return myEditorScrollbarTop + (int)(((float)lineNumber / lineCount) * myEditorTargetHeight);
      }
    }

    private void recalcMarkSpots() {
      if (mySpots != null) return;
      final List<RangeHighlighter> sortedHighlighters = getSortedHighlighters();
      mySpots = new ArrayList<MarkSpot>();
      if (sortedHighlighters.isEmpty()) return;
      Queue<PositionedRangeHighlighter> startQueue =
        new PriorityQueue<PositionedRangeHighlighter>(5, new Comparator<PositionedRangeHighlighter>() {
          public int compare(final PositionedRangeHighlighter o1, final PositionedRangeHighlighter o2) {
            return o1.yStart - o2.yStart;
          }
        });
      Queue<PositionedRangeHighlighter> endQueue =
        new PriorityQueue<PositionedRangeHighlighter>(5, new Comparator<PositionedRangeHighlighter>() {
          public int compare(final PositionedRangeHighlighter o1, final PositionedRangeHighlighter o2) {
            return o1.yEnd - o2.yEnd;
          }
        });
      int index = 0;
      MarkSpot currentSpot = null;
      while (!startQueue.isEmpty() || !endQueue.isEmpty() || index != sortedHighlighters.size()) {
        LOG.assertTrue(startQueue.size() == endQueue.size());

        final PositionedRangeHighlighter positionedMark;
        boolean addingNew;
        if (index != sortedHighlighters.size()) {
          RangeHighlighter mark = sortedHighlighters.get(index);
          if (!mark.isValid() || mark.getErrorStripeMarkColor() == null) {
            sortedHighlighters.remove(index);
            continue;
          }
          PositionedRangeHighlighter positioned = getPositionedRangeHighlighter(mark);
          if (!endQueue.isEmpty() && endQueue.peek().yEnd <= positioned.yStart) {
            positionedMark = endQueue.peek();
            addingNew = false;
          }
          else {
            positionedMark = positioned;
            addingNew = true;
          }
        }
        else if (!endQueue.isEmpty()) {
          positionedMark = endQueue.peek();
          addingNew = false;
        }
        else {
          LOG.error("cant be");
          return;
        }

        if (addingNew) {
          if (currentSpot == null) {
            currentSpot = new MarkSpot(positionedMark.yStart, -1);
          }
          else {
            currentSpot.yEnd = positionedMark.yStart;
            if (currentSpot.yEnd != currentSpot.yStart) {
              spitOutMarkSpot(currentSpot, startQueue);
            }
            currentSpot = new MarkSpot(positionedMark.yStart, -1);
          }
          while (index != sortedHighlighters.size()) {
            PositionedRangeHighlighter positioned = getPositionedRangeHighlighter(sortedHighlighters.get(index));
            if (positioned.yStart != positionedMark.yStart) break;
            startQueue.add(positioned);
            endQueue.add(positioned);
            index++;
          }
        }
        else {
          currentSpot.yEnd = positionedMark.yEnd;
          spitOutMarkSpot(currentSpot, startQueue);
          currentSpot = new MarkSpot(positionedMark.yEnd, -1);
          while (!endQueue.isEmpty() && endQueue.peek().yEnd == positionedMark.yEnd) {
            final PositionedRangeHighlighter highlighter = endQueue.remove();
            for (Iterator<PositionedRangeHighlighter> iterator = startQueue.iterator(); iterator.hasNext();) {
              PositionedRangeHighlighter positioned = iterator.next();
              if (positioned == highlighter) {
                iterator.remove();
                break;
              }
            }
          }
          if (startQueue.isEmpty()) {
            currentSpot = null;
          }
        }
      }
    }

    private void spitOutMarkSpot(final MarkSpot currentSpot, final Queue<PositionedRangeHighlighter> startQueue) {
      mySpots.add(currentSpot);
      currentSpot.highlighters = new RangeHighlighter[startQueue.size()];
      int i =0;
      for (PositionedRangeHighlighter positioned : startQueue) {
        currentSpot.highlighters[i++] = positioned.highlighter;
      }
      Arrays.sort(currentSpot.highlighters, LAYER_COMPARATOR);
    }

    private void repaint(Graphics g, final int width) {
      recalcMarkSpots();
      for (int i = 0; i < mySpots.size(); i++) {
        MarkSpot markSpot = mySpots.get(i);

        int yStart = markSpot.yStart;
        RangeHighlighter mark = markSpot.highlighters[markSpot.highlighters.length - 1];

        int yEnd = markSpot.yEnd;

        final Color color = mark.getErrorStripeMarkColor();

        int x = 1;
        int paintWidth = width;
        if (mark.isThinErrorStripeMark()) {
          paintWidth /= 2;
          x += paintWidth / 2;
        }

        g.setColor(color);
        g.fillRect(x + 1, yStart, paintWidth - 2, yEnd - yStart);

        Color brighter = color.brighter();
        Color darker = color.darker();

        g.setColor(brighter);
        //left
        UIUtil.drawLine(g, x, yStart, x, yEnd - 1);
        if (i == 0 || !isAdjacent(mySpots.get(i - 1), markSpot) || wider(markSpot, mySpots.get(i - 1))) {
          //top decoration
          UIUtil.drawLine(g, x + 1, yStart, x + paintWidth - 2, yStart);
        }
        g.setColor(darker);
        if (i == mySpots.size() - 1 || !isAdjacent(markSpot, mySpots.get(i + 1)) || wider(markSpot, mySpots.get(i + 1))) {
          // bottom decoration
          UIUtil.drawLine(g, x + 1, yEnd - 1, x + paintWidth - 2, yEnd - 1);
        }
        //right
        UIUtil.drawLine(g, x + paintWidth - 2, yStart, x + paintWidth - 2, yEnd - 1);

      }      
    }

    private boolean isAdjacent(MarkSpot markTop, MarkSpot markBottom) {
      return markTop.yEnd >= markBottom.yStart;
    }

    private boolean wider(MarkSpot markTop, MarkSpot markBottom) {
      final RangeHighlighter highlighterTop = markTop.highlighters[markTop.highlighters.length - 1];
      final RangeHighlighter highlighterBottom = markBottom.highlighters[markBottom.highlighters.length - 1];
      return !highlighterTop.isThinErrorStripeMark() && highlighterBottom.isThinErrorStripeMark();
    }

    public void doClick(final MouseEvent e, final int width) {
      recalcMarkSpots();
      RangeHighlighter marker = getNearestRangeHighlighter(e, width);
      if (marker == null) return;
      int offset = marker.getStartOffset();

      final Document doc = myEditor.getDocument();
      if (doc.getLineCount() > 0) {
        // Necessary to expand folded block even if navigating just before one
        // Very useful when navigating to first unused import statement.
        int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));
        myEditor.getCaretModel().moveToOffset(lineEnd);
      }

      myEditor.getCaretModel().moveToOffset(offset);
      myEditor.getSelectionModel().removeSelection();
      ScrollingModel scrollingModel = myEditor.getScrollingModel();
      scrollingModel.disableAnimation();
      scrollingModel.scrollToCaret(ScrollType.CENTER);
      scrollingModel.enableAnimation();
      fireErrorMarkerClicked(marker, e);
    }

    private RangeHighlighter getNearestRangeHighlighter(final MouseEvent e, final int width) {
      List<MarkSpot> nearestSpots = getNearestMarkSpots(e, width);
      RangeHighlighter nearestMarker = null;
      int yPos = 0;
      for (MarkSpot markSpot : nearestSpots) {
        for (RangeHighlighter highlighter : markSpot.highlighters) {
          final int newYPos = visibleLineToYPosition(offsetToLine(highlighter.getStartOffset()));

          if (nearestMarker == null || Math.abs(yPos - e.getY()) > Math.abs(newYPos - e.getY())) {
            nearestMarker = highlighter;
            yPos = newYPos;
          }
        }
      }
      return nearestMarker;
    }

    private List<MarkSpot> getNearestMarkSpots(final MouseEvent e, final double width) {
      List<MarkSpot> nearestSpot = new SmartList<MarkSpot>();
      for (MarkSpot markSpot : mySpots) {
        if (markSpot.near(e, width)) {
          nearestSpot.add(markSpot);
        }
      }
      return nearestSpot;
    }
  }

  EditorMarkupModelImpl(EditorImpl editor) {
    super((DocumentImpl)editor.getDocument());
    myEditor = editor;
  }

  public void setErrorStripeVisible(boolean val) {
    if (val) {
      myErrorPanel = new MyErrorPanel();
      myEditor.getPanel().add(myErrorPanel,
                              myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_LEFT
                              ? BorderLayout.WEST
                              : BorderLayout.EAST);
    }
    else if (myErrorPanel != null) {
      myEditor.getPanel().remove(myErrorPanel);
      myErrorPanel = null;
    }
  }

  public void setErrorPanelPopupHandler(PopupHandler handler) {
    if (myErrorPanel != null) {
      myErrorPanel.setPopupHandler(handler);
    }
  }

  public void setErrorStripTooltipRendererProvider(@NotNull final ErrorStripTooltipRendererProvider provider) {
    myTooltipRendererProvider = provider;
  }

  public Editor getEditor() {
    return myEditor;
  }

  public void setErrorStripeRenderer(ErrorStripeRenderer renderer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myErrorStripeRenderer = renderer;
    //try to not cancel tooltips here, since it is being called after every writeAction, even to the console
    //HintManager.getInstance().getTooltipController().cancelTooltips();
    if (myErrorPanel != null) {
      myErrorPanel.repaint();
    }
  }

  public ErrorStripeRenderer getErrorStripeRenderer() {
    return myErrorStripeRenderer;
  }

  public void dispose() {
    myErrorStripeRenderer = null;
    super.dispose();
  }

  public void repaint() {
    markDirtied();
    EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
    myScrollBarHeight = scrollBar.getSize().height;

    if (myErrorPanel != null) {
      myErrorPanel.repaint();
    }
  }

  private List<RangeHighlighter> getSortedHighlighters() {
    if (myCachedSortedHighlighters == null) {
      myCachedSortedHighlighters = new ArrayList<RangeHighlighter>();

      for (RangeHighlighter highlighter : getAllHighlighters()) {
        if (highlighter.getErrorStripeMarkColor() != null) {
          myCachedSortedHighlighters.add(highlighter);
        }
      }

      final MarkupModel docMarkup = getDocument().getMarkupModel(myEditor.myProject);
      for (RangeHighlighter highlighter : docMarkup.getAllHighlighters()) {
        if (highlighter.getErrorStripeMarkColor() != null) {
          myCachedSortedHighlighters.add(highlighter);
        }
      }

      if (!myCachedSortedHighlighters.isEmpty()) {
        Collections.sort(myCachedSortedHighlighters, new Comparator<RangeHighlighter>() {
          public int compare(final RangeHighlighter h1, final RangeHighlighter h2) {
            return h1.getStartOffset() - h2.getStartOffset();
          }
        });
      }
    }
    return myCachedSortedHighlighters;
  }

  private class MyErrorPanel extends JPanel implements MouseMotionListener, MouseListener {
    private PopupHandler myHandler;

    private MyErrorPanel() {
      setOpaque(true);
      addMouseListener(this);
      addMouseMotionListener(this);
    }

    public Dimension getPreferredSize() {
      return new Dimension(ERRORS_FOUND_ICON.getIconWidth() + 2, 0);
    }

    protected void paintComponent(Graphics g) {
      ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintStart();

      try {
        LafManager lafManager = LafManager.getInstance();
        if (lafManager == null || lafManager.isUnderAquaLookAndFeel()) {
          g.setColor(new Color(0xF0F0F0));
          Rectangle clipBounds = g.getClipBounds();
          g.fillRect(clipBounds.x, clipBounds.y, clipBounds.width, clipBounds.height);
        } else {
          super.paintComponent(g);
        }

        if (myErrorStripeRenderer != null) {
          EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
          int top = scrollBar.getDecScrollButtonHeight();
          myErrorStripeRenderer.paint(this, g, new Rectangle(0, 0, getWidth(), top));
        }

        myMarkSpots.repaint(g, ERRORS_FOUND_ICON.getIconWidth());
      }
      finally {
        ((ApplicationImpl)ApplicationManager.getApplication()).editorPaintFinish();
      }
    }

    // mouse events
    public void mouseClicked(final MouseEvent e) {
      CommandProcessor.getInstance().executeCommand(
        myEditor.myProject, new Runnable() {
          public void run() {
            doMouseClicked(e);
          }
        },
        EditorBundle.message("move.caret.command.name"), getDocument()
      );
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    private void doMouseClicked(MouseEvent e) {
      myEditor.getContentComponent().requestFocus();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }
      myMarkSpots.doClick(e, getWidth());
    }

    public void mouseMoved(MouseEvent e) {
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      int buttonHeight = scrollBar.getDecScrollButtonHeight();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }

      if (e.getY() < buttonHeight && myErrorStripeRenderer != null) {
        String tooltipMessage = myErrorStripeRenderer.getTooltipMessage();
        showTooltip(e, new LineTooltipRenderer(tooltipMessage));
        return;
      }

      if (myMarkSpots.showToolTipByMouseMove(e,getWidth())) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return;
      }

      cancelMyToolTips(e);

      if (getCursor().equals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))) {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }

    private void cancelMyToolTips(final MouseEvent e) {
      final TooltipController tooltipController = HintManager.getInstance().getTooltipController();
      if (!tooltipController.shouldSurvive(e)) {
        tooltipController.cancelTooltip(ERROR_STRIPE_TOOLTIP_GROUP);
      }
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
      cancelMyToolTips(e);
    }

    public void mouseDragged(MouseEvent e) {
      cancelMyToolTips(e);
    }

    public void setPopupHandler(final PopupHandler handler) {
      if (myHandler != null) {
        removeMouseListener(myHandler);
      }

      myHandler = handler;
      addMouseListener(handler);
    }

  }

  private void showTooltip(MouseEvent e, final TooltipRenderer tooltipObject) {
    final TooltipController tooltipController = HintManager.getInstance().getTooltipController();
    tooltipController.showTooltipByMouseMove(myEditor, e, tooltipObject,
                                             myEditor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT,
                                             ERROR_STRIPE_TOOLTIP_GROUP);
  }

  private ErrorStripeListener[] getCachedErrorMarkerListeners() {
    if (myCachedErrorMarkerListeners == null) {
      myCachedErrorMarkerListeners = myErrorMarkerListeners.toArray(
        new ErrorStripeListener[myErrorMarkerListeners.size()]
      );
    }

    return myCachedErrorMarkerListeners;
  }

  private void fireErrorMarkerClicked(RangeHighlighter marker, MouseEvent e) {
    ErrorStripeEvent event = new ErrorStripeEvent(getEditor(), e, marker);
    ErrorStripeListener[] listeners = getCachedErrorMarkerListeners();
    for (ErrorStripeListener listener : listeners) {
      listener.errorMarkerClicked(event);
    }
  }

  public void addErrorMarkerListener(ErrorStripeListener listener) {
    myCachedErrorMarkerListeners = null;
    myErrorMarkerListeners.add(listener);
  }

  public void removeErrorMarkerListener(ErrorStripeListener listener) {
    myCachedErrorMarkerListeners = null;
    boolean success = myErrorMarkerListeners.remove(listener);
    LOG.assertTrue(success);
  }

  public void markDirtied() {
    myCachedSortedHighlighters = null;
    myMarkSpots.clear();
  }

  private static int getMinHeight() {
    return DaemonCodeAnalyzerSettings.getInstance().ERROR_STRIPE_MARK_MIN_HEIGHT;
  }

  private static class BasicTooltipRendererProvider implements ErrorStripTooltipRendererProvider {
    public TooltipRenderer calcTooltipRenderer(@NotNull final Collection<RangeHighlighter> highlighters) {
        LineTooltipRenderer bigRenderer = null;
        Collection<String> tooltips = new THashSet<String>(); //do not show same tooltip twice

        for (RangeHighlighter highlighter : highlighters) {
          final Object tooltipObject = highlighter.getErrorStripeTooltip();
          if (tooltipObject == null) continue;

          final String text = tooltipObject.toString();
          if (tooltips.add(text)) {
            if (bigRenderer == null) {
              bigRenderer = new LineTooltipRenderer(text);
            }
            else {
              bigRenderer.addBelow(text);
            }
          }
        }

        return bigRenderer;
      }
  }
}
