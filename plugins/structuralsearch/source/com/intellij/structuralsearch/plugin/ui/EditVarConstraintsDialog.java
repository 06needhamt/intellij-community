package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.impl.matcher.predicates.ScriptPredicate;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.EditorTextField;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 25, 2004
 * Time: 1:52:18 PM
 * To change this template use File | Settings | File Templates.
 */
class EditVarConstraintsDialog extends DialogWrapper {
  private JTextField maxoccurs;
  private JCheckBox applyWithinTypeHierarchy;
  private JCheckBox notRegexp;
  private EditorTextField regexp;
  private JTextField minoccurs;
  private JPanel mainForm;
  private JCheckBox notWrite;
  private JCheckBox notRead;
  private JCheckBox write;
  private JCheckBox read;
  private JList parameterList;
  private JCheckBox partOfSearchResults;
  private JCheckBox notExprType;
  private EditorTextField regexprForExprType;
  private SearchModel model;
  private JCheckBox exprTypeWithinHierarchy;

  private List<Variable> variables;
  private Variable current;
  private JCheckBox wholeWordsOnly;
  private JCheckBox formalArgTypeWithinHierarchy;
  private JCheckBox invertFormalArgType;
  private EditorTextField formalArgType;
  private TextFieldWithBrowseButton customScriptCode;
  private JCheckBox maxoccursUnlimited;

  private ComboboxWithBrowseButton withinCombo;
  private JPanel containedInConstraints;
  private JCheckBox invertWithinIn;
  private JPanel variableConstraints;
  private JPanel expressionConstraints;
  private JPanel occurencePanel;

  private static Project myProject;

  EditVarConstraintsDialog(final Project project,SearchModel _model,List<Variable> _variables, boolean replaceContext, FileType fileType) {
    super(project,false);

    //regexp.getDocument().addDocumentListener(
    //  new DocumentAdapter() {
    //    public void documentChanged(DocumentEvent event) {
    //      doProcessing(applyWithinTypeHierarchy, regexp);
    //    }
    //  }
    //);

    variables = _variables;
    model = _model;

    setTitle(SSRBundle.message("editvarcontraints.edit.variables"));
    
    regexprForExprType.getDocument().addDocumentListener(
      new DocumentAdapter() {
        public void documentChanged(DocumentEvent event) {
          doProcessing(exprTypeWithinHierarchy, regexprForExprType);
        }
      }
    );
    
    formalArgType.getDocument().addDocumentListener(
      new DocumentAdapter() {
        public void documentChanged(DocumentEvent event) {
          doProcessing(formalArgTypeWithinHierarchy, formalArgType);
        }
      }
    );

    partOfSearchResults.setEnabled(!replaceContext);
    containedInConstraints.setVisible(false);
    withinCombo.getComboBox().setEditable(true);

    customScriptCode.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {

      }
    });

    withinCombo.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final SelectTemplateDialog dialog = new SelectTemplateDialog(project, false, false);
        dialog.show();
        if (dialog.getExitCode() == SelectTemplateDialog.OK_EXIT_CODE) {
          final Configuration[] selectedConfigurations = dialog.getSelectedConfigurations();
          if (selectedConfigurations.length == 1) {
            withinCombo.getComboBox().getEditor().setItem(selectedConfigurations[0].getMatchOptions().getSearchPattern()); // TODO:
          }
        }
      }
    });

    boolean hasContextVar = false;
    for(Variable var:variables) {
      if (Configuration.CONTEXT_VAR_NAME.equals(var.getName())) {
        hasContextVar = true; break;
      }
    }

    if (!hasContextVar) {
      variables.add(new Variable(Configuration.CONTEXT_VAR_NAME, "", "", true));
    }

    if (fileType == StdFileTypes.JAVA) {

      formalArgTypeWithinHierarchy.setEnabled(true);
      invertFormalArgType.setEnabled(true);
      formalArgType.setEnabled(true);

      exprTypeWithinHierarchy.setEnabled(true);
      notExprType.setEnabled(true);
      regexprForExprType.setEnabled(true);

      read.setEnabled(true);
      notRead.setEnabled(true);
      write.setEnabled(true);
      notWrite.setEnabled(true);

      applyWithinTypeHierarchy.setEnabled(true);
    } else {
      formalArgTypeWithinHierarchy.setEnabled(false);
      invertFormalArgType.setEnabled(false);
      formalArgType.setEnabled(false);

      exprTypeWithinHierarchy.setEnabled(false);
      notExprType.setEnabled(false);
      regexprForExprType.setEnabled(false);

      read.setEnabled(false);
      notRead.setEnabled(false);
      write.setEnabled(false);
      notWrite.setEnabled(false);

      applyWithinTypeHierarchy.setEnabled(false);
    }

    parameterList.setModel(
      new AbstractListModel() {
        public Object getElementAt(int index) {
          return variables.get(index);
        }

        public int getSize() {
          return variables.size();
        }
      }
    );

    parameterList.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );

    parameterList.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        boolean rollingBackSelection;

        public void valueChanged(ListSelectionEvent e) {
          if (e.getValueIsAdjusting()) return;
          if (rollingBackSelection) {
            rollingBackSelection=false;
            return;
          }
          final Variable var = variables.get(parameterList.getSelectedIndex());
          if (validateParameters()) {
            if (current!=null) copyValuesFromUI(current);
            ApplicationManager.getApplication().runWriteAction(new Runnable() { public void run() { copyValuesToUI(var); }});
            current = var;
          } else {
            rollingBackSelection = true;
            parameterList.setSelectedIndex(e.getFirstIndex()==parameterList.getSelectedIndex()?e.getLastIndex():e.getFirstIndex());
          }
        }
      }
    );

    parameterList.setCellRenderer(
      new DefaultListCellRenderer() {
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          String name = ((Variable)value).getName();
          if (Configuration.CONTEXT_VAR_NAME.equals(name)) name = SSRBundle.message("complete.match.variable.name");
          return super.getListCellRendererComponent(list, name, index, isSelected, cellHasFocus);
        }
      }
    );

    maxoccursUnlimited.addChangeListener(
      new MyChangeListener(maxoccurs)
    );

    customScriptCode.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final EditScriptDialog wrapper = new EditScriptDialog(project, customScriptCode.getText());
        wrapper.show();
        if (wrapper.getExitCode() == OK_EXIT_CODE) {
          customScriptCode.setText(wrapper.getScriptText());
        }
      }
    });
    init();

    if (variables.size() > 0) parameterList.setSelectedIndex(0);
  }

  private static void doProcessing(JCheckBox checkBox, EditorTextField editor) {
    checkBox.setEnabled( editor.getDocument().getText().length() > 0);
    if (!checkBox.isEnabled()) checkBox.setSelected(false);
  }

  private boolean validateParameters() {
    return validateRegExp(regexp) && validateRegExp(regexprForExprType) &&
           validateIntOccurence(minoccurs) &&
           validateScript(customScriptCode.getTextField()) &&
           (maxoccursUnlimited.isSelected() || validateIntOccurence(maxoccurs));
  }

  protected JComponent createCenterPanel() {
    return mainForm;
  }

  protected void doOKAction() {
    if(validateParameters()) {
      if (current!=null) copyValuesFromUI(current);
      super.doOKAction();
    }
  }

  void copyValuesFromUI(Variable var) {
    MatchVariableConstraint varInfo = (var!=null)?model.getConfig().getMatchOptions().getVariableConstraint(var.getName()):null;

    if (varInfo == null) {
      varInfo = new MatchVariableConstraint();
      varInfo.setName(var.getName());
      model.getConfig().getMatchOptions().addVariableConstraint(varInfo);
    }

    varInfo.setInvertReadAccess(notRead.isSelected());
    varInfo.setReadAccess(read.isSelected());
    varInfo.setInvertWriteAccess(notWrite.isSelected());
    varInfo.setWriteAccess(write.isSelected());
    varInfo.setRegExp(regexp.getDocument().getText());
    varInfo.setInvertRegExp(notRegexp.isSelected());

    int minCount = Integer.parseInt( minoccurs.getText() );
    varInfo.setMinCount(minCount);

    int maxCount;
    if (maxoccursUnlimited.isSelected()) maxCount = Integer.MAX_VALUE;
    else maxCount = Integer.parseInt( maxoccurs.getText() );

    varInfo.setMaxCount(maxCount);
    varInfo.setWithinHierarchy(applyWithinTypeHierarchy.isSelected());
    varInfo.setInvertRegExp(notRegexp.isSelected());

    varInfo.setPartOfSearchResults(partOfSearchResults.isEnabled() && partOfSearchResults.isSelected());

    varInfo.setInvertExprType(notExprType.isSelected());
    varInfo.setNameOfExprType(regexprForExprType.getDocument().getText());
    varInfo.setExprTypeWithinHierarchy(exprTypeWithinHierarchy.isSelected());
    varInfo.setWholeWordsOnly(wholeWordsOnly.isSelected());
    varInfo.setInvertFormalType(invertFormalArgType.isSelected());
    varInfo.setFormalArgTypeWithinHierarchy(formalArgTypeWithinHierarchy.isSelected());
    varInfo.setNameOfFormalArgType(formalArgType.getDocument().getText());
    varInfo.setScriptCodeConstraint("\"" + customScriptCode.getTextField().getText() + "\"");

    final String withinConstraint = (String)withinCombo.getComboBox().getEditor().getItem();
    varInfo.setWithinConstraint(withinConstraint.length() > 0 ? "\"" + withinConstraint +"\"":"");
    varInfo.setInvertWithinConstraint(invertWithinIn.isSelected());
  }

  private void copyValuesToUI(Variable var) {
    MatchVariableConstraint varInfo = (var!=null)?model.getConfig().getMatchOptions().getVariableConstraint(var.getName()):null;

    if (varInfo == null) {
      notRead.setSelected(false);
      notRegexp.setSelected(false);
      read.setSelected(false);
      notWrite.setSelected(false);
      write.setSelected(false);
      regexp.getDocument().setText("");

      minoccurs.setText("1");
      maxoccurs.setText("1");
      maxoccursUnlimited.setSelected(false);
      applyWithinTypeHierarchy.setSelected(false);
      partOfSearchResults.setSelected(false);

      regexprForExprType.getDocument().setText("");
      notExprType.setSelected(false);
      exprTypeWithinHierarchy.setSelected(false);
      wholeWordsOnly.setSelected(false);

      invertFormalArgType.setSelected(false);
      formalArgTypeWithinHierarchy.setSelected(false);
      formalArgType.getDocument().setText("");
      customScriptCode.setText("");

      withinCombo.getComboBox().getEditor().setItem("");
      invertWithinIn.setSelected(false);
    } else {
      notRead.setSelected(varInfo.isInvertReadAccess());
      read.setSelected(varInfo.isReadAccess());
      notWrite.setSelected(varInfo.isInvertWriteAccess());
      write.setSelected(varInfo.isWriteAccess());
      
      applyWithinTypeHierarchy.setSelected(varInfo.isWithinHierarchy());
      regexp.getDocument().setText(varInfo.getRegExp());
      //doProcessing(applyWithinTypeHierarchy,regexp);
      
      notRegexp.setSelected(varInfo.isInvertRegExp());
      minoccurs.setText(Integer.toString(varInfo.getMinCount()));

      if(varInfo.getMaxCount() == Integer.MAX_VALUE) {
        maxoccursUnlimited.setSelected(true);
        maxoccurs.setText("");
      } else {
        maxoccursUnlimited.setSelected(false);
        maxoccurs.setText(Integer.toString(varInfo.getMaxCount()));
      }

      partOfSearchResults.setSelected( partOfSearchResults.isEnabled() && varInfo.isPartOfSearchResults() );

      exprTypeWithinHierarchy.setSelected( varInfo.isExprTypeWithinHierarchy() );
      regexprForExprType.getDocument().setText( varInfo.getNameOfExprType() );
      doProcessing(exprTypeWithinHierarchy, regexprForExprType);
      
      notExprType.setSelected( varInfo.isInvertExprType() );
      wholeWordsOnly.setSelected( varInfo.isWholeWordsOnly() );

      invertFormalArgType.setSelected( varInfo.isInvertFormalType() );
      formalArgTypeWithinHierarchy.setSelected( varInfo.isFormalArgTypeWithinHierarchy() );
      formalArgType.getDocument().setText( varInfo.getNameOfFormalArgType() );
      doProcessing(formalArgTypeWithinHierarchy,formalArgType);
      customScriptCode.setText( StringUtil.stripQuotesAroundValue(varInfo.getScriptCodeConstraint()) );

      withinCombo.getComboBox().getEditor().setItem(StringUtil.stripQuotesAroundValue(varInfo.getWithinConstraint()));
      invertWithinIn.setSelected(varInfo.isInvertWithinConstraint());
    }

    boolean isExprContext = true;
    final boolean contextVar = Configuration.CONTEXT_VAR_NAME.equals(var.getName());
    if (contextVar) isExprContext = false;
    containedInConstraints.setVisible(contextVar);
    expressionConstraints.setVisible(isExprContext);
    partOfSearchResults.setEnabled(!contextVar); //?

    occurencePanel.setVisible(!contextVar);
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.ui.EditVarConstraintsDialog";
  }

  private static boolean validateRegExp(EditorTextField field) {
    try {
      final String s = field.getDocument().getText();
      if (s.length() > 0) {
        Pattern.compile(s);
      }
    } catch(PatternSyntaxException ex) {
      Messages.showErrorDialog(SSRBundle.message("invalid.regular.expression"), SSRBundle.message("invalid.regular.expression"));
      field.requestFocus();
      return false;
    }
    return true;
  }

  private static boolean validateScript(JTextField field) {
    final String text = field.getText();

    if (text.length() > 0) {
      final String s = ScriptPredicate.checkValidScript(text);

      if (s != null) {
        Messages.showErrorDialog(SSRBundle.message("invalid.groovy.script"), SSRBundle.message("invalid.groovy.script"));
        field.requestFocus();
        return false;
      }
    }
    return true;
  }

  private static boolean validateIntOccurence(JTextField field) {
    try {
      int a = Integer.parseInt(field.getText());
      if (a==-1) throw new NumberFormatException();
    } catch(NumberFormatException ex) {
      Messages.showErrorDialog(SSRBundle.message("invalid.occurence.count"), SSRBundle.message("invalid.occurence.count"));
      field.requestFocus();
      return false;
    }
    return true;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.dialogs.search.replace.structural.editvariable");
  }

  private void createUIComponents() {
    regexp = createComponent();
    regexprForExprType = createComponent();
    formalArgType = createComponent();
  }

  private static EditorTextField createComponent() {
    final String fileName = "1.regexp";
    FileType fileType = getFileType(fileName);
    Document doc = createDocument(fileName, fileType, "");
    return new EditorTextField(doc, myProject, fileType);
  }

  private static Document createDocument(final String fileName, final FileType fileType, String text) {
    final PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(fileName, fileType, text, -1, true);

    return PsiDocumentManager.getInstance(myProject).getDocument(file);
  }

  private static FileType getFileType(final String fileName) {
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    if (fileType == FileTypes.UNKNOWN) fileType = StdFileTypes.PLAIN_TEXT;
    return fileType;
  }

  public static void setProject(final Project project) {
    myProject = project;
  }

  private class MyChangeListener implements ChangeListener {
    JTextField textField;

    MyChangeListener(JTextField _minoccurs) {
      textField = _minoccurs;
    }

    public void stateChanged(ChangeEvent e) {
      final JCheckBox jCheckBox = (JCheckBox)e.getSource();

      if (jCheckBox.isSelected()) {
        textField.setEnabled(false);
      }
      else {
        textField.setEnabled(true);
      }
    }
  }

  private static Editor createEditor(final Project project, final String text, final String fileName) {
    final FileType fileType = getFileType(fileName);
    final Document doc = createDocument(fileName, fileType, text);
    final Editor editor = EditorFactory.getInstance().createEditor(doc, project);

    ((EditorEx)editor).setEmbeddedIntoDialogWrapper(true);
    final EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(false);
    settings.setFoldingOutlineShown(false);
    settings.setRightMarginShown(false);
    settings.setLineMarkerAreaShown(false);
    ((EditorEx)editor).setHighlighter(HighlighterFactory.createHighlighter(fileType, DefaultColorSchemesManager.getInstance().getAllSchemes()[0], project));

    return editor;
  }

  private static class EditScriptDialog extends DialogWrapper {
    private Editor editor;

    public EditScriptDialog(final Project project, String text) {
      super(project, true);
      setTitle("Edit Groovy Script Constraint");

      editor = createEditor(project, text, "1.groovy");

      init();
      setSize(300, 300);
    }

    @Override
    protected String getDimensionServiceKey() {
      return getClass().getName();
    }

    protected JComponent createCenterPanel() {
      return editor.getComponent();
    }

    String getScriptText() {
      return editor.getDocument().getText();
    }

    @Override
    protected void dispose() {
      EditorFactory.getInstance().releaseEditor(editor);
      super.dispose();
    }
  }
}
