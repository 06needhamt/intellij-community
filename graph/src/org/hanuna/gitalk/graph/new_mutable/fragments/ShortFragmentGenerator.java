package org.hanuna.gitalk.graph.new_mutable.fragments;

import org.hanuna.gitalk.graph.NewGraph;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class ShortFragmentGenerator {
    private final NewGraph graph;
    private UnhiddenNodeFunction unhiddenNodes = new UnhiddenNodeFunction() {
        @Override
        public boolean isUnhiddenNode(@NotNull Node node) {
            return false;
        }
    };

    public ShortFragmentGenerator(NewGraph graph) {
        this.graph = graph;
    }

    public void setUnhiddenNodes(UnhiddenNodeFunction unhiddenNodes) {
        this.unhiddenNodes = unhiddenNodes;
    }

    private void addDownNodeToSet(@NotNull Set<Node> nodes, @NotNull Node node) {
        for (Edge edge : node.getDownEdges()) {
            Node downNode = edge.getDownNode();
            nodes.add(downNode);
        }
    }

    private boolean allUpNodeHere(@NotNull Set<Node> here, @NotNull Node node) {
        for (Edge upEdge : node.getUpEdges()) {
            if (!here.contains(upEdge.getUpNode())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public NewGraphFragment getDownShortFragment(@NotNull Node startNode) {
        if (startNode.getType() == Node.Type.EDGE_NODE) {
            throw new IllegalArgumentException("small fragment may start only with COMMIT_NODE, but this node is: " + startNode);
        }

        Set<Node> upNodes = new HashSet<Node>();
        upNodes.add(startNode);
        Set<Node> notAddedNodes = new HashSet<Node>();
        addDownNodeToSet(notAddedNodes, startNode);

        Node endNode = null;

        int startRowIndex = startNode.getRowIndex() + 1;
        int lastIndex = graph.getNodeRows().size() - 1;

        boolean isEnd = false;
        for (int currentRowIndex = startRowIndex; currentRowIndex <= lastIndex && !isEnd; currentRowIndex++) {
            for (Node node : graph.getNodeRows().get(currentRowIndex).getNodes()) {
                if (notAddedNodes.remove(node)) {
                    if (notAddedNodes.isEmpty() && node.getType() != Node.Type.EDGE_NODE) {
                        if (allUpNodeHere(upNodes, node)) { // i.e. we found smallFragment
                            endNode = node;
                        }
                        isEnd = true;
                        break;
                    } else {
                        if (!allUpNodeHere(upNodes, node) || unhiddenNodes.isUnhiddenNode(node)) {
                            isEnd = true;
                        }
                        upNodes.add(node);
                        addDownNodeToSet(notAddedNodes, node);
                    }
                }
            }
        }
        if (endNode == null) {
            return null;
        } else {
            upNodes.remove(startNode);
            return new SimpleGraphFragment(startNode, endNode, upNodes);
        }
    }


    private void addUpNodeToSet(@NotNull Set<Node> nodes, @NotNull Node node) {
        for (Edge edge : node.getUpEdges()) {
            Node upNode = edge.getUpNode();
            nodes.add(upNode);
        }
    }

    private boolean allDownNodeHere(@NotNull Set<Node> here, @NotNull Node node) {
        for (Edge downEdge : node.getDownEdges()) {
            if (!here.contains(downEdge.getDownNode())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public NewGraphFragment getUpShortFragment(@NotNull Node startNode) {
        if (startNode.getType() == Node.Type.EDGE_NODE) {
            throw new IllegalArgumentException("small fragment may start only with COMMIT_NODE, but this node is: " + startNode);
        }

        Set<Node> downNodes = new HashSet<Node>();
        downNodes.add(startNode);
        Set<Node> notAddedNodes = new HashSet<Node>();
        addUpNodeToSet(notAddedNodes, startNode);

        Node endNode = null;

        int startRowIndex = startNode.getRowIndex() - 1;
        int lastIndex = 0;

        boolean isEnd = false;
        for (int currentRowIndex = startRowIndex; currentRowIndex >= lastIndex && !isEnd; currentRowIndex--) {
            for (Node node : graph.getNodeRows().get(currentRowIndex).getNodes()) {
                if (notAddedNodes.remove(node)) {
                    if (notAddedNodes.isEmpty() && node.getType() != Node.Type.EDGE_NODE) {
                        if (allDownNodeHere(downNodes, node)) { // i.e. we found smallFragment
                            endNode = node;
                        }
                        isEnd = true;
                        break;
                    } else {
                        if (!allDownNodeHere(downNodes, node) || unhiddenNodes.isUnhiddenNode(node)) {
                            isEnd = true;
                        }
                        downNodes.add(node);
                        addUpNodeToSet(notAddedNodes, node);
                    }
                }
            }
        }
        if (endNode == null) {
            return null;
        } else {
            downNodes.remove(startNode);
            return new SimpleGraphFragment(endNode, startNode, downNodes);
        }
    }



}
