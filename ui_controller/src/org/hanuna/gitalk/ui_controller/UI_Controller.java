package org.hanuna.gitalk.ui_controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.GraphFragment;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.ui_controller.table_models.GraphTableModel;
import org.hanuna.gitalk.ui_controller.table_models.RefTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hanuna.gitalk.ui_controller.EventsController.ControllerListener;

/**
 * @author erokhins
 */
public class UI_Controller {
    private final DataPack dataPack;
    private final EventsController events = new EventsController();
    private final RefTableModel refTableModel;
    private final GraphTableModel graphTableModel;

    private GraphElement prevGraphElement = null;
    private Set<Commit> prevSelectionBranches;

    public UI_Controller(@NotNull DataPack dataPack) {
        this.dataPack = dataPack;

        this.refTableModel = new RefTableModel(dataPack.getRefsModel());
        this.graphTableModel = new GraphTableModel(dataPack);
        this.prevSelectionBranches = new HashSet<Commit>(refTableModel.getCheckedCommits());
    }


    public GraphTableModel getGraphTableModel() {
        return graphTableModel;
    }

    public RefTableModel getRefTableModel() {
        return refTableModel;
    }

    public void addControllerListener(@NotNull ControllerListener listener) {
        events.addListener(listener);
    }

    public void over(@Nullable GraphElement graphElement) {
        SelectController selectController = dataPack.getSelectController();
        GraphFragmentController fragmentController = dataPack.getFragmentController();
        if (graphElement == prevGraphElement) {
            return;
        } else {
            prevGraphElement = graphElement;
        }
        selectController.deselectAll();
        if (graphElement == null) {
            events.runUpdateTable();
        } else {
            GraphFragment graphFragment = fragmentController.relateFragment(graphElement);
            selectController.select(graphFragment);
            events.runUpdateTable();
        }
    }

    public void click(@Nullable GraphElement graphElement) {
        SelectController selectController = dataPack.getSelectController();
        GraphFragmentController fragmentController = dataPack.getFragmentController();
        selectController.deselectAll();
        if (graphElement == null) {
            return;
        }
        GraphFragment fragment = fragmentController.relateFragment(graphElement);
        if (fragment == null) {
            return;
        }
        boolean visible = fragmentController.isVisible(fragment);
        Replace replace = fragmentController.setVisible(fragment, !visible);
        events.runUpdateTable();
        events.runJumpToRow(replace.from());
    }

    public void runUpdateShowBranches() {
        Set<Commit> checkedCommits = refTableModel.getCheckedCommits();
        if (! prevSelectionBranches.equals(checkedCommits)) {
            MyTimer timer = new MyTimer("update branch shows");

            prevSelectionBranches = new HashSet<Commit>(checkedCommits);
            dataPack.setShowBranches(checkedCommits);
            graphTableModel.rewriteData(dataPack);

            events.runUpdateTable();
            events.runJumpToRow(0);

            timer.print();
        }
    }

    @Nullable
    private Node mainNodeInRow(int rowIndex) {
        List<NodeRow> nodeRows = dataPack.getGraph().getNodeRows();
        assert rowIndex < nodeRows.size();
        for (Node node : nodeRows.get(rowIndex).getVisibleNodes()) {
            if (node.getType() == Node.Type.COMMIT_NODE) {
                return node;
            }
        }
        return null;
    }

    public void hideAll() {
        int currentRowIndex = 0;
        Graph graph = dataPack.getGraph();
        GraphFragmentController fragmentController = graph.getFragmentController();
        while (currentRowIndex < graph.getNodeRows().size()) {
            Node node = mainNodeInRow(currentRowIndex);
            if (node != null) {
                GraphFragment fragment = fragmentController.relateFragment(node);
                if (fragment != null && fragmentController.isVisible(fragment)) {
                    fragmentController.setVisible(fragment, false);
                }
            }
            currentRowIndex++;
        }

        dataPack.updatePrintModel();
        graphTableModel.rewriteData(dataPack);
        events.runUpdateTable();
        events.runJumpToRow(0);
    }


}
