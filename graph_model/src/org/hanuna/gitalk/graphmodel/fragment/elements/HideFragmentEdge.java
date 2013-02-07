package org.hanuna.gitalk.graphmodel.fragment.elements;

import org.hanuna.gitalk.graph.elements.Branch;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

/**
* @author erokhins
*/
public class HideFragmentEdge implements Edge {
    private final Node upNode;
    private final Node downNode;

    public HideFragmentEdge(Node upNode, Node downNode) {
        this.upNode = upNode;
        this.downNode = downNode;
    }

    @NotNull
    @Override
    public Node getUpNode() {
        return upNode;
    }

    @NotNull
    @Override
    public Node getDownNode() {
        return downNode;
    }

    @NotNull
    @Override
    public Type getType() {
        return Type.HIDE_FRAGMENT;
    }

    @NotNull
    @Override
    public Branch getBranch() {
        return upNode.getBranch();
    }

    @Override
    public Node getNode() {
        return null;
    }

    @Override
    public Edge getEdge() {
        return this;
    }
}
