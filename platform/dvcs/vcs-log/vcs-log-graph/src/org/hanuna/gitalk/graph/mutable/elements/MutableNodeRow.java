package org.hanuna.gitalk.graph.mutable.elements;

import com.intellij.util.SmartList;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.graph.mutable.GraphDecorator;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableNodeRow implements NodeRow {
  private final List<MutableNode> nodes = new SmartList<MutableNode>();
  private final MutableGraph graph;
  private int rowIndex;

  public MutableNodeRow(@NotNull MutableGraph graph, int rowIndex) {
    this.graph = graph;
    this.rowIndex = rowIndex;
  }

  public void setRowIndex(int rowIndex) {
    this.rowIndex = rowIndex;
  }

  @NotNull
  public GraphDecorator getGraphDecorator() {
    return graph.getGraphDecorator();
  }

  @NotNull
  public List<MutableNode> getInnerNodeList() {
    return nodes;
  }

  @NotNull
  @Override
  public List<Node> getNodes() {
    List<Node> visibleNodes = new ArrayList<Node>(nodes.size());
    for (Node node : nodes) {
      if (getGraphDecorator().isVisibleNode(node)) {
        visibleNodes.add(node);
      }
    }
    return visibleNodes;
  }

  @Override
  public int getRowIndex() {
    return rowIndex;
  }
}
