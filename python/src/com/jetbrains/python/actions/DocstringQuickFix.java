package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
public class DocstringQuickFix implements LocalQuickFix {

  PyParameter myMissing;
  String myMissingText = "";
  String myUnexpected;
  String myPrefix;

  public DocstringQuickFix(PyParameter missing, String unexpected) {
    myMissing = missing;
    if (myMissing != null) {
      if (myMissing.getText().startsWith("*")) {
        myMissingText = myMissing.getText();
      }
      else {
        myMissingText = myMissing.getName();
      }
    }
    myUnexpected = unexpected;
  }

  @NotNull
  public String getName() {
    if (myMissing != null) {
      return PyBundle.message("QFIX.docstring.add.$0", myMissingText);
    }
    else if (myUnexpected != null){
      return PyBundle.message("QFIX.docstring.remove.$0", myUnexpected);
    }
    else  {
      return "insert docstring stub";
    }
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nullable
  private static Editor getEditor(Project project, PsiFile file) {
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document != null) {
      Editor[] editors = EditorFactory.getInstance().getEditors(document);
      if (editors.length > 0)
        return editors[0];
    }
    return null;
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PyDocStringOwner.class);
    if (docStringOwner == null) return;
    PyStringLiteralExpression element = docStringOwner.getDocStringExpression();
    if (element == null) {
      if (docStringOwner instanceof PyFunction) {
        PsiDocumentManager.getInstance(project).getDocument(docStringOwner.getContainingFile());
        PythonDocumentationProvider.inserDocStub((PyFunction)docStringOwner, project, getEditor(project, docStringOwner.getContainingFile()));
      }
      return;
    }
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(element.getProject());
    if (documentationSettings.isEpydocFormat(element.getContainingFile())) {
      myPrefix = "@";
    }
    else {
      myPrefix = ":";
    }

    String replacement = element.getText();
    if (myMissing != null) {
      replacement = createMissingReplacement(element);
    }
    if (myUnexpected != null) {
      replacement = createUnexpectedReplacement(replacement);
    }
    if (!replacement.equals(element.getText())) {
      PyExpression str = elementGenerator.createDocstring(replacement).getExpression();
      element.replace(str);
    }
  }

  private String createUnexpectedReplacement(String text) {
    StringBuilder newText = new StringBuilder();
    String[] lines = LineTokenizer.tokenize(text, true);
    boolean skipNext = false;
    for (String line : lines) {
      if (line.contains(myPrefix)) {
        String[] subLines = line.split(" ");
        boolean lookNext = false;
        boolean add = true;
        for (String s : subLines) {
          if (s.trim().equals(myPrefix + "param")) {
            lookNext = true;
          }
          if (lookNext && s.trim().endsWith(":")) {
            String tmp = s.trim().substring(0, s.trim().length() - 1);
            if (myUnexpected.equals(tmp)) {
              lookNext = false;
              skipNext = true;
              add = false;
            }
          }
        }
        if (add) {
          newText.append(line);
          skipNext = false;
        }
      }
      else if (!skipNext || line.contains("\"\"\"") || line.contains("'''")) {
        newText.append(line);
      }
    }
    return newText.toString();
  }

  private String createMissingReplacement(PsiElement element) {
    String text = element.getText();
    String[] lines = LineTokenizer.tokenize(text, true);
    StringBuilder replacementText = new StringBuilder();
    int ind = lines.length - 1;
    if (lines.length == 1) {
      return createSingleLineReplacement(element);
    }
    for (int i = 0; i != lines.length - 1; ++i) {
      String line = lines[i];
      if (line.contains(myPrefix)) {
        ind = i;
        break;
      }
      replacementText.append(line);
    }
    addParam(replacementText, element, false);
    for (int i = ind; i != lines.length; ++i) {
      String line = lines[i];
      replacementText.append(line);
    }
    return replacementText.toString();
  }

  private void addParam(StringBuilder replacementText, PsiElement element, boolean addWS) {
    PyFunction fun = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    PsiWhiteSpace whitespace = PsiTreeUtil.getPrevSiblingOfType(fun.getStatementList(), PsiWhiteSpace.class);
    String ws = "\n";
    if (whitespace != null) {
      String[] spaces = whitespace.getText().split("\n");
      if (spaces.length > 1) {
        ws = ws + whitespace.getText().split("\n")[1];
      }
    }
    replacementText.deleteCharAt(replacementText.length() - 1);
    replacementText.append(ws);

    String paramText = myMissingText;
    replacementText.append(myPrefix).append("param ").append(paramText).append(": ");
    if (addWS)
      replacementText.append(ws);
    else
      replacementText.append("\n");
  }

  private String createSingleLineReplacement(PsiElement element) {
    String text = element.getText();
    StringBuilder replacementText = new StringBuilder();
    String closingQuotes = "";
    if (text.endsWith("'''") || text.endsWith("\"\"\"")) {
      replacementText.append(text.substring(0, text.length() - 2));
      closingQuotes = text.substring(text.length() - 3);
    }
    else {
      replacementText.append(text.substring(0, text.length()));
      closingQuotes = text.substring(text.length() - 1);
    }
    addParam(replacementText, element, true);
    replacementText.append(closingQuotes);
    return replacementText.toString();
  }
}
