package com.jetbrains.python.actions;

import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Turns an unqualified unresolved identifier into qualified and resolvable.
 *
 * @author dcheryasov
 */
public class ImportFromExistingAction implements QuestionAction {
  PyElement myTarget;
  List<ImportCandidateHolder> mySources; // list of <import, imported_item>
  String myName;
  boolean myUseQualifiedImport;
  private Runnable myOnDoneCallback;

  /**
   * @param target element to become qualified as imported.
   * @param sources clauses of import to be used.
   * @param name relevant name ot the target element (e.g. of identifier in an expression).
   * @param useQualified if True, use qualified "import modulename" instead of "from modulename import ...".
   */
  public ImportFromExistingAction(@NotNull PyElement target, @NotNull List<ImportCandidateHolder> sources, String name,
                                  boolean useQualified) {
    myTarget = target;
    mySources = sources;
    myName = name;
    myUseQualifiedImport = useQualified;
  }

  public void onDone(Runnable callback) {
    assert myOnDoneCallback == null;
    myOnDoneCallback = callback;
  }


  /**
   * Alters either target (by qualifying a name) or source (by explicitly importing the name).
   * @return true if action succeeded
   */
  public boolean execute() {
    // check if the tree is sane
    PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
    if (!myTarget.isValid()) return false;
    if ((myTarget instanceof PyQualifiedExpression) && ((((PyQualifiedExpression)myTarget).getQualifier() != null))) return false; // we cannot be qualified
    for (ImportCandidateHolder item : mySources) {
      if (!item.getImportable().isValid()) return false;
      if (!item.getFile().isValid()) return false;
      if (item.getImportElement() != null && !item.getImportElement().isValid()) return false;
    }
    if (mySources.isEmpty()) {
      return false;
    }
    // act
    if (mySources.size() > 1) {
      selectSourceAndDo();
    }
    else doWriteAction(mySources.get(0));
    return true;
  }

  private void selectSourceAndDo() {
    // GUI part
    ImportCandidateHolder[] items = mySources.toArray(new ImportCandidateHolder[mySources.size()]); // silly JList can't handle modern collections
    final JList list = new JBList(items);
    list.setCellRenderer(new CellRenderer(myName));

    final Runnable runnable = new Runnable() {
      public void run() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        PsiDocumentManager.getInstance(myTarget.getProject()).commitAllDocuments();
        doWriteAction(mySources.get(index));
      }
    };

    DataManager.getInstance().getDataContextFromFocus().doWhenDone(new AsyncResult.Handler<DataContext>() {
      @Override
      public void run(DataContext dataContext) {
        new PopupChooserBuilder(list).
          setTitle(myUseQualifiedImport? PyBundle.message("ACT.qualify.with.module") : PyBundle.message("ACT.from.some.module.import")).
          setItemChoosenCallback(runnable).
          createPopup().
          showInBestPositionFor(dataContext);
      }
    });
  }

  private void doIt(final ImportCandidateHolder item) {
    PyImportElement src = item.getImportElement();
    final Project project = myTarget.getProject();
    final PyElementGenerator gen = PyElementGenerator.getInstance(project);
    if (src != null) { // use existing import
      // did user choose 'import' or 'from import'?
      PsiElement parent = src.getParent();
      if (parent instanceof PyFromImportStatement) {
        // add another import element right after the one we got
        PsiElement new_elt = gen.createImportElement(myName);
        PyUtil.addListNode(parent, new_elt, null, false, true);
      }
      else { // just 'import'
        // all we need is to qualify our target
        myTarget.replace(gen.createExpressionFromText(src.getVisibleName() + "." + myName));
      }
    }
    else { // no existing import, add it then use it
      if (myUseQualifiedImport) {
        AddImportHelper.addImportStatement(myTarget.getContainingFile(), item.getPath(), null);
        String qual_name;
        if (item.getAsName() != null) qual_name = item.getAsName();
        else qual_name = item.getPath();
        myTarget.replace(gen.createExpressionFromText(qual_name + "." + myName));
      }
      else {
        AddImportHelper.addImportFromStatement(myTarget.getContainingFile(), item.getPath(), myName, null);
      }
    }
  }

  private void doWriteAction(final ImportCandidateHolder item) {
    PsiElement src = item.getImportable();
    new WriteCommandAction(src.getProject(), PyBundle.message("ACT.CMD.use.import"), myTarget.getContainingFile()) {
      @Override
      protected void run(Result result) throws Throwable {
        doIt(item);
      }
    }.execute();
    if (myOnDoneCallback != null) {
      myOnDoneCallback.run();
    }
  }

  // Stolen from FQNameCellRenderer
  private static class CellRenderer extends SimpleColoredComponent implements ListCellRenderer {
    private final Font FONT;
    private final String myName;

    public CellRenderer(String name) {
      myName = name;
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      FONT = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
      setOpaque(true);
    }

    // value is a QualifiedHolder
    public Component getListCellRendererComponent(
      JList list,
      Object value, // expected to be
      int index,
      boolean isSelected,
      boolean cellHasFocus
    ){

      clear();

      ImportCandidateHolder item = (ImportCandidateHolder)value;
      setIcon(item.getImportable().getIcon(0));
      String item_name = item.getPresentableText(myName);
      append(item_name, SimpleTextAttributes.REGULAR_ATTRIBUTES);

      String tailText = item.getTailText();
      if (tailText != null) {
        append(" " + tailText, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }

      setFont(FONT);
      if (isSelected) {
        setBackground(list.getSelectionBackground());
        setForeground(list.getSelectionForeground());
      }
      else {
        setBackground(list.getBackground());
        setForeground(list.getForeground());
      }
      return this;
    }
  }
}
