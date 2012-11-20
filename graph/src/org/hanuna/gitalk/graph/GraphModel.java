package org.hanuna.gitalk.graph;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.common.Interval;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface GraphModel {

    @NotNull
    public ReadOnlyList<NodeRow> getNodeRows();

    /**
     * @param edge .getType() == hideBranch or throw IllegalStateException
     * @return interval [a, b)
     * before operation rows was a-1, a, b, b+1...
     */
    @NotNull
    public Interval showBranch(@NotNull Edge edge);

    /**
     * from upNode to downNode will be simple way 1:1 (1 parent & 1 children)
     * after operation will be Edge(upNode, downNode, hideBranch, branchIndex),
     * where branchIndex will be equal branchIndex all intermediate edges
     * @return [a, b)
     * before 1, 2, ...
     * now 1, 2, ... a-1, a, b, b+1...
     */
    @NotNull
    public Interval hideBranch(@NotNull Node upNode, @NotNull Node downNode);

}
