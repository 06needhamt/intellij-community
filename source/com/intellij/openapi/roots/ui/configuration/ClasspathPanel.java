/*
 * Copyright 2004-2005 Alexey Efimov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.DataManager;
import com.intellij.ide.IconUtilEx;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.j2ee.serverInstances.ApplicationServersManager;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryFileChooser;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
import com.intellij.openapi.roots.ui.util.CellAppearance;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Icons;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class ClasspathPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.ClasspathPanel");

  private final Project myProject;
  private final ModifiableRootModel myRootModel;
  private final ModulesProvider myModulesProvider;
  private final Table myEntryTable;
  private final MyTableModel myModel;
  private final EventDispatcher<OrderPanelListener> myListeners = EventDispatcher.create(OrderPanelListener.class);
  private PopupAction[] myPopupActions = null;
  private Icon[] myIcons = null;
  private static final Comparator<Library> LIBRARIES_COMPARATOR = new Comparator<Library>() {
    public int compare(Library elem1, Library elem2) {
      return elem1.getName().compareToIgnoreCase(elem2.getName());
    }
  };
  private static final Comparator<Module> MODULES_COMPARATOR = new Comparator<Module>() {
    public int compare(Module elem1, Module elem2) {
      return elem1.getName().compareToIgnoreCase(elem2.getName());
    }
  };

  protected ClasspathPanel(Project project, ModifiableRootModel rootModel, final ModulesProvider modulesProvider) {
    super(new BorderLayout());
    myProject = project;
    myRootModel = rootModel;
    myModulesProvider = modulesProvider;
    myModel = new MyTableModel(rootModel);
    myEntryTable = new Table(myModel);
    myEntryTable.setShowGrid(false);
    myEntryTable.setDragEnabled(false);
    myEntryTable.setShowHorizontalLines(false);
    myEntryTable.setShowVerticalLines(false);
    myEntryTable.setIntercellSpacing(new Dimension(0, 0));

    myEntryTable.setDefaultRenderer(TableItem.class, new TableItemRenderer());
    myEntryTable.setDefaultRenderer(Boolean.class, new ExportFlagRenderer(myEntryTable.getDefaultRenderer(Boolean.class)));
    myEntryTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    new SpeedSearchBase<Table>(myEntryTable) {
      public int getSelectedIndex() {
        return myEntryTable.getSelectedRow();
      }

      public Object[] getAllElements() {
        final int count = myModel.getRowCount();
        Object[] elements = new Object[count];
        for (int idx = 0; idx < count; idx++) {
          elements[idx] = myModel.getItemAt(idx);
        }
        return elements;
      }

      public String getElementText(Object element) {
        return getCellAppearance((TableItem)element, false).getText();
      }

      public void selectElement(Object element, String selectedText) {
        final int count = myModel.getRowCount();
        for (int row = 0; row < count; row++) {
          if (element.equals(myModel.getItemAt(row))) {
            myEntryTable.getSelectionModel().setSelectionInterval(row, row);
            TableUtil.scrollSelectionToVisible(myEntryTable);
            break;
          }
        }
      }
    };


    final FontMetrics fontMetrics = myEntryTable.getFontMetrics(myEntryTable.getFont());
    final int width = fontMetrics.stringWidth(" " + MyTableModel.EXPORT_COLUMN_NAME + " ") + 4;
    final TableColumn checkboxColumn = myEntryTable.getTableHeader().getColumnModel().getColumn(MyTableModel.EXPORT_COLUMN);
    checkboxColumn.setWidth(width);
    checkboxColumn.setPreferredWidth(width);
    checkboxColumn.setMaxWidth(width);
    checkboxColumn.setMinWidth(width);

    myEntryTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final int[] selectedRows = myEntryTable.getSelectedRows();
          boolean currentlyMarked = true;
          for (final int selectedRow : selectedRows) {
            final TableItem item = myModel.getItemAt(selectedRow);
            if (selectedRow < 0 || !item.isExportable()) {
              return;
            }
            currentlyMarked &= item.isExported();
          }
          for (final int selectedRow : selectedRows) {
            myModel.getItemAt(selectedRow).setExported(!currentlyMarked);
          }
          myModel.fireTableDataChanged();
          TableUtil.selectRows(myEntryTable, selectedRows);
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      JComponent.WHEN_FOCUSED
    );

    this.add(ScrollPaneFactory.createScrollPane(myEntryTable), BorderLayout.CENTER);
    this.add(createButtonsBlock(), BorderLayout.EAST);

    if (myEntryTable.getRowCount() > 0) {
      myEntryTable.getSelectionModel().setSelectionInterval(0,0);
    }
  }


  private JComponent createButtonsBlock() {
    final JButton addButton = new JButton(ProjectBundle.message("button.add"));
    final JButton removeButton = new JButton(ProjectBundle.message("button.remove"));
    final JButton editButton = new JButton(ProjectBundle.message("button.edit"));
    final JButton upButton = new JButton(ProjectBundle.message("button.move.up"));
    final JButton downButton = new JButton(ProjectBundle.message("button.move.down"));

    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(addButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));
    panel.add(editButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));
    panel.add(removeButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));
    panel.add(upButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));
    panel.add(downButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));

    myEntryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        final int[] selectedRows = myEntryTable.getSelectedRows();
        boolean editButtonEnabled = selectedRows.length == 1;
        boolean removeButtonEnabled = true;
        int minRow = myEntryTable.getRowCount() + 1;
        int maxRow = -1;
        for (final int selectedRow : selectedRows) {
          minRow = Math.min(minRow, selectedRow);
          maxRow = Math.max(maxRow, selectedRow);
          final TableItem item = myModel.getItemAt(selectedRow);
          if (!item.isRemovable()) {
            removeButtonEnabled = false;
          }
          if (!item.isEditable()) {
            editButtonEnabled = false;
          }
        }
        upButton.setEnabled(minRow > 0 && minRow < myEntryTable.getRowCount());
        downButton.setEnabled(maxRow >= 0 && maxRow < myEntryTable.getRowCount() - 1);
        editButton.setEnabled(editButtonEnabled);
        removeButton.setEnabled(removeButtonEnabled);
      }
    });

    upButton.addActionListener(new ButtonAction() {
      protected void executeImpl() {
        moveSelectedRows(-1);
      }
    });
    downButton.addActionListener(new ButtonAction() {
      protected void executeImpl() {
        moveSelectedRows(+1);
      }
    });

    myEntryTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && editButton.isEnabled()) {
          editButton.doClick();
        }
      }
    });

    addKeyboardShortcut(myEntryTable, removeButton, KeyEvent.VK_DELETE, 0);
    addKeyboardShortcut(myEntryTable, addButton, KeyEvent.VK_INSERT, 0);
    addKeyboardShortcut(myEntryTable, upButton, KeyEvent.VK_UP, KeyEvent.CTRL_DOWN_MASK);
    addKeyboardShortcut(myEntryTable, downButton, KeyEvent.VK_DOWN, KeyEvent.CTRL_DOWN_MASK);

    addButton.addActionListener(new ButtonAction() {
      protected void executeImpl() {
        initPopupActions();
        final JBPopup popup = JBPopupFactory.getInstance().createWizardStep(new BaseListPopupStep<PopupAction>(null, myPopupActions, myIcons) {
          public boolean isMnemonicsNavigationEnabled() {
            return true;
          }
          public boolean isSelectable(PopupAction value) {
            return value.isSelectable();
          }
          public PopupStep onChosen(final PopupAction selectedValue, final boolean finalChoice) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                selectedValue.execute();
              }
            }, ModalityState.stateForComponent(ClasspathPanel.this));
            return PopupStep.FINAL_CHOICE;
          }
          public String getTextFor(PopupAction value) {
            return "&" + value.getIndex() + "  " + value.getTitle();
          }
        });
        popup.showUnderneathOf(addButton);
      }
    });

    editButton.addActionListener(new ButtonAction() {
      protected void executeImpl() {
        final int row = myEntryTable.getSelectedRow();
        final TableItem tableItem = myModel.getItemAt(row);
        if (tableItem instanceof JdkItem) {
          editJdk((JdkItem)tableItem, row);
        }
        else if (tableItem instanceof LibItem) {
          final LibraryOrderEntry entry = ((LibItem)tableItem).getEntry();
          if (entry != null && entry.isValid()) {
            final Library library = entry.getLibrary();
            final LibraryTable moduleLibraryTable = myRootModel.getModuleLibraryTable();
            final boolean isModuleLibrary = moduleLibraryTable.getTableLevel().equals(entry.getLibraryLevel());
            final LibraryTable table = isModuleLibrary ? moduleLibraryTable : library.getTable();
            final Library[] libraries = table.getLibraries();
            // need the following code because library instance can be proxied
            final ArrayList<Library> toSelect = new ArrayList<Library>(1);
            for (final Library lib : libraries) {
              if (lib.equals(library)) { // the order is important, not library.equals(lib)!
                toSelect.add(lib);
              }
            }
            LibraryTableEditor.showEditDialog(ClasspathPanel.this, table, toSelect);
            // library's name may have changed, so update the row with the entry
            if (isModuleLibrary) {
              // there might be new libraries edded from this dialog
              forceInitFromModel();
            }
            else {
              myModel.fireTableRowsUpdated(row, row);
            }
          }
        }
      }
    });
    removeButton.addActionListener(new ButtonAction() {
      protected void executeImpl() {
        final List removedRows = TableUtil.removeSelectedItems(myEntryTable);
        if (removedRows.size() == 0) {
          return;
        }
        final LibraryTable moduleLibraryTable = myRootModel.getModuleLibraryTable();
        LibraryTable.ModifiableModel modifiableModel = null;
        for (Iterator it = removedRows.iterator(); it.hasNext();) {
          final TableItem item = (TableItem)((Object[])it.next())[MyTableModel.ITEM_COLUMN];
          final OrderEntry orderEntry = item.getEntry();
          if (orderEntry == null) {
            continue;
          }
          if (orderEntry instanceof LibraryOrderEntry) {
            final LibraryOrderEntry libEntry = (LibraryOrderEntry)orderEntry;
            if (libEntry.isValid() && moduleLibraryTable.getTableLevel().equals(libEntry.getLibraryLevel())) {
              if (modifiableModel == null) {
                modifiableModel = moduleLibraryTable.getModifiableModel();
              }
              modifiableModel.removeLibrary(libEntry.getLibrary());
              continue;
            }
          }
          myRootModel.removeOrderEntry(orderEntry);
        }
        if (modifiableModel != null) {
          modifiableModel.commit();
        }
        final int[] selectedRows = myEntryTable.getSelectedRows();
        myModel.fireTableDataChanged();
        TableUtil.selectRows(myEntryTable, selectedRows);
      }
    });
    return panel;
  }

  private static void addKeyboardShortcut(final JComponent target, final JButton button, final int keyEvent, final int modifiers) {
    target.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (button.isEnabled()) {
            button.doClick();
          }
        }
      },
      KeyStroke.getKeyStroke(keyEvent, modifiers),
      JComponent.WHEN_FOCUSED
    );
  }

  private void editJdk(JdkItem item, int row) {
    final EditJdkDialog editJdkDialog = new EditJdkDialog();
    editJdkDialog.show();
    if (editJdkDialog.isOK()) {
      if (editJdkDialog.isInheritJdk()) {
        if (!myRootModel.isJdkInherited()) {
          myRootModel.inheritJdk();
        }
      }
      else { // use custom
        final ProjectJdk jdk = editJdkDialog.getSelectedModuleJdk();
        if (jdk != null) {
          if (!jdk.equals(myRootModel.getJdk())) {
            myRootModel.setJdk(jdk);
          }
        }
        else {
          myRootModel.setJdk(null);
        }
      }
      // now update my order entry
      JdkOrderEntry jdkEntry = null;
      final OrderEntry[] orderEntries = myRootModel.getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) {
          jdkEntry = (JdkOrderEntry)orderEntry;
          break;
        }
      }
      item.setEntry(jdkEntry);
      myModel.fireTableRowsUpdated(row, row);
    }
  }

  private abstract class ButtonAction implements ActionListener {
    public final void actionPerformed(ActionEvent e) {
      execute();
    }

    public final void execute() {
      try {
        disableModelUpdate();
        executeImpl();
      }
      finally {
        enableModelUpdate();
        myEntryTable.requestFocus();
      }
    }

    protected abstract void executeImpl();
  }

  private class PopupAction extends ButtonAction{
    private final String myTitle;
    private final Icon myIcon;
    private final int myIndex;

    protected PopupAction(String title, Icon icon, final int index) {
      myTitle = title;
      myIcon = icon;
      myIndex = index;
    }

    public String getTitle() {
      return myTitle;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public int getIndex() {
      return myIndex;
    }

    public boolean isSelectable() {
      return true;
    }

    protected void executeImpl() {
    }
  }

  private abstract class ChooseAndAddAction<ItemType> extends PopupAction{
    public ChooseAndAddAction(int index, String title, Icon icon) {
      super(title, icon, index);
    }

    protected final void executeImpl() {
      final ChooserDialog<ItemType> dialog = createChooserDialog();
      if (dialog == null) {
        return;
      }
      dialog.doChoose();
      if (!dialog.isOK()) {
        return;
      }
      final List<ItemType> chosen = dialog.getChosenElements();
      if (chosen.size() == 0) {
        return;
      }
      //int insertionIndex = myEntryTable.getSelectedRow();
      for (ItemType item : chosen) {
        //myModel.addItemAt(createTableItem(item), insertionIndex++);
        myModel.addItem(createTableItem(item));
      }
      myModel.fireTableDataChanged();
      final ListSelectionModel selectionModel = myEntryTable.getSelectionModel();
      //selectionModel.setSelectionInterval(insertionIndex - chosen.size(), insertionIndex - 1);
      selectionModel.setSelectionInterval(myModel.getRowCount() - chosen.size(), myModel.getRowCount() - 1);
      TableUtil.scrollSelectionToVisible(myEntryTable);
    }

    protected abstract TableItem createTableItem(final ItemType item);

    protected abstract @Nullable ChooserDialog<ItemType> createChooserDialog();
  }

  private void initPopupActions() {
    if (myPopupActions == null) {
      myPopupActions = new PopupAction[] {
        new ChooseAndAddAction<Library>(1, ProjectBundle.message("classpath.add.jar.directory.action"), Icons.JAR_ICON) {
          protected TableItem createTableItem(final Library item) {
            final OrderEntry[] entries = myRootModel.getOrderEntries();
            for (OrderEntry entry : entries) {
              if (entry instanceof LibraryOrderEntry) {
                final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
                if (item.equals(libraryOrderEntry.getLibrary())) {
                  return new LibItem(libraryOrderEntry);
                }
              }
            }
            LOG.assertTrue(false, "Unknown library " + item);
            return null;
          }

          protected ChooserDialog<Library> createChooserDialog() {
            return new ChooseModuleLibrariesDialog(ClasspathPanel.this, myRootModel.getModuleLibraryTable());
          }
        },
        new ChooseNamedLibraryAction(2, ProjectBundle.message("classpath.add.project.library.action"), LibraryTablesRegistrar.getInstance().getLibraryTable(myProject)),
        new ChooseNamedLibraryAction(3, ProjectBundle.message("classpath.add.global.library.action"), LibraryTablesRegistrar.getInstance().getLibraryTable()),
        new ChooseNamedLibraryAction(4, ProjectBundle.message("classpath.add.appserver.library.action"), ApplicationServersManager.getInstance().getLibraryTable()),
        new ChooseAndAddAction<Module>(5, ProjectBundle.message("classpath.add.module.dependency.action"), IconUtilEx.getModuleTypeIcon(ModuleType.JAVA, 0)) {
          protected TableItem createTableItem(final Module item) {
            return new ModuleItem(myRootModel.addModuleOrderEntry(item));
          }
          protected ChooserDialog<Module> createChooserDialog() {
            final List<Module> chooseItems = getDependencyModules();
            if (chooseItems.size() == 0) {
              Messages.showMessageDialog(ClasspathPanel.this, ProjectBundle.message("message.no.module.dependency.candidates"), getTitle(), Messages.getInformationIcon());
              return null;
            }
            return new ChooseModulesDialog(chooseItems, ProjectBundle.message("classpath.chooser.title.add.module.dependency"));
          }
        }
      };

      myIcons = new Icon[myPopupActions.length];
      for (int idx = 0; idx < myPopupActions.length; idx++) {
        myIcons[idx] = myPopupActions[idx].getIcon();
      }
    }
  }


  private void enableModelUpdate() {
    myInsideChange--;
  }

  private void disableModelUpdate() {
    myInsideChange++;
  }

  public void addListener(OrderPanelListener listener) {
    myListeners.addListener(listener);
  }

  public void removeListener(OrderPanelListener listener) {
    myListeners.removeListener(listener);
  }

  private void moveSelectedRows(int increment) {
    if (increment == 0) {
      return;
    }
    if (myEntryTable.isEditing()){
      myEntryTable.getCellEditor().stopCellEditing();
    }
    final ListSelectionModel selectionModel = myEntryTable.getSelectionModel();
    for(int row = increment < 0? 0 : myModel.getRowCount() - 1; increment < 0? row < myModel.getRowCount() : row >= 0; row += (increment < 0? +1 : -1)){
      if (selectionModel.isSelectedIndex(row)) {
        final int newRow = moveRow(row, increment);
        selectionModel.removeSelectionInterval(row, row);
        selectionModel.addSelectionInterval(newRow, newRow);
      }
    }
    myModel.fireTableRowsUpdated(0, myModel.getRowCount() - 1);
    Rectangle cellRect = myEntryTable.getCellRect(selectionModel.getMinSelectionIndex(), 0, true);
    if (cellRect != null) {
      myEntryTable.scrollRectToVisible(cellRect);
    }
    myEntryTable.repaint();
    myListeners.getMulticaster().entryMoved();
  }

  private int moveRow(final int row, final int increment) {
    int newIndex = Math.abs(row + increment) % myModel.getRowCount();
    final TableItem item = myModel.removeDataRow(row);
    myModel.addItemAt(item, newIndex);
    return newIndex;
  }

  public void stopEditing() {
    TableUtil.stopEditing(myEntryTable);
  }

  public List<OrderEntry> getEntries() {
    final int count = myModel.getRowCount();
    final List<OrderEntry> entries = new ArrayList<OrderEntry>(count);
    for (int row = 0; row < count; row++) {
      final OrderEntry entry = myModel.getItemAt(row).getEntry();
      if (entry != null) {
        entries.add(entry);
      }
    }
    return entries;
  }

  private int myInsideChange = 0;
  public void initFromModel() {
    if (myInsideChange == 0) {
      forceInitFromModel();
    }
  }

  private void forceInitFromModel() {
    final int[] selection = myEntryTable.getSelectedRows();
    myModel.clear();
    myModel.init();
    myModel.fireTableDataChanged();
    TableUtil.selectRows(myEntryTable, selection);
  }

  private List<Module> getDependencyModules() {
    final int rowCount = myModel.getRowCount();
    final Set<String> filtered = new HashSet<String>(rowCount);
    for (int row = 0; row < rowCount; row++) {
      final OrderEntry entry = myModel.getItemAt(row).getEntry();
      if (entry instanceof ModuleOrderEntry) {
        filtered.add(((ModuleOrderEntry)entry).getModuleName());
      }
    }
    final Module self = myModulesProvider.getModule(myRootModel.getModule().getName());
    filtered.add(self.getName());

    final Module[] modules = myModulesProvider.getModules();
    final List<Module> elements = new ArrayList<Module>(modules.length);
    for (final Module module : modules) {
      if (!filtered.contains(module.getName())) {
        elements.add(module);
      }
    }
    Collections.sort(elements, MODULES_COMPARATOR);
    return elements;
  }


  private static abstract class TableItem<T extends OrderEntry> {
    protected @Nullable T myEntry;

    protected TableItem(@Nullable T entry) {
      myEntry = entry;
    }

    public final boolean isExportable() {
      return myEntry instanceof ExportableOrderEntry;
    }

    public final boolean isExported() {
      return isExportable()? ((ExportableOrderEntry)myEntry).isExported() : false;
    }

    public final void setExported(boolean isExported) {
      if (isExportable()) {
        ((ExportableOrderEntry)myEntry).setExported(isExported);
      }
    }

    public final @Nullable T getEntry() {
      return myEntry;
    }

    public final void setEntry(T entry) {
      myEntry = entry;
    }

    public abstract boolean isRemovable();
    public abstract boolean isEditable();
  }

  private static class LibItem extends TableItem<LibraryOrderEntry> {
    public LibItem(LibraryOrderEntry entry) {
      super(entry);
    }

    public boolean isRemovable() {
      return true;
    }

    public boolean isEditable() {
      final LibraryOrderEntry orderEntry = getEntry();
      return orderEntry != null && orderEntry.isValid();
    }
  }

  private static class ModuleItem extends TableItem<ModuleOrderEntry> {
    public ModuleItem(final ModuleOrderEntry entry) {
      super(entry);
    }

    public boolean isRemovable() {
      return true;
    }

    public boolean isEditable() {
      return true;
    }
  }

  private static class JdkItem extends TableItem<JdkOrderEntry> {
    public JdkItem(final JdkOrderEntry entry) {
      super(entry);
    }

    public boolean isRemovable() {
      return false;
    }

    public boolean isEditable() {
      return true;
    }
  }

  private static class SelfModuleItem extends TableItem<ModuleSourceOrderEntry> {
    public SelfModuleItem(final ModuleSourceOrderEntry entry) {
      super(entry);
    }

    public boolean isRemovable() {
      return false;
    }

    public boolean isEditable() {
      return false;
    }
  }

  private static class MyTableModel extends AbstractTableModel implements ItemRemovable {
    private static final String EXPORT_COLUMN_NAME = ProjectBundle.message("modules.order.export.export.column");
    public static final int EXPORT_COLUMN = 0;
    public static final int ITEM_COLUMN = 1;
    private final java.util.List<TableItem> myItems = new ArrayList<TableItem>();
    private final ModifiableRootModel myRootModel;

    public MyTableModel(final ModifiableRootModel rootModel) {
      myRootModel = rootModel;
      init();
    }

    public void init() {
      final OrderEntry[] orderEntries = myRootModel.getOrderEntries();
      boolean hasJdkOrderEntry = false;
      for (final OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) {
          hasJdkOrderEntry = true;
        }
        addOrderEntry(orderEntry);
      }
      if (!hasJdkOrderEntry) {
        addItemAt(new JdkItem(null), 0);
      }
    }

    private void addOrderEntry(OrderEntry orderEntry) {
      if (orderEntry instanceof JdkOrderEntry) {
        addItem(new JdkItem(((JdkOrderEntry)orderEntry)));
      }
      else if (orderEntry instanceof LibraryOrderEntry) {
        addItem(new LibItem(((LibraryOrderEntry)orderEntry)));
      }
      else if (orderEntry instanceof ModuleOrderEntry) {
        addItem(new ModuleItem(((ModuleOrderEntry)orderEntry)));
      }
      else if (orderEntry instanceof ModuleSourceOrderEntry) {
        addItem(new SelfModuleItem(((ModuleSourceOrderEntry)orderEntry)));
      }
    }

    public TableItem getItemAt(int row) {
      return myItems.get(row);
    }

    public void addItem(TableItem item) {
      myItems.add(item);
    }

    public void addItemAt(TableItem item, int row) {
      myItems.add(row, item);
    }

    public TableItem removeDataRow(int row) {
      return myItems.remove(row);
    }

    public void removeRow(int row) {
      removeDataRow(row);
    }

    public void clear() {
      myItems.clear();
    }

    public int getRowCount() {
      return myItems.size();
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      final TableItem item = myItems.get(rowIndex);
      if (columnIndex == EXPORT_COLUMN) {
        return Boolean.valueOf(item.isExported());
      }
      if (columnIndex == ITEM_COLUMN) {
        return item;
      }
      LOG.assertTrue(false, "Incorrect column index: " + columnIndex);
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      final TableItem item = myItems.get(rowIndex);
      if (columnIndex == EXPORT_COLUMN) {
        item.setExported(((Boolean)aValue).booleanValue());
      }
    }

    public String getColumnName(int column) {
      if (column == EXPORT_COLUMN) {
        return EXPORT_COLUMN_NAME;
      }
      return "";
    }

    public Class getColumnClass(int column) {
      if (column == EXPORT_COLUMN) {
        return Boolean.class;
      }
      if (column == ITEM_COLUMN) {
        return TableItem.class;
      }
      return super.getColumnClass(column);
    }

    public int getColumnCount() {
      return 2;
    }

    public boolean isCellEditable(int row, int column) {
      if (column == EXPORT_COLUMN) {
        final TableItem item = myItems.get(row);
        return item.isExportable();
      }
      return false;
    }
  }

  private CellAppearance getCellAppearance(final TableItem item, final boolean selected) {
    if (item instanceof JdkItem && item.getEntry() == null) {
      return CellAppearanceUtils.forJdk(null, false, selected);
    }
    else {
      return CellAppearanceUtils.forOrderEntry(item.getEntry(), selected);
    }
  }

  private class TableItemRenderer extends ColoredTableCellRenderer {
    private final Border NO_FOCUS_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);

    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setPaintFocusBorder(false);
      setFocusBorderAroundIcon(true);
      setBorder(NO_FOCUS_BORDER);
      final TableItem item = (TableItem)value;
      getCellAppearance(item, selected).customize(this);
    }
  }

  private static class ExportFlagRenderer implements TableCellRenderer {
    private final TableCellRenderer myDelegate;
    private final JPanel myBlankPanel;

    public ExportFlagRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
      myBlankPanel = new JPanel();
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (!table.isCellEditable(row, column)) {
        myBlankPanel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return myBlankPanel;
      }
      return myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
  }

  private static interface ChooserDialog<T> {
    List<T> getChosenElements();
    void doChoose();
    boolean isOK();
  }

  private class ChooseModulesDialog extends DialogWrapper implements ChooserDialog<Module>{
    protected ElementsChooser<Module> myChooser;

    public ChooseModulesDialog(final List<Module> items, final String title) {
      super(ClasspathPanel.this, true);
      setTitle(title);
      myChooser = new ElementsChooser<Module>(false) {
        protected String getItemText(final Module item) {
          return ChooseModulesDialog.this.getItemText(item);
        }
      };
      myChooser.setColorUnmarkedElements(false);

      setElements(items, items.size() > 0? items.subList(0, 1) : Collections.<Module>emptyList());
      myChooser.getComponent().registerKeyboardAction(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            doOKAction();
          }
        },
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        JComponent.WHEN_FOCUSED
      );
      init();
    }

    public void doChoose() {
      show();
    }

    public List<Module> getChosenElements() {
      return myChooser.getSelectedElements();
    }

    public JComponent getPreferredFocusedComponent() {
      return myChooser.getComponent();
    }

    protected JComponent createCenterPanel() {
      return ScrollPaneFactory.createScrollPane(myChooser.getComponent());
    }

    private void setElements(final Collection<Module> elements, final Collection<Module> elementsToSelect) {
      myChooser.clear();
      for (final Module item : elements) {
        myChooser.addElement(item, false, createElementProperties(item));
      }
      myChooser.selectElements(elementsToSelect);
    }

    private ElementsChooser.ElementProperties createElementProperties(final Module item) {
      return new ElementsChooser.ElementProperties() {
        public Icon getIcon() {
          return IconUtilEx.getIcon(item, 0);
        }
        public Color getColor() {
          return null;
        }
      };
    }

    private String getItemText(final Module item) {
      return item.getName();
    }
  }

  private static class ChooseModuleLibrariesDialog extends LibraryFileChooser implements ChooserDialog<Library> {
    private Pair<String, VirtualFile[]> myLastChosen;
    private final LibraryTable myLibraryTable;

    public ChooseModuleLibrariesDialog(Component parent, final LibraryTable libraryTable) {
      super(createFileChooserDescriptor(parent), parent, false, null);
      myLibraryTable = libraryTable;
    }

    private static FileChooserDescriptor createFileChooserDescriptor(Component parent) {
      final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, true, false, false, true);
      final Module contextModule = (Module)DataManager.getInstance().getDataContext(parent).getData(DataConstantsEx.MODULE_CONTEXT);
      descriptor.setContextModule(contextModule);
      return descriptor;
    }

    public List<Library> getChosenElements() {
      if (myLastChosen == null) {
        return Collections.emptyList();
      }
      final VirtualFile[] files = filterAlreadyAdded(myLastChosen.getSecond());
      if (files.length == 0) {
        return Collections.emptyList();
      }
      final LibraryTable.ModifiableModel modifiableModel = myLibraryTable.getModifiableModel();
      final List<Library> addedLibraries = new ArrayList<Library>(files.length);
      for (VirtualFile file : files) {
        final Library library = modifiableModel.createLibrary(null);
        final Library.ModifiableModel libModel = library.getModifiableModel();
        libModel.addRoot(file, OrderRootType.CLASSES);
        libModel.commit();
        addedLibraries.add(library);
      }
      modifiableModel.commit();
      return addedLibraries;
    }

    private VirtualFile[] filterAlreadyAdded(final VirtualFile[] files) {
      if (files == null || files.length == 0) {
        return VirtualFile.EMPTY_ARRAY;
      }
      final Set<VirtualFile> chosenFilesSet = new HashSet<VirtualFile>(Arrays.asList(files));
      final Set<VirtualFile> alreadyAdded = new HashSet<VirtualFile>();
      final Library[] libraries = myLibraryTable.getLibraries();
      for (Library library : libraries) {
        final VirtualFile[] libraryFiles = library.getFiles(OrderRootType.CLASSES);
        for (VirtualFile libraryFile : libraryFiles) {
          alreadyAdded.add(libraryFile);
        }
      }
      chosenFilesSet.removeAll(alreadyAdded);
      return chosenFilesSet.toArray(new VirtualFile[chosenFilesSet.size()]);
    }

    public void doChoose() {
      myLastChosen = chooseNameAndFiles();
    }
  }

  private class EditJdkDialog extends DialogWrapper{
    private JRadioButton myRbUseProjectJdk;
    private JRadioButton myRbUseCustomJdk;
    private SimpleColoredComponent myProjectJdkNameLabel;
    private JdkComboBox myCbModuleJdk;
    private JButton myChangeProjectJdkButton;
    private JButton myChangeModuleJdkButton;
    private ProjectJdk mySelectedModuleJdk = null;

    public EditJdkDialog() {
      super(ClasspathPanel.this, true);
      setTitle("Set Module JDK");
      init();
    }

    /**
     * @return null if JDK should be inherited
     */
    public ProjectJdk getSelectedModuleJdk() {
      return mySelectedModuleJdk;
    }

    public boolean isInheritJdk() {
      return myRbUseProjectJdk.isSelected();
    }

    protected JComponent createCenterPanel() {
      final JPanel jdkPanel = new JPanel(new GridBagLayout());
      myProjectJdkNameLabel = new SimpleColoredComponent();
      myCbModuleJdk = new JdkComboBox();
      myCbModuleJdk.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            mySelectedModuleJdk = myCbModuleJdk.getSelectedJdk();
          }
        }
      });

      myRbUseProjectJdk = new JRadioButton(ProjectBundle.message("module.libraries.target.jdk.project.radio"), myRootModel.isJdkInherited());
      myRbUseCustomJdk = new JRadioButton(ProjectBundle.message("module.libraries.target.jdk.module.radio"), !myRootModel.isJdkInherited());
      final ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(myRbUseProjectJdk);
      buttonGroup.add(myRbUseCustomJdk);
      myRbUseProjectJdk.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            updateJdkPanelControls();
          }
        }
      });
      myRbUseCustomJdk.addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          if (e.getStateChange() == ItemEvent.SELECTED) {
            updateJdkPanelControls();
          }
        }
      });

      myChangeProjectJdkButton = new FixedSizeButton(20);
      myChangeProjectJdkButton.addActionListener(new ConfigureJdkAction(myChangeProjectJdkButton) {
        public ProjectJdk getProjectJdkToSelect() {
          return ProjectRootManager.getInstance(myProject).getProjectJdk();
        }

        public void setChosenJdk(ProjectJdk jdk) {
          ProjectRootManager.getInstance(myProject).setProjectJdk(jdk);
        }
      });
      myChangeModuleJdkButton = new FixedSizeButton(20);
      myChangeModuleJdkButton.addActionListener(new ConfigureJdkAction(myChangeModuleJdkButton) {
        public ProjectJdk getProjectJdkToSelect() {
          return myRootModel.getJdk();
        }

        public void setChosenJdk(ProjectJdk jdk) {
          myCbModuleJdk.update(jdk);
        }
      });

      myCbModuleJdk.setPreferredSize(new Dimension(myCbModuleJdk.getPreferredSize().width, myChangeModuleJdkButton.getPreferredSize().height));

      jdkPanel.add(myRbUseProjectJdk,
                   new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                          new Insets(0, 6, 0, 0), 0, 0));
      jdkPanel.add(myProjectJdkNameLabel,
                   new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.BOTH,
                                          new Insets(0, 2, 0, 0), 0, 0));
      jdkPanel.add(myChangeProjectJdkButton,
                   new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE,
                                          new Insets(0, 4, 0, 6), 0, 0));

      jdkPanel.add(myRbUseCustomJdk,
                   new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                          new Insets(5, 6, 0, 0), 0, 0));
      jdkPanel.add(myCbModuleJdk,
                   new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                          new Insets(5, 2, 0, 0), 0, 0));
      jdkPanel.add(myChangeModuleJdkButton,
                   new GridBagConstraints(2, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.EAST, GridBagConstraints.NONE,
                                          new Insets(5, 4, 0, 6), 0, 0));

      jdkPanel.add(Box.createHorizontalGlue(),
                   new GridBagConstraints(3, GridBagConstraints.RELATIVE, 1, 2, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
                                          new Insets(0, 0, 0, 0), 0, 0));

      updateJdkPanelControls();
      return jdkPanel;
    }

    private void updateJdkPanelControls() {
      myChangeModuleJdkButton.setEnabled(myRbUseCustomJdk.isSelected());
      myCbModuleJdk.setEnabled(myRbUseCustomJdk.isSelected());

      myChangeProjectJdkButton.setEnabled(myRbUseProjectJdk.isSelected());
      myProjectJdkNameLabel.setEnabled(myRbUseProjectJdk.isSelected());
      myProjectJdkNameLabel.clear();

      final CellAppearance appearance = CellAppearanceUtils.forProjectJdk(myProject);
      appearance.customize(myProjectJdkNameLabel);
      myProjectJdkNameLabel.repaint();

      if (!myRootModel.isJdkInherited()) {
        final ProjectJdk modelJdk = myRootModel.getJdk();
        if (modelJdk != null) {
          myCbModuleJdk.setSelectedJdk(modelJdk);
        }
        else {
          final OrderEntry[] orderEntries = myRootModel.getOrderEntries();
          String moduleJdkName = null;
          for (final OrderEntry orderEntry : orderEntries) {
            if (orderEntry instanceof JdkOrderEntry) {
              moduleJdkName = ((JdkOrderEntry)orderEntry).getJdkName();
              if (moduleJdkName != null) {
                break;
              }
            }
          }
          if (moduleJdkName != null) {
            myCbModuleJdk.setInvalidJdk(moduleJdkName);
          }
          else {
            myCbModuleJdk.setSelectedJdk(null);
          }
        }
      }
      else {
        myCbModuleJdk.setSelectedJdk(mySelectedModuleJdk);
      }
    }

    private abstract class ConfigureJdkAction implements ActionListener {
      private final JComponent myParent;

      protected ConfigureJdkAction(JComponent parent) {
        myParent = parent;
      }

      public abstract ProjectJdk getProjectJdkToSelect();
      public abstract void setChosenJdk(ProjectJdk jdk);

      public void actionPerformed(ActionEvent e) {
        ProjectJdksEditor editor = new ProjectJdksEditor(getProjectJdkToSelect(), myParent);
        editor.show();
        if (editor.isOK()) {
          final ProjectJdk selectedJdk = editor.getSelectedJdk();
          if (selectedJdk != null) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                setChosenJdk(selectedJdk);
              }
            });
          }
        }
        updateJdkPanelControls(); // should update, no matter pressed user ok or cancel (the jdk itself could change)
      }
    }

  }

  private class ChooseNamedLibraryAction extends ChooseAndAddAction<Library> {
    private LibraryTable myLibraryTable;

    public ChooseNamedLibraryAction(final int index, final String title, final LibraryTable libraryTable) {
      super(index, title, Icons.LIBRARY_ICON);
      myLibraryTable = libraryTable;
    }

    protected TableItem createTableItem(final Library item) {
      // clear invalid order entry corresponding to added library if any
      final OrderEntry[] orderEntries = myRootModel.getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry && !orderEntry.isValid()) {
          if (item.getName().equals(((LibraryOrderEntry)orderEntry).getLibraryName())) {
            myRootModel.removeOrderEntry(orderEntry);
          }
        }
      }
      return new LibItem(myRootModel.addLibraryEntry(item));
    }

    protected ChooserDialog<Library> createChooserDialog() {
      return new ChooserDialog<Library>() {
        private LibraryTableEditor myEditor = LibraryTableEditor.editLibraryTable(myLibraryTable);
        private boolean myIsOk;

        public List<Library> getChosenElements() {
          final List<Library> chosen = new ArrayList<Library>(Arrays.asList(myEditor.getSelectedLibraries()));
          chosen.removeAll(getAlreadyAddedLibraries());
          return chosen;
        }

        public void doChoose() {
          final Iterator iter = myLibraryTable.getLibraryIterator();
          myIsOk = myEditor.openDialog(ClasspathPanel.this, iter.hasNext()? Collections.singleton((Library)iter.next()) : Collections.<Library>emptyList(), false);
        }

        public boolean isOK() {
          return myIsOk;
        }
      };
    }

    private Collection<Library> getAlreadyAddedLibraries() {
      final OrderEntry[] orderEntries = myRootModel.getOrderEntries();
      final Set<Library> result = new HashSet<Library>(orderEntries.length);
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry && orderEntry.isValid()) {
          result.add(((LibraryOrderEntry)orderEntry).getLibrary());
        }
      }
      return result;
    }

  }
}
