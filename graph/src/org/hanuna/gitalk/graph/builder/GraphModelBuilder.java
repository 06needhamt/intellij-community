package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.Branch;
import org.hanuna.gitalk.graph.Edge;
import org.hanuna.gitalk.graph.GraphModel;
import org.hanuna.gitalk.graph.Node;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static org.hanuna.gitalk.graph.builder.MutableNode.createEdge;

/**
 * @author erokhins
 */
public class GraphModelBuilder {
    private int lastLogIndex;
    private Map<Hash, MutableNode> hashMutableNode = new HashMap<Hash, MutableNode>();
    private MutableNodeRow nextRow;
    private final List<MutableNodeRow> rows = new ArrayList<MutableNodeRow>();



    private int getLogIndexOfCommit(Commit commit) {
        final CommitData data = commit.getData();
        if (data == null) {
            return lastLogIndex;
        } else {
            return data.getLogIndex();
        }
    }



    // return add's node
    private MutableNode addCurrentCommit(Commit commit) {
        MutableNode node = hashMutableNode.remove(commit.hash());
        if (node == null) {
            node = new MutableNode(commit, new Branch());
        }
        node.setRow(nextRow);
        node.setType(Node.Type.commitNode);
        nextRow.add(node);
        rows.add(nextRow);

        nextRow = new MutableNodeRow(rows.size(), rows.size());
        return node;
    }

    private void addParent(MutableNode node, Commit parent, Branch branch) {
        MutableNode parentNode = hashMutableNode.get(parent.hash());
        if (parentNode == null) {
            parentNode = new MutableNode(parent, branch);
            createEdge(node, parentNode, Edge.Type.usual, branch);
            hashMutableNode.put(parent.hash(), parentNode);
        } else {
            int index = getLogIndexOfCommit(parent);
            createEdge(node, parentNode, Edge.Type.usual, branch);
            // i.e. we need create new Node
            if (index != rows.size()) {
                // remove old node
                hashMutableNode.remove(parent.hash());

                MutableNode newParentNode = new MutableNode(parentNode.getCommit(), parentNode.getBranch());
                createEdge(parentNode, newParentNode, Edge.Type.usual, parentNode.getBranch());
                hashMutableNode.put(parent.hash(), newParentNode);

                parentNode.setType(Node.Type.edgeNode);
                parentNode.setRow(nextRow);
                nextRow.add(parentNode);

            }
        }
    }

    private void append(@NotNull Commit commit) {
        MutableNode node = addCurrentCommit(commit);

        CommitData data = commit.getData();
        if (data == null) {
            throw new IllegalStateException("commit was append, but commitData is null");
        }
        final ReadOnlyList<Commit> parents = data.getParents();
        for (int i = 0; i < parents.size(); i++) {
            Commit parent = parents.get(i);
            if (i == 0) {
                addParent(node, parent, node.getBranch());
            } else {
                addParent(node, parent, new Branch());
            }
        }
    }

    private void prepare(int lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
        nextRow = new MutableNodeRow(0, 0);
    }

    private void lastActions() {
        final Collection<MutableNode> lastNodes = hashMutableNode.values();
        for (MutableNode node : lastNodes) {
            node.setRow(nextRow);
            node.setType(Node.Type.endCommitNode);
            nextRow.add(node);
        }
        if (nextRow.getNodes().size() > 0) {
            rows.add(nextRow);
        }
    }

    @NotNull
    public GraphModel build(ReadOnlyList<Commit> listOfCommits) {
        prepare(listOfCommits.size() - 1);
        for (Commit listOfCommit : listOfCommits) {
            append(listOfCommit);
        }
        lastActions();
        return new GraphModelImpl(rows, lastLogIndex);
    }



}
