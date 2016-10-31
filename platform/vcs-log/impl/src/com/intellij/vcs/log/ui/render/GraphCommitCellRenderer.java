package com.intellij.vcs.log.ui.render;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkRenderer;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableCell;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.graph.EdgePrintElement;
import com.intellij.vcs.log.graph.PrintElement;
import com.intellij.vcs.log.paint.GraphCellPainter;
import com.intellij.vcs.log.paint.PaintParameters;
import com.intellij.vcs.log.ui.frame.VcsLogGraphTable;
import com.intellij.vcs.log.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;

public class GraphCommitCellRenderer extends TypeSafeTableCellRenderer<GraphCommitCell> {
  private static final int MAX_GRAPH_WIDTH = 6;
  private static final int VERTICAL_PADDING = JBUI.scale(7);

  @NotNull private final VcsLogData myLogData;
  @NotNull private final VcsLogGraphTable myGraphTable;

  @Nullable private final FadeOutPainter myFadeOutPainter;
  @Nullable private final ReferencePainter myTooltipPainter;

  @NotNull private final MyComponent myComponent;

  private boolean myExpanded;

  public GraphCommitCellRenderer(@NotNull VcsLogData logData,
                                 @NotNull GraphCellPainter painter,
                                 @NotNull VcsLogGraphTable table) {
    myLogData = logData;
    myGraphTable = table;

    myFadeOutPainter = isRedesignedLabels() ? new FadeOutPainter() : null;
    myTooltipPainter = isRedesignedLabels() ? new LabelPainter(myLogData) : null;

    myComponent = new MyComponent(logData, painter, table) {
      @Override
      public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (myFadeOutPainter != null) {
          if (!myExpanded) {
            int start = Math.max(myGraphImage.getWidth(), getWidth() - myFadeOutPainter.getWidth());
            myFadeOutPainter.paint((Graphics2D)g, start, 0, getHeight());
          }
        }
      }
    };
  }

  @Override
  protected Component getTableCellRendererComponentImpl(@NotNull JTable table,
                                                        @NotNull GraphCommitCell value,
                                                        boolean isSelected,
                                                        boolean hasFocus,
                                                        int row,
                                                        int column) {
    myComponent.customize(value, isSelected, hasFocus, row, column);

    myExpanded = myGraphTable.getExpandableItemsHandler().getExpandedItems().contains(new TableCell(row, column));
    if (myFadeOutPainter != null) {
      myFadeOutPainter.customize(value.getRefsToThisCommit(), row, column, table  /*any color fits here*/);
    }
    return myComponent;
  }

  public static boolean isRedesignedLabels() {
    return Registry.is("vcs.log.labels.redesign");
  }

  @Nullable
  public JComponent getTooltip(@NotNull Object value, @NotNull Point point, int width) {
    if (myTooltipPainter == null) return null;

    GraphCommitCell cell = getValue(value);
    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    if (!refs.isEmpty()) {
      myTooltipPainter
        .customizePainter(myComponent, refs, myComponent.getBackground(), myComponent.getForeground(), myComponent.getWidth());
      if (myTooltipPainter.getSize().getWidth() - LabelPainter.GRADIENT_WIDTH >= width - point.getX()) {
        return new TooltipReferencesPanel(myLogData, myTooltipPainter, refs);
      }
    }
    return null;
  }

  public int getPreferredHeight() {
    return myComponent.getPreferredHeight();
  }

  public int getTooltipXCoordinate(int row) {
    GraphCommitCell cell = getValue(myGraphTable.getModel().getValueAt(row, GraphTableModel.COMMIT_COLUMN));
    Collection<VcsRef> refs = cell.getRefsToThisCommit();
    if (!refs.isEmpty()) {
      int labelWidth = LabelPainter.calculateWidth(myLogData, refs, myComponent, getPreferredHeight(), true);
      TableColumn commitColumn = myGraphTable.getColumnModel().getColumn(GraphTableModel.COMMIT_COLUMN);
      return commitColumn.getWidth() - (labelWidth - LabelPainter.GRADIENT_WIDTH) / 2;
    }
    return -1;
  }

  private static class MyComponent extends SimpleColoredRenderer {
    @NotNull private final VcsLogData myLogData;
    @NotNull private final VcsLogGraphTable myGraphTable;
    @NotNull private final GraphCellPainter myPainter;
    @NotNull private final IssueLinkRenderer myIssueLinkRenderer;
    @NotNull private final ReferencePainter myReferencePainter;

    @NotNull protected PaintInfo myGraphImage = new PaintInfo(UIUtil.createImage(1, 1, BufferedImage.TYPE_INT_ARGB), 0);
    @NotNull private Font myFont;
    private int myHeight;

    public MyComponent(@NotNull VcsLogData data, @NotNull GraphCellPainter painter, @NotNull VcsLogGraphTable table) {
      myLogData = data;
      myPainter = painter;
      myGraphTable = table;

      myReferencePainter = isRedesignedLabels() ? new LabelPainter(myLogData) : new RectangleReferencePainter(myLogData);

      myIssueLinkRenderer = new IssueLinkRenderer(myLogData.getProject(), this);
      myFont = RectanglePainter.getFont();
      myHeight = calculateHeight();
    }

    @NotNull
    @Override
    public Dimension getPreferredSize() {
      Dimension preferredSize = super.getPreferredSize();
      int referencesSize = myReferencePainter.isLeftAligned() ? 0 : myReferencePainter.getSize().width;
      if (referencesSize > 0) referencesSize -= LabelPainter.GRADIENT_WIDTH;
      return new Dimension(preferredSize.width + referencesSize, getPreferredHeight());
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);

      int graphImageWidth = myGraphImage.getWidth();

      if (!myReferencePainter.isLeftAligned()) {
        int start = Math.max(graphImageWidth, getWidth() - myReferencePainter.getSize().width);
        myReferencePainter.paint((Graphics2D)g, start, 0, getHeight());
      }
      else {
        myReferencePainter.paint((Graphics2D)g, graphImageWidth, 0, getHeight());
      }

      UIUtil.drawImage(g, myGraphImage.getImage(), 0, 0, null);
    }

    public void customize(@NotNull GraphCommitCell cell, boolean isSelected, boolean hasFocus, int row, int column) {
      clear();
      setPaintFocusBorder(hasFocus && myGraphTable.getCellSelectionEnabled());
      acquireState(myGraphTable, isSelected, hasFocus, row, column);
      getCellState().updateRenderer(this);

      myGraphImage = getGraphImage(cell.getPrintElements());

      SimpleTextAttributes style = myGraphTable.applyHighlighters(this, row, column, hasFocus, isSelected);

      Collection<VcsRef> refs = cell.getRefsToThisCommit();
      Color baseForeground = ObjectUtils.assertNotNull(myGraphTable.getBaseStyle(row, column, hasFocus, isSelected).getForeground());

      append(""); // appendTextPadding wont work without this
      int columnWidth = myGraphTable.getColumnModel().getColumn(column).getWidth();
      if (myReferencePainter.isLeftAligned()) {
        myReferencePainter.customizePainter(this, refs, getBackground(), baseForeground,
                                            0 /*left aligned painter does not use available width*/);

        appendTextPadding(myGraphImage.getWidth() + myReferencePainter.getSize().width);
        myIssueLinkRenderer.appendTextWithLinks(cell.getText(), style);
      }
      else {
        appendTextPadding(myGraphImage.getWidth());
        myIssueLinkRenderer.appendTextWithLinks(cell.getText(), style);
        myReferencePainter.customizePainter(this, refs, getBackground(), baseForeground,
                                            Math.min(columnWidth - super.getPreferredSize().width, columnWidth / 3));
      }
    }

    private int calculateHeight() {
      return Math.max(myReferencePainter.getSize().height, getFontMetrics(myFont).getHeight() + VERTICAL_PADDING);
    }

    public int getPreferredHeight() {
      Font font = RectanglePainter.getFont();
      if (myFont != font) {
        myFont = font;
        myHeight = calculateHeight();
      }
      return myHeight;
    }

    @NotNull
    private PaintInfo getGraphImage(@NotNull Collection<? extends PrintElement> printElements) {
      double maxIndex = 0;
      for (PrintElement printElement : printElements) {
        maxIndex = Math.max(maxIndex, printElement.getPositionInCurrentRow());
        if (printElement instanceof EdgePrintElement) {
          maxIndex = Math.max(maxIndex,
                              (printElement.getPositionInCurrentRow() + ((EdgePrintElement)printElement).getPositionInOtherRow()) / 2.0);
        }
      }
      maxIndex++;

      maxIndex = Math.max(maxIndex, Math.min(MAX_GRAPH_WIDTH, myGraphTable.getVisibleGraph().getRecommendedWidth()));
      BufferedImage image = UIUtil.createImage((int)(PaintParameters.getNodeWidth(myGraphTable.getRowHeight()) * (maxIndex + 2)),
                                               myGraphTable.getRowHeight(),
                                               BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = image.createGraphics();
      myPainter.draw(g2, printElements);

      int width = (int)(maxIndex * PaintParameters.getNodeWidth(myGraphTable.getRowHeight()));
      return new PaintInfo(image, width);
    }
  }

  private static class PaintInfo {
    private final int myWidth;
    @NotNull private final Image myImage;

    PaintInfo(@NotNull Image image, int width) {
      myImage = image;
      myWidth = width;
    }

    @NotNull
    Image getImage() {
      return myImage;
    }

    /**
     * Returns the "interesting" width of the painted image, i.e. the width which the text in the table should be offset by. <br/>
     * It can be smaller than the width of {@link #getImage() the image}, because we allow the text to cover part of the graph
     * (some diagonal edges, etc.)
     */
    int getWidth() {
      return myWidth;
    }
  }

  private class FadeOutPainter {
    private int myWidth = LabelPainter.GRADIENT_WIDTH;

    public void customize(@NotNull Collection<VcsRef> currentRefs, int row, int column, @NotNull JTable table) {
      myWidth = 0;

      if (currentRefs.isEmpty()) {
        int prevWidth = 0;
        if (row > 0) {
          GraphCommitCell commitCell = getValue(table.getValueAt(row - 1, column));
          prevWidth = LabelPainter.calculateWidth(myLogData, commitCell.getRefsToThisCommit(), myComponent, getPreferredHeight(), true);
        }

        int nextWidth = 0;
        if (row < table.getRowCount() - 1) {
          GraphCommitCell commitCell = getValue(table.getValueAt(row + 1, column));
          nextWidth = LabelPainter.calculateWidth(myLogData, commitCell.getRefsToThisCommit(), myComponent, getPreferredHeight(), true);
        }

        myWidth = Math.max(Math.max(prevWidth, nextWidth), LabelPainter.GRADIENT_WIDTH);
      }
    }

    public void paint(@NotNull Graphics2D g2, int x, int y, int height) {
      GraphicsConfig config = GraphicsUtil.setupAAPainting(g2);
      LabelPainter.paintFadeOut(g2, x, y, myWidth, height, myComponent.getBackground());
      config.restore();
    }

    public int getWidth() {
      return myWidth;
    }
  }
}
