package com.siyeh.ig.naming;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QuestionableNameInspection extends ClassInspection{
    /** @noinspection PublicField*/
    public String nameCheckString = "foo,bar,baz";
    private final RenameFix fix = new RenameFix();

    private List<Object> nameList = new ArrayList<Object>(32);

    {
        parseNameString();
    }

    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseNameString();
    }

    private void parseNameString(){
        nameList.clear();
        final String[] strings = nameCheckString.split(",");
        for(String string : strings){
            nameList.add(string);
        }
    }

    public void writeSettings(Element element) throws WriteExternalException{
        formatNameCheckString();
        super.writeSettings(element);
    }

    private void formatNameCheckString(){
        final StringBuffer buffer = new StringBuffer();
        boolean first = true;
        for(Object aNameList : nameList){
            if(first){
                first = false;
            } else{
                buffer.append(',');
            }
            final String exceptionName = (String) aNameList;
            buffer.append(exceptionName);
        }
        nameCheckString = buffer.toString();
    }

    public String getDisplayName(){
        return "Questionable name";
    }

    public String getGroupDisplayName(){
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    public JComponent createOptionsPanel(){
        final Form form = new Form();
        return form.getContentPanel();
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "Questionable name '#ref'. #loc ";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new QuestionableNameVisitor();
    }

    private class QuestionableNameVisitor extends BaseInspectionVisitor{
        private boolean inClass = false;


        public void visitVariable(@NotNull PsiVariable variable){
            super.visitVariable(variable);
            final String name = variable.getName();
            if(nameList.contains(name)){
                registerVariableError(variable);
            }
        }

        public void visitMethod(@NotNull PsiMethod method){
            super.visitMethod(method);
            final String name = method.getName();
            if(nameList.contains(name)){
                registerMethodError(method);
            }
        }

        public void visitClass(@NotNull PsiClass aClass){
            if(inClass){
                return;
            }
            final String name = aClass.getName();
            if(nameList.contains(name)){
                registerClassError(aClass);
            }
            final boolean wasInClass = inClass;
            inClass = true;
            super.visitClass(aClass);
            inClass = wasInClass;
        }
    }

    /** @noinspection PublicInnerClass*/
    public class Form{
        private JPanel contentPanel;
        private JButton addButton;
        private JButton deleteButton;
        private JTable table;

        public Form(){
            super();
            table.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.setRowSelectionAllowed(true);
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            table.setEnabled(true);
            final QuestionableNameTableModel model =
            new QuestionableNameTableModel();
            table.setModel(model);
            addButton.setEnabled(true);
            addButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    nameList.add("");
                    model.fireTableStructureChanged();
                }
            });
            deleteButton.setEnabled(true);
            deleteButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e){
                    final int[] selectedRows = table.getSelectedRows();
                    Arrays.sort(selectedRows);
                    for(int i = selectedRows.length - 1; i >= 0; i--){
                        nameList.remove(selectedRows[i]);
                    }
                    model.fireTableStructureChanged();
                }
            });
        }

        public JComponent getContentPanel(){
            return contentPanel;
        }
    }

    private class QuestionableNameTableModel extends AbstractTableModel{
        public int getRowCount(){
            return nameList.size();
        }

        public int getColumnCount(){
            return 1;
        }

        public String getColumnName(int columnIndex){
            return "Name";
        }

        public Class getColumnClass(int columnIndex){
            return String.class;
        }

        public boolean isCellEditable(int rowIndex, int columnIndex){
            return true;
        }

        public Object getValueAt(int rowIndex, int columnIndex){
            return nameList.get(rowIndex);
        }

        public void setValueAt(Object aValue, int rowIndex, int columnIndex){
            nameList.set(rowIndex, aValue);
        }
    }
}
