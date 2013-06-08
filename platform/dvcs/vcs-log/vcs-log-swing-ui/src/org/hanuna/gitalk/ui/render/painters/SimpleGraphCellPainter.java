package org.hanuna.gitalk.ui.render.painters;

import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.printmodel.ShortEdge;
import org.hanuna.gitalk.printmodel.SpecialPrintElement;
import org.hanuna.gitalk.ui.render.PositionUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Ellipse2D;

import static org.hanuna.gitalk.ui.render.Print_Parameters.*;

/**
 * @author erokhins
 */
public class SimpleGraphCellPainter implements GraphCellPainter {
  private Graphics2D g2;
  private final Stroke usual = new BasicStroke(THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  private final Stroke hide = new BasicStroke(THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);
  private final Stroke selectUsual = new BasicStroke(SELECT_THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL);
  private final Stroke selectHide = new BasicStroke(SELECT_THICK_LINE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0, new float[]{7}, 0);

  private final Color markColor = Color.BLACK;

  private void paintUpLine(int from, int to, Color color) {
    int x1 = WIDTH_NODE * from + WIDTH_NODE / 2;
    int y1 = HEIGHT_CELL / 2;
    int x2 = WIDTH_NODE * to + WIDTH_NODE / 2;
    int y2 = -HEIGHT_CELL / 2;
    g2.setColor(color);
    g2.drawLine(x2, y2, x1, y1);
  }

  private void paintDownLine(int from, int to, Color color) {
    int x1 = WIDTH_NODE * from + WIDTH_NODE / 2;
    int y1 = HEIGHT_CELL / 2;
    int x2 = WIDTH_NODE * to + WIDTH_NODE / 2;
    int y2 = HEIGHT_CELL + HEIGHT_CELL / 2;
    g2.setColor(color);
    g2.drawLine(x1, y1, x2, y2);
  }

  private void paintAbove(int position, Color color) {
    int x1 = WIDTH_NODE * position + 3;
    int y = 4;
    int x2 = WIDTH_NODE * position + WIDTH_NODE - 4;
    g2.setColor(color);
    g2.drawLine(x1, y, x2, y);
  }

  private void paintBelow(int position, Color color) {
    int x1 = WIDTH_NODE * position + 3;
    int y = HEIGHT_CELL - 4;
    int x2 = WIDTH_NODE * position + WIDTH_NODE - 4;
    g2.setColor(color);
    g2.drawLine(x1, y, x2, y);
  }


  private void paintCircle(int position, Color color, boolean select) {
    int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
    int y0 = HEIGHT_CELL / 2;
    int r = CIRCLE_RADIUS;
    if (select) {
      r = SELECT_CIRCLE_RADIUS;
    }
    Ellipse2D.Double circle = new Ellipse2D.Double(x0 - r + 0.5, y0 - r + 0.5, 2 * r, 2 * r);
    g2.setColor(color);
    g2.fill(circle);
  }

  private void paintHide(int position, Color color) {
    int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
    int y0 = HEIGHT_CELL / 2;
    int r = CIRCLE_RADIUS;
    g2.setColor(color);
    g2.drawLine(x0, y0, x0, y0 + r);
    g2.drawLine(x0, y0 + r, x0 + r, y0);
    g2.drawLine(x0, y0 + r, x0 - r, y0);
  }

  private void paintShow(int position, Color color) {
    int x0 = WIDTH_NODE * position + WIDTH_NODE / 2;
    int y0 = HEIGHT_CELL / 2;
    int r = CIRCLE_RADIUS;
    g2.setColor(color);
    g2.drawLine(x0, y0, x0, y0 - r);
    g2.drawLine(x0, y0 - r, x0 + r, y0);
    g2.drawLine(x0, y0 - r, x0 - r, y0);
  }

  private void setStroke(boolean usual, boolean select) {
    if (usual) {
      if (select) {
        g2.setStroke(selectUsual);
      }
      else {
        g2.setStroke(this.usual);
      }
    }
    else {
      if (select) {
        g2.setStroke(selectHide);
      }
      else {
        g2.setStroke(hide);
      }
    }
  }

  private interface LitePrinter {
    void print(Color color);
  }

  private void drawLogick(boolean selected, boolean marked, boolean isUsual, Color usualColor, LitePrinter printer) {
    if (selected) {
      setStroke(isUsual, true);
      printer.print(markColor);
      setStroke(isUsual, false);
      printer.print(usualColor);
    } else {
      setStroke(isUsual, marked);
      printer.print(usualColor);
    }
  }



  @Override
  public void draw(Graphics2D g2, GraphPrintCell row) {
    this.g2 = g2;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    for (final ShortEdge edge : row.getUpEdges()) {
      drawLogick(edge.isSelected(), edge.isMarked(), edge.isUsual(), ColorGenerator.getColor(edge.getEdge().getBranch()),
                 new LitePrinter() {
                   @Override
                   public void print(Color color) {
                     paintUpLine(edge.getDownPosition(), edge.getUpPosition(), color);
                   }
                 });
    }
    for (final ShortEdge edge : row.getDownEdges()) {
      drawLogick(edge.isSelected(), edge.isMarked(), edge.isUsual(), ColorGenerator.getColor(edge.getEdge().getBranch()),
                 new LitePrinter() {
                   @Override
                   public void print(Color color) {
                     paintDownLine(edge.getUpPosition(), edge.getDownPosition(), color);
                   }
                 });

    }
    for (final SpecialPrintElement printElement : row.getSpecialPrintElements()) {
      final Edge edge;
      switch (printElement.getType()) {
        case COMMIT_NODE:
          Node node = printElement.getGraphElement().getNode();
          assert node != null;
          if (printElement.isSelected()) {
            paintCircle(printElement.getPosition(), markColor, true);
            paintCircle(printElement.getPosition(), ColorGenerator.getColor(node.getBranch()), false);
          } else {
            paintCircle(printElement.getPosition(), ColorGenerator.getColor(node.getBranch()), printElement.isMarked());
          }
          break;
        case UP_ARROW:
          edge = printElement.getGraphElement().getEdge();
          assert edge != null;
          drawLogick(printElement.isSelected(), printElement.isMarked(), edge.getType() == Edge.EdgeType.USUAL,
                     ColorGenerator.getColor(edge.getBranch()),
                     new LitePrinter() {
                       @Override
                       public void print(Color color) {
                         paintShow(printElement.getPosition(), color);
                       }
                     });
          break;
        case DOWN_ARROW:
          edge = printElement.getGraphElement().getEdge();
          assert edge != null;
          drawLogick(printElement.isSelected(), printElement.isMarked(), edge.getType() == Edge.EdgeType.USUAL,
                     ColorGenerator.getColor(edge.getBranch()),
                     new LitePrinter() {
                       @Override
                       public void print(Color color) {
                         paintHide(printElement.getPosition(), color);
                       }
                     });
          break;
        default:
          throw new IllegalStateException();
      }
    }

    for (final SpecialPrintElement printElement : row.getSpecialPrintElements()) {
      if (printElement.getType() == SpecialPrintElement.Type.COMMIT_NODE && printElement.getDragAndDropSelect() != 0) {
        Node node = printElement.getGraphElement().getNode();
        assert node != null;
        if (printElement.getDragAndDropSelect() > 0) {
          paintAbove(printElement.getPosition(), ColorGenerator.getColor(node.getBranch()));
        } else {
          paintBelow(printElement.getPosition(), ColorGenerator.getColor(node.getBranch()));
        }
      }
    }
  }

  @Nullable
  @Override
  public GraphElement mouseOver(GraphPrintCell row, int x, int y) {
    for (SpecialPrintElement printElement : row.getSpecialPrintElements()) {
      if (printElement.getType() == SpecialPrintElement.Type.COMMIT_NODE) {
        if (PositionUtil.overNode(printElement.getPosition(), x, y)) {
          return printElement.getGraphElement();
        }
      }
    }
    for (ShortEdge edge : row.getUpEdges()) {
      if (PositionUtil.overUpEdge(edge, x, y)) {
        return edge.getEdge();
      }
    }
    for (ShortEdge edge : row.getDownEdges()) {
      if (PositionUtil.overDownEdge(edge, x, y)) {
        return edge.getEdge();
      }
    }

    return null;
  }

  @Nullable
  @Override
  public SpecialPrintElement mouseOverArrow(GraphPrintCell row, int x, int y) {
    for (SpecialPrintElement printElement : row.getSpecialPrintElements()) {
      if (printElement.getType() != SpecialPrintElement.Type.COMMIT_NODE) {
        if (PositionUtil.overNode(printElement.getPosition(), x, y)) {
          return printElement;
        }
      }
    }
    return null;
  }
}
