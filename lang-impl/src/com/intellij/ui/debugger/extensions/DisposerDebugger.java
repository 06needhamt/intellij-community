package com.intellij.ui.debugger.extensions;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructureBase;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.objectTree.ObjectNode;
import com.intellij.openapi.util.objectTree.ObjectTree;
import com.intellij.openapi.util.objectTree.ObjectTreeListener;
import com.intellij.openapi.vcs.history.TextTransferrable;
import com.intellij.ui.debugger.UiDebuggerExtension;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsListener;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DisposerDebugger extends JComponent implements UiDebuggerExtension {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.debugger.extensions.DisposerDebugger");

  private JBTabsImpl myTreeTabs;

  public DisposerDebugger() {
    myTreeTabs = new JBTabsImpl(null, null, this);

    final Splitter splitter = new Splitter(true);

    final JBTabsImpl bottom = new JBTabsImpl(null, null, this);
    final AllocationPanel allocations = new AllocationPanel(myTreeTabs);
    bottom.addTab(new TabInfo(allocations).setText("Allocation")).setActions(allocations.getActions(), ActionPlaces.UNKNOWN);


    splitter.setFirstComponent(myTreeTabs);
    splitter.setSecondComponent(bottom);

    setLayout(new BorderLayout());
    add(splitter, BorderLayout.CENTER);

    addTree(new DisposerTree(this), "All", false);
    addTree(new DisposerTree(this), "Watch", true);
  }

  private void addTree(DisposerTree tree, String name, boolean canClear) {
    final DefaultActionGroup group = new DefaultActionGroup();
    if (canClear) {
      group.add(new ClearAction(tree));
    }

    myTreeTabs.addTab(new TabInfo(tree).setText(name).setObject(tree).setActions(group, ActionPlaces.UNKNOWN));
  }

  private class ClearAction extends AnAction {
    private DisposerTree myTree;

    private ClearAction(DisposerTree tree) {
      super("Clear");
      myTree = tree;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setIcon(IconLoader.getIcon("/actions/sync.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      myTree.clear();
    }
  }

  private static class AllocationPanel extends JPanel implements TreeSelectionListener {

    private JEditorPane myAllocation;
    private DefaultActionGroup myActions;

    private JBTabs myTreeTabs;

    private AllocationPanel(JBTabs treeTabs) {
      myTreeTabs = treeTabs;

      myAllocation = new JEditorPane();
      final DefaultCaret caret = new DefaultCaret();
      myAllocation.setCaret(caret);
      caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
      myAllocation.setEditable(false);

      setLayout(new BorderLayout());
      add(new JScrollPane(myAllocation), BorderLayout.CENTER);


      treeTabs.addListener(new TabsListener.Adapter() {
        @Override
        public void selectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          updateText();
        }

        @Override
        public void beforeSelectionChanged(TabInfo oldSelection, TabInfo newSelection) {
          removeListener(oldSelection);
          addListener(newSelection);
        }
      });

      myActions = new DefaultActionGroup();
      myActions.add(new CopyAllocationAction());
    }

    private void addListener(TabInfo info) {
      if (info == null) return;
      ((DisposerTree)info.getObject()).getTree().getSelectionModel().addTreeSelectionListener(this);
    }

    private void removeListener(TabInfo info) {
      if (info == null) return;
      ((DisposerTree)info.getObject()).getTree().getSelectionModel().removeTreeSelectionListener(this);
    }

    public void valueChanged(TreeSelectionEvent e) {
      updateText();
    }

    private void updateText() {
      if (myTreeTabs.getSelectedInfo() == null) return;

      final DisposerTree tree = (DisposerTree)myTreeTabs.getSelectedInfo().getObject();

      final DisposerNode node = tree.getSelectedNode();
      if (node != null) {
        final Throwable allocation = node.getAllocation();
        if (allocation != null) {
          final StringWriter s = new StringWriter();
          final PrintWriter writer = new PrintWriter(s);
          allocation.printStackTrace(writer);
          myAllocation.setText(s.toString());
          return;
        }
      }

      myAllocation.setText(null);
    }

    public ActionGroup getActions() {
      return myActions;
    }

    private class CopyAllocationAction extends AnAction {
      public CopyAllocationAction() {
        super("Copy", "Copy allocation to clipboard", IconLoader.getIcon("/actions/copy.png"));
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(myAllocation.getDocument().getLength() > 0);
      }

      public void actionPerformed(AnActionEvent e) {
        try {
          Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new TextTransferrable(myAllocation.getText(), myAllocation.getText()), null);
        }
        catch (HeadlessException e1) {
          LOG.error(e1);
        }
      }
    }

  }

  private static class DisposerTree extends JPanel implements Disposable, ObjectTreeListener, ElementFilter<DisposerNode> {

    private FilteringTreeBuilder myTreeBuilder;
    private Tree myTree;
    private long myModificationToFilter;

    private DisposerTree(Disposable parent) {
      myModificationToFilter = -1;

      final DisposerStructure structure = new DisposerStructure(this);
      final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
      final Tree tree = new Tree(model);
      tree.setRootVisible(false);
      tree.setShowsRootHandles(true);
      tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      myTreeBuilder = new FilteringTreeBuilder(null, tree, DisposerTree.this, structure, AlphaComparator.INSTANCE) {
        @Override
        public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
          return structure.getRootElement() == getOriginalNode(nodeDescriptor);
        }
      };
      myTreeBuilder.setFilteringMerge(200);
      Disposer.register(this, myTreeBuilder);
      myTree = tree;

      setLayout(new BorderLayout());
      add(new JScrollPane(myTree), BorderLayout.CENTER);

      Disposer.getTree().addListener(this);

      Disposer.register(parent, this);
    }

    public boolean shouldBeShowing(DisposerNode value) {
      return value.getValue().getModification() >= myModificationToFilter;
    }

    public void objectRegistered(Object node) {
      queueUpdate();
    }



    public void objectExecuted(Object node) {
      queueUpdate();
    }

    private void queueUpdate() {
      myTreeBuilder.refilter();
    }

    public void dispose() {
      Disposer.getTree().removeListener(this);
    }

    @Nullable
    public DisposerNode getSelectedNode() {
      final Set<DisposerNode> nodes = myTreeBuilder.getSelectedElements(DisposerNode.class);
      return nodes.size() == 1 ? nodes.iterator().next() : null;
    }

    public Tree getTree() {
      return myTree;
    }

    public void clear() {
      myModificationToFilter = Disposer.getTree().getModification();
      myTreeBuilder.refilter();
    }
  }

  public JComponent getComponent() {
    return this;
  }

  public String getName() {
    return "Disposer";
  }

  public void dispose() {
  }


  private static class DisposerStructure extends AbstractTreeStructureBase {
    private DisposerNode myRoot;

    private DisposerStructure(DisposerTree tree) {
      super(null);
      myRoot = new DisposerNode(tree, null);
    }

    public List<TreeStructureProvider> getProviders() {
      return null;
    }

    public Object getRootElement() {
      return myRoot;
    }

    public void commit() {
    }

    public boolean hasSomethingToCommit() {
      return false;
    }
  }

  private static class DisposerNode extends AbstractTreeNode<ObjectNode> {
    private DisposerTree myTree;

    private DisposerNode(DisposerTree tree, ObjectNode value) {
      super(null, value);
      myTree = tree;
    }

    @NotNull
    public Collection<? extends AbstractTreeNode> getChildren() {
      final ObjectNode value = getValue();
      if (value != null) {
        final Collection subnodes = value.getChildren();
        final ArrayList<DisposerNode> children = new ArrayList<DisposerNode>(subnodes.size());
        for (Iterator iterator = subnodes.iterator(); iterator.hasNext();) {
          children.add(new DisposerNode(myTree, (ObjectNode)iterator.next()));
        }
        return children;
      }
      else {
        final ObjectTree<Disposable> tree = Disposer.getTree();
        final THashSet<Disposable> root = tree.getRootObjects();
        ArrayList<DisposerNode> children = new ArrayList<DisposerNode>(root.size());
        for (Disposable each : root) {
          children.add(new DisposerNode(myTree, tree.getNode(each)));
        }
        return children;
      }
    }

    @Nullable
    public Throwable getAllocation() {
      return getValue() != null ? getValue().getAllocation() : null;
    }

    @Override
    protected boolean shouldUpdateData() {
      return true;
    }

    protected void update(PresentationData presentation) {
      if (getValue() != null) {
        final Object object = getValue().getObject();
        final String classString = object.getClass().toString();
        final String objectString = object.toString();

        presentation.setPresentableText(objectString);

        if (getValue().getOwnModification() < myTree.myModificationToFilter) {
          presentation.setForcedTextForeground(Color.gray);
        }

        if (objectString != null) {
          final int dogIndex = objectString.lastIndexOf("@");
          if (dogIndex >= 0) {
            final String fqNameObject = objectString.substring(0, dogIndex);
            final String fqNameClass = classString.substring("class ".length());
            if (fqNameObject.equals(fqNameClass)) return;
          }
        }

        presentation.setLocationString(classString);
      }
    }
  }

}