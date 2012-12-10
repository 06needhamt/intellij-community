package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.printmodel.layout.LayoutModel;
import org.hanuna.gitalk.printmodel.layout.LayoutRow;

/**
 * @author erokhins
 */
public class LayoutTestUtils {
    public static String toShortStr(Node node) {
        return node.getCommit().hash().toStrHash();
    }

    public static String toShortStr(GraphElement element) {
        Node node = element.getNode();
        if (node != null) {
            return toShortStr(node);
        } else {
            Edge edge = element.getEdge();
            if (edge == null) {
                throw new IllegalStateException();
            }
            return toShortStr(edge.getUpNode()) + ":" + toShortStr(edge.getDownNode());
        }
    }

    public static String toStr(LayoutRow row) {
        ReadOnlyList<GraphElement> orderedGraphElements = row.getOrderedGraphElements();
        if (orderedGraphElements.isEmpty()) {
            return "";
        }
        StringBuilder s = new StringBuilder();
        s.append(toShortStr(orderedGraphElements.get(0)));
        for (int i = 1; i < orderedGraphElements.size(); i++) {
            s.append(" ").append(toShortStr(orderedGraphElements.get(i)));
        }
        return s.toString();
    }

    public static String toStr(LayoutModel layoutModel) {
        ReadOnlyList<LayoutRow> cells = layoutModel.getLayoutRows();
        if (cells.isEmpty()) {
            return "";
        }
        StringBuilder s = new StringBuilder();
        s.append(toStr(cells.get(0)));
        for (int i = 1; i < cells.size(); i++) {
            s.append("\n").append(toStr(cells.get(i)));
        }

        return s.toString();
    }
}
