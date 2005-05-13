package com.intellij.ide.structureView.newStructureView;

import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.FileEditorPositionListener;
import com.intellij.ide.structureView.ModelListener;

import java.util.ArrayList;

public class TreeModelWrapper implements StructureViewModel {
  private final StructureViewModel myModel;
  private final TreeActionsOwner myStructureView;

  public TreeModelWrapper(StructureViewModel model, TreeActionsOwner structureView) {
    myModel = model;
    myStructureView = structureView;
  }

  public StructureViewTreeElement getRoot() {
    return myModel.getRoot();
  }

  public Grouper[] getGroupers() {
    ArrayList<TreeAction> filtered = filterActive(myModel.getGroupers());
    return filtered.toArray(new Grouper[filtered.size()]);
  }

  private ArrayList<TreeAction> filterActive(TreeAction[] actions) {
    ArrayList<TreeAction> filtered = new ArrayList<TreeAction>();
    for (TreeAction action : actions) {
      if (myStructureView.isActionActive(action.getName())) filtered.add(action);
    }
    return filtered;
  }

  public Sorter[] getSorters() {
    ArrayList<TreeAction> filtered = filterActive(myModel.getSorters());
    return filtered.toArray(new Sorter[filtered.size()]);
  }

  public Filter[] getFilters() {
    ArrayList<TreeAction> filtered = filterActive(myModel.getFilters());
    return filtered.toArray(new Filter[filtered.size()]);
  }

  public Object getCurrentEditorElement() {
    return myModel.getCurrentEditorElement();
  }

  public static boolean isActive(final TreeAction action, final TreeActionsOwner actionsOwner) {
    return shouldRevert(action) ?  !actionsOwner.isActionActive(action.getName()) : actionsOwner.isActionActive(action.getName());
  }

  public static boolean shouldRevert(final TreeAction action) {
    return action instanceof Filter && ((Filter)action).isReverted();
  }

  public void addEditorPositionListener(FileEditorPositionListener listener) {
    myModel.addEditorPositionListener(listener);
  }

  public void removeEditorPositionListener(FileEditorPositionListener listener) {
    myModel.removeEditorPositionListener(listener);
  }

  public void dispose() {
    myModel.dispose();
  }

  public void addModelListener(ModelListener modelListener) {
    myModel.addModelListener(modelListener);
  }

  public void removeModelListener(ModelListener modelListener) {
    myModel.removeModelListener(modelListener);
  }
}
