package org.hanuna.gitalk;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.NewGraph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.graph.new_mutable.GraphBuilder;
import org.hanuna.gitalk.graph.new_mutable.MutableGraph;
import org.hanuna.gitalk.parser.SimpleCommitListParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * @author erokhins
 */
public class GraphTestUtils {


    @NotNull
    public static Node getCommitNode(Graph graph, int rowIndex) {
        NodeRow row = graph.getNodeRows().get(rowIndex);
        for (Node node : row.getNodes()) {
            if (node.getType() == Node.Type.COMMIT_NODE) {
                return node;
            }
        }
        throw new IllegalArgumentException();
    }

    @NotNull
    public static Node getCommitNode(NewGraph graph, int rowIndex) {
        NodeRow row = graph.getNodeRows().get(rowIndex);
        for (Node node : row.getNodes()) {
            if (node.getType() == Node.Type.COMMIT_NODE) {
                return node;
            }
        }
        throw new IllegalArgumentException();
    }

    @NotNull
    public static MutableGraph getNewMutableGraph(@NotNull String inputStr) {
        SimpleCommitListParser parser = new SimpleCommitListParser(new StringReader(inputStr));
        List<Commit> commits = null;
        try {
            commits = parser.readAllCommits();
        } catch (IOException e) {
            throw new IllegalStateException();
        }
        return GraphBuilder.build(commits);
    }
}
