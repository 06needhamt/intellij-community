/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

/**
 * @author peter
 */
public abstract class DomTableView extends JPanel {
  @NonNls private static final String TREE = "Tree";
  @NonNls private static final String EMPTY_PANE = "EmptyPane";
  private final ListTableModel myTableModel = new MyListTableModel();
  private final TableView myTable = new TableView() {
    public boolean editCellAt(final int row, final int column, final EventObject e) {
      final boolean b = super.editCellAt(row, column, e);
      if (b) {
        final Component editor = getEditorComponent();
        editor.addFocusListener(new FocusAdapter() {
          public void focusLost(FocusEvent e) {
            if (!e.isTemporary() && myTable.isEditing()) {
              final Component oppositeComponent = e.getOppositeComponent();
              if (editor.equals(oppositeComponent)) return;
              editor.removeFocusListener(this);
              if (editor instanceof Container && ((Container)editor).isAncestorOf(oppositeComponent)) {
                oppositeComponent.addFocusListener(this);
              }
              else {
                myTable.getCellEditor().stopCellEditing();
              }
            }
          }
        });
      }
      return b;
    }

    public TableCellRenderer getCellRenderer(int row, int column) {
      return getTableCellRenderer(row, column, super.getCellRenderer(row, column), myTableModel.getItems().get(row));
    }
  };
  private final String myHelpID;
  private final String myEmptyPaneText;
  private final JPanel myInnerPanel;

  protected TableCellRenderer getTableCellRenderer(final int row, final int column, final TableCellRenderer superRenderer, final Object value) {
    return new StripeTableCellRenderer(superRenderer);
  }

  protected DomTableView() {
    this(null, null);
  }

  protected DomTableView(final String emptyPaneText, final String helpID) {
    super(new BorderLayout());
    myTableModel.setSortable(false);

    myEmptyPaneText = emptyPaneText;
    myHelpID = helpID;


    final JTableHeader header = myTable.getTableHeader();
    header.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        updateTooltip(e);
      }
    });
    header.addMouseListener(new MouseAdapter() {
      public void mouseEntered(MouseEvent e) {
        updateTooltip(e);
      }
    });
    header.setReorderingAllowed(false);

    myTable.setRowHeight(Icons.CLASS_ICON.getIconHeight());
    myTable.setPreferredScrollableViewportSize(new Dimension(-1, 150));
    myTable.setSelectionMode(allowMultipleRowsSelection() ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);

    myInnerPanel = new JPanel(new CardLayout());
    myInnerPanel.add(ScrollPaneFactory.createScrollPane(myTable), TREE);
    if (getEmptyPaneText() != null) {
      //noinspection HardCodedStringLiteral
      EmptyPane emptyPane = new EmptyPane("<html>" + getEmptyPaneText() + "</html>");
      final JComponent emptyPanel = emptyPane.getComponent();
      myInnerPanel.add(emptyPanel, EMPTY_PANE);
    }

    add(myInnerPanel, BorderLayout.CENTER);

    ToolTipManager.sharedInstance().registerComponent(myTable);
  }

  protected final void installPopup(final DefaultActionGroup group) {
    PopupHandler.installPopupHandler(myTable, group, ActionPlaces.J2EE_ATTRIBUTES_VIEW_POPUP, ActionManager.getInstance());
  }

  public final void setColumnInfos(final ColumnInfo[] columnInfos) {
    myTableModel.setColumnInfos(columnInfos);
    myTableModel.fireTableStructureChanged();
    adjustColumnWidths();
  }

  public final void setItems(List items) {
    if (myTable.isEditing()) {
      myTable.getCellEditor().cancelCellEditing();
    }
    final int row = myTable.getSelectedRow();
    myTableModel.setItems(new ArrayList(items));
    if (row >= 0 && row < myTableModel.getRowCount()) {
      myTable.getSelectionModel().setSelectionInterval(row, row);
    }
  }

  protected final void initializeTable() {
    myTable.setModel(myTableModel);
    if (getEmptyPaneText() != null) {
      final CardLayout cardLayout = ((CardLayout)myInnerPanel.getLayout());
      myTable.getModel().addTableModelListener(new TableModelListener() {
        public void tableChanged(TableModelEvent e) {
          cardLayout.show(myInnerPanel, myTable.getRowCount() == 0 ? EMPTY_PANE : TREE);
        }
      });
    }
    tuneTable(myTable);

    adjustColumnWidths();
    fireTableChanged();
  }

  protected final void fireTableChanged() {
    final int row = myTable.getSelectedRow();
    getTableModel().fireTableDataChanged();
    if (row >= 0 && row < myTableModel.getRowCount()) {
      myTable.getSelectionModel().setSelectionInterval(row, row);
    }
  }

  protected void adjustColumnWidths() {
    final ColumnInfo[] columnInfos = myTableModel.getColumnInfos();
    for (int i = 0; i < columnInfos.length; i++) {
      ColumnInfo columnInfo = columnInfos[i];
      final TableColumn column = myTable.getColumnModel().getColumn(i);
      int width = -1;
      for (int j = 0; j < myTableModel.getRowCount(); j++) {
        Object t = myTableModel.getItems().get(j);
        final Component component = myTable.getCellRenderer(j, i).getTableCellRendererComponent(myTable, columnInfo.valueOf(t), false, false, j, i);
        final int prefWidth = component.getPreferredSize().width;
        if (prefWidth > width) {
          width = prefWidth;
        }
      }
      if (width > 0) {
        column.setPreferredWidth(width);
      }
    }
  }

  protected String getEmptyPaneText() {
    return myEmptyPaneText;
  }

  protected final void updateTooltip(final MouseEvent e) {
    final int i = myTable.columnAtPoint(e.getPoint());
    if (i >= 0) {
      myTable.getTableHeader().setToolTipText(myTableModel.getColumnInfos()[i].getTooltipText());
    }
  }

  protected void tuneTable(JTable table) {
  }

  protected abstract Project getProject();

  protected boolean allowMultipleRowsSelection() {
    return true;
  }

  public final JTable getTable() {
    return myTable;
  }

  public final ListTableModel getTableModel() {
    return myTableModel;
  }

  @Nullable
  public Object getData(String dataId) {
    if (DataConstantsEx.HELP_ID.equals(dataId)) {
      return myHelpID;
    }
    return null;
  }

  private class MyListTableModel extends ListTableModel {
    public MyListTableModel() {
      super(ColumnInfo.EMPTY_ARRAY);
    }

    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      final Object oldValue = getValueAt(rowIndex, columnIndex);
      if (!Comparing.equal(oldValue, aValue)) {
        new WriteCommandAction(getProject()) {
          protected void run(final Result result) throws Throwable {
            MyListTableModel.super.setValueAt(aValue, rowIndex, columnIndex);
          }
        }.execute();
      }
    }
  }
}
