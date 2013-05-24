package org.hanuna.gitalk.data.impl;

import com.intellij.openapi.project.Project;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.Function;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.data.CommitDataGetter;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.GraphBuilder;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.hanuna.gitalk.graphmodel.GraphModel;
import org.hanuna.gitalk.graphmodel.impl.GraphModelImpl;
import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.printmodel.impl.GraphPrintCellModelImpl;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class DataPackImpl implements DataPack {
  public static DataPackImpl buildDataPack(@NotNull List<CommitParents> commitParentsList,
                                           @NotNull List<Ref> allRefs,
                                           Executor<String> statusUpdater, Project project) {
    statusUpdater.execute("Build graph");

    MyTimer timer = new MyTimer("graph build");
    MutableGraph graph = GraphBuilder.build(commitParentsList, allRefs);
    timer.print();

    timer.clear("graphModel build");
    GraphModel graphModel = new GraphModelImpl(graph, allRefs);
    timer.print();

    statusUpdater.execute("Build print model");
    timer.clear("build printModel");
    final GraphPrintCellModel printCellModel = new GraphPrintCellModelImpl(graphModel.getGraph());
    timer.print();
    graphModel.addUpdateListener(new Executor<UpdateRequest>() {
      @Override
      public void execute(UpdateRequest key) {
        printCellModel.recalculate(key);
      }
    });

    final RefsModel refsModel = new RefsModel(allRefs);
    graphModel.getFragmentManager().setUnconcealedNodeFunction(new Function<Node, Boolean>() {
      @NotNull
      @Override
      public Boolean get(@NotNull Node key) {
        if (key.getDownEdges().isEmpty() || key.getUpEdges().isEmpty() || refsModel.isBranchRef(key.getCommitHash())) {
          return true;
        }
        else {
          return false;
        }
      }
    });
    return new DataPackImpl(graphModel, refsModel, printCellModel, project);
  }


  private final GraphModel graphModel;
  private final RefsModel refsModel;
  private final GraphPrintCellModel printCellModel;
  private final CommitDataGetter commitDataGetter;

  private DataPackImpl(GraphModel graphModel, RefsModel refsModel, GraphPrintCellModel printCellModel, Project project) {
    this.graphModel = graphModel;
    this.refsModel = refsModel;
    this.printCellModel = printCellModel;
    commitDataGetter = new CacheCommitDataGetter(project, this);
  }

  public void appendCommits(@NotNull List<CommitParents> commitParentsList, @NotNull Executor<String> statusUpdater) {
    statusUpdater.execute("Rebuild graph and print model");
    MyTimer timer = new MyTimer("append commits");
    graphModel.appendCommitsToGraph(commitParentsList);
    timer.print();
  }

  @Override
  public CommitDataGetter getCommitDataGetter() {
    return commitDataGetter;
  }

  @NotNull
  @Override
  public RefsModel getRefsModel() {
    return refsModel;
  }

  @NotNull
  @Override
  public GraphModel getGraphModel() {
    return graphModel;
  }

  @NotNull
  @Override
  public GraphPrintCellModel getPrintCellModel() {
    return printCellModel;
  }
}
