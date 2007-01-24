package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.DefaultCharFilter;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.*;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.IntArrayList;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class TemplateState implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.impl.TemplateState");
  private Project myProject;
  private Editor myEditor;

  private TemplateImpl myTemplate;
  private TemplateSegments mySegments = null;

  private RangeMarker myTemplateRange = null;
  private ArrayList<RangeHighlighter> myTabStopHighlighters = new ArrayList<RangeHighlighter>();
  private int myCurrentVariableNumber = -1;
  private int myCurrentSegmentNumber = -1;
  private boolean toProcessTab = true;

  private boolean myDocumentChangesTerminateTemplate = true;
  private boolean myDocumentChanged = false;

  private CommandAdapter myCommandListener;

  private List<TemplateEditingListener> myListeners = new ArrayList<TemplateEditingListener>();
  private DocumentAdapter myEditorDocumentListener;
  private Map myProperties = new HashMap();
  private boolean myTemplateIndented = false;
  private Document myDocument;

  public TemplateState(Project project, final Editor editor) {
    myProject = project;
    myEditor = editor;
    myDocument = myEditor.getDocument();
  }

  private void initListeners() {
    myEditorDocumentListener = new DocumentAdapter() {
      public void beforeDocumentChange(DocumentEvent e) {
        myDocumentChanged = true;
      }
    };

    myCommandListener = new CommandAdapter() {
      public void commandStarted(CommandEvent event) {
        if (myEditor != null) {
          final int offset = myEditor.getCaretModel().getOffset();
          myDocumentChangesTerminateTemplate = offset < mySegments.getSegmentStart(myCurrentSegmentNumber) ||
                                   offset > mySegments.getSegmentEnd(myCurrentSegmentNumber);
        }
      }

      public void beforeCommandFinished(CommandEvent event) {
        afterChangedUpdate();
      }
    };

    myDocument.addDocumentListener(myEditorDocumentListener);
    CommandProcessor.getInstance().addCommandListener(myCommandListener);
  }

  public synchronized void dispose() {
    if (myEditorDocumentListener != null) {
      myDocument.removeDocumentListener(myEditorDocumentListener);
      myEditorDocumentListener = null;
    }
    if (myCommandListener != null) {
      CommandProcessor.getInstance().removeCommandListener(myCommandListener);
      myCommandListener = null;
    }

    //Avoid the leak of the editor
    releaseEditor();
    myDocument = null;
  }

  public boolean isToProcessTab() {
    return toProcessTab;
  }

  private void setCurrentVariableNumber(int variableNumber) {
    myCurrentVariableNumber = variableNumber;
    ((DocumentEx)myDocument).setStripTrailingSpacesEnabled(variableNumber < 0);
    if (variableNumber < 0) {
      myCurrentSegmentNumber = -1;
      releaseAll();
    } else {
      myCurrentSegmentNumber = getCurrentSegmentNumber();
    }
  }

  public TextResult getVariableValue(String variableName) {
    if (variableName.equals(TemplateImpl.SELECTION)) {
      return new TextResult((String)getProperties().get(ExpressionContext.SELECTION));
    }
    if (variableName.equals(TemplateImpl.END)) {
      return new TextResult("");
    }

    CharSequence text = myDocument.getCharsSequence();
    int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
    if (segmentNumber < 0) {
      return null;
    }
    int start = mySegments.getSegmentStart(segmentNumber);
    int end = mySegments.getSegmentEnd(segmentNumber);
    int length = myDocument.getTextLength();
    if (start > length || end > length) {
      return null;
    }
    return new TextResult(text.subSequence(start, end).toString());
  }

  public TextRange getCurrentVariableRange() {
    int number = getCurrentSegmentNumber();
    if (number == -1) return null;
    return new TextRange(mySegments.getSegmentStart(number), mySegments.getSegmentEnd(number));
  }

  public TextRange getVariableRange(String variableName) {
    int segment = myTemplate.getVariableSegmentNumber(variableName);
    if (segment < 0) return null;

    return new TextRange(mySegments.getSegmentStart(segment), mySegments.getSegmentEnd(segment));
  }

  public boolean isFinished() {
    return (myCurrentVariableNumber < 0);
  }

  private void releaseAll() {
    if (mySegments != null) {
      mySegments.removeAll();
      mySegments = null;
    }
    myTemplateRange = null;
    myTemplate = null;
    releaseEditor();
    myTabStopHighlighters.clear();
  }

  private void releaseEditor() {
    if (myEditor != null) {
      for (RangeHighlighter segmentHighlighter : myTabStopHighlighters) {
        myEditor.getMarkupModel().removeHighlighter(segmentHighlighter);
      }

      myEditor = null;
    }
  }

  public void start(TemplateImpl template) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    UndoManager.getInstance(myProject).undoableActionPerformed(
      new UndoableAction() {
        public void undo() throws UnexpectedUndoException {
          if (myDocument != null) {
            fireTemplateCancelled();
            //hack to close lookup if any: TODO lookup API for closing active lookup
            final int segmentNumber = getCurrentSegmentNumber();
            if (segmentNumber >= 0) {
              int offsetToMove = myTemplate.getSegmentOffset(segmentNumber) - 1;
              if (offsetToMove < 0) offsetToMove = myDocument.getTextLength();
              final int oldOffset = myEditor.getCaretModel().getOffset();
              myEditor.getCaretModel().moveToOffset(offsetToMove);
              myEditor.getCaretModel().moveToOffset(oldOffset);
            }
            setCurrentVariableNumber(-1);
          }
        }

        public void redo() throws UnexpectedUndoException {
          //TODO:
          // throw new UnexpectedUndoException("Not implemented");
        }

        public DocumentReference[] getAffectedDocuments() {
          if (myDocument == null) return new DocumentReference[0];
          return new DocumentReference[]{DocumentReferenceByDocument.createDocumentReference(myDocument)};
        }

        public boolean isComplex() {
          return false;
        }
      }
    );
    myTemplateIndented = false;
    myCurrentVariableNumber = -1;
    mySegments = new TemplateSegments(myEditor);
    myTemplate = template;


    if (template.isInline()) {
      int caretOffset = myEditor.getCaretModel().getOffset();
      myTemplateRange = myDocument.createRangeMarker(caretOffset, caretOffset + template.getTemplateText().length());
    }
    else {
      preprocessTemplate(PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument), myEditor.getCaretModel().getOffset(), myTemplate.getTemplateText());
      int caretOffset = myEditor.getCaretModel().getOffset();
      myTemplateRange = myDocument.createRangeMarker(caretOffset, caretOffset);
    }
    myTemplateRange.setGreedyToLeft(true);
    myTemplateRange.setGreedyToRight(true);

    processAllExpressions(template);
  }

  private void fireTemplateCancelled() {
    TemplateEditingListener[] listeners = myListeners.toArray(new TemplateEditingListener[myListeners.size()]);
    for (TemplateEditingListener listener : listeners) {
      listener.templateCancelled(myTemplate);
    }
  }

  private void preprocessTemplate(final PsiFile file, int caretOffset, final String textToInsert) {
    if (file.getLanguage().equals(StdLanguages.JSPX)) {
      if (XmlUtil.toCode(textToInsert)) {
        try {
          caretOffset += JspSpiUtil.escapeCharsInJspContext((JspFile)file, caretOffset, myTemplate.getTemplateText());
          PostprocessReformattingAspect.getInstance(myProject).doPostponedFormatting();
          myEditor.getCaretModel().moveToOffset(caretOffset);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  private void processAllExpressions(final TemplateImpl template) {
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          if (!template.isInline()) myDocument.insertString(myTemplateRange.getStartOffset(), template.getTemplateText());
          for (int i = 0; i < template.getSegmentsCount(); i++) {
            int segmentOffset = myTemplateRange.getStartOffset() + template.getSegmentOffset(i);
            mySegments.addSegment(segmentOffset, segmentOffset);
          }

          calcResults(false);
          calcResults(false);  //Fixed SCR #[vk500] : all variables should be recalced twice on start.
          doReformat();

          int nextVariableNumber = getNextVariableNumber(-1);
          if (nextVariableNumber == -1) {
            finishTemplateEditing();
          }
          else {
            setCurrentVariableNumber(nextVariableNumber);
            initTabStopHighlighters();
            initListeners();
            focusCurrentExpression();
          }
        }
      }
    );
  }

  private void doReformat() {
    final Runnable action = new Runnable() {
      public void run() {
        IntArrayList indices = initEmptyVariables();
        mySegments.setSegmentsGreedy(false);
        reformat();
        mySegments.setSegmentsGreedy(true);
        restoreEmptyVariables(indices);
      }
    };
    ApplicationManager.getApplication().runWriteAction(action);
  }

  private void shortenReferences() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
        if (file != null) {
          CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
          if (myTemplate.isToShortenLongNames()) {
            try {
              IntArrayList indices = initEmptyVariables();
              mySegments.setSegmentsGreedy(false);

              PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
              codeStyleManager.shortenClassReferences(file, myTemplateRange.getStartOffset(), myTemplateRange.getEndOffset());
              unblockDocument();

              mySegments.setSegmentsGreedy(true);
              restoreEmptyVariables(indices);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    });
  }

  private void afterChangedUpdate() {
    if (isFinished()) return;
    LOG.assertTrue(myTemplate != null);
    if (myDocumentChanged) {
      if (myDocumentChangesTerminateTemplate || mySegments.isInvalid()) {
        setCurrentVariableNumber(-1);
        fireTemplateCancelled();
      } else {
        calcResults(true);
      }
    }
  }

  private String getExpressionString(int index) {
    CharSequence text = myDocument.getCharsSequence();

    if (!mySegments.isValid(index)) return "";

    int start = mySegments.getSegmentStart(index);
    int end = mySegments.getSegmentEnd(index);

    return text.subSequence(start, end).toString();
  }

  private int getCurrentSegmentNumber() {
    if (myCurrentVariableNumber == -1) {
      return -1;
    }
    String variableName = myTemplate.getVariableNameAt(myCurrentVariableNumber);
    return myTemplate.getVariableSegmentNumber(variableName);
  }

  private void focusCurrentExpression() {
    if (isFinished()) {
      return;
    }

    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);

    final int currentSegmentNumber = getCurrentSegmentNumber();
    if (currentSegmentNumber < 0) return;
    final int start = mySegments.getSegmentStart(currentSegmentNumber);
    final int end = mySegments.getSegmentEnd(currentSegmentNumber);
    myEditor.getCaretModel().moveToOffset(end);
    myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    myEditor.getSelectionModel().removeSelection();


    myEditor.getSelectionModel().setSelection(start, end);
    Expression expressionNode = myTemplate.getExpressionAt(myCurrentVariableNumber);

    final ExpressionContext context = createExpressionContext(start);
    final LookupItem[] lookupItems = expressionNode.calculateLookupItems(context);
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    if (lookupItems != null && lookupItems.length > 0) {
      final LookupItemPreferencePolicy preferencePolicy = new LookupItemPreferencePolicy() {
        public int compare(LookupItem i1, LookupItem i2) {
          if (i1.equals(i2)) return 0;
          if (i1.equals(lookupItems[0])) return -1;
          if (i2.equals(lookupItems[0])) return +1;
          return 0;
        }

        public void setPrefix(String prefix) {
        }

        public void itemSelected(LookupItem item) {
        }
      };

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myEditor == null) return;

          final LookupManager lookupManager = LookupManager.getInstance(myProject);
          if (lookupManager.isDisposed()) return;
          final Lookup lookup = lookupManager.showLookup(myEditor, lookupItems, "", preferencePolicy,
                                                         new DefaultCharFilter(psiFile, end));
          lookup
            .setCurrentItem(
              lookupItems[0]); // [Valentin] not absolutely correct but all existing macros return the first item as the result
          toProcessTab = false;
          lookup.addLookupListener(
            new LookupAdapter() {
              public void lookupCanceled(LookupEvent event) {
                lookup.removeLookupListener(this);
                toProcessTab = true;
              }

              public void itemSelected(LookupEvent event) {
                lookup.removeLookupListener(this);
                if (isFinished()) return;
                toProcessTab = true;

                final LookupItem item = event.getItem();

                if (item != null) {
                  PsiDocumentManager.getInstance(myProject).commitAllDocuments();

                  Integer bracketCount = (Integer)item.getAttribute(LookupItem.BRACKETS_COUNT_ATTR);
                  if (bracketCount != null) {
                    StringBuffer tail = new StringBuffer();
                    for (int i = 0; i < bracketCount.intValue(); i++) {
                      tail.append("[]");
                    }
                    EditorModificationUtil.insertStringAtCaret(myEditor, tail.toString());
                    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
                  }

                  updateTypeBindings(item.getObject(), psiFile, currentSegmentNumber);

                  char c = event.getCompletionChar();
                  if (c == '.') {
                    EditorModificationUtil.insertStringAtCaret(myEditor, ".");
                    AutoPopupController.getInstance(myProject).autoPopupMemberLookup(myEditor);
                    return;
                  }

                  if (item.getAttribute(Expression.AUTO_POPUP_NEXT_LOOKUP) != null) {
                    AutoPopupController.getInstance(myProject).autoPopupMemberLookup(myEditor);
                    return;
                  }

                  if (!isFinished()) {
                    calcResults(true);
                  }
                }

                nextTab();
              }
            }
          );
        }
      });
    }
    else {
      Result result = expressionNode.calculateResult(context);
      if (result instanceof PsiElementResult) {
        updateTypeBindings(((PsiElementResult)result).getElement(), psiFile, currentSegmentNumber);
      }
      if (result instanceof PsiTypeResult) {
        updateTypeBindings(((PsiTypeResult)result).getType(), psiFile, currentSegmentNumber);
      }
      if (result instanceof InvokeActionResult) {
        ((InvokeActionResult)result).getAction().run();
      }
    }
    focusCurrentHighlighter(true);
  }


  private void updateTypeBindings(Object item, PsiFile file, final int segmentNumber) {
    PsiClass aClass = null;
    if (item instanceof PsiClass) {
      aClass = (PsiClass)item;
    }
    else if (item instanceof PsiType) {
      aClass = PsiUtil.resolveClassInType(((PsiType)item));
    }

    if (aClass != null) {
      if (aClass instanceof PsiTypeParameter) {
        if (((PsiTypeParameter)aClass).getOwner() instanceof PsiMethod) {
          PsiElement element = file.findElementAt(mySegments.getSegmentStart(segmentNumber));
          PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
          if (method != null) {
            PsiTypeParameterList paramList = method.getTypeParameterList();
            PsiTypeParameter[] params = paramList.getTypeParameters();
            for (PsiTypeParameter param : params) {
              if (param.getName().equals(aClass.getName())) return;
            }
            try {
              paramList.add(aClass.copy());
              unblockDocument();
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }  else {
        addImportForClass(aClass, segmentNumber);
        unblockDocument();
      }
    }
  }

  private void unblockDocument() {
    PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myDocument);
  }

  private void addImportForClass(final PsiClass aClass, int segmentNumber) {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    if (!aClass.isValid() || aClass.getQualifiedName() == null) return;

    PsiManager manager = PsiManager.getInstance(myProject);
    PsiResolveHelper helper = manager.getResolveHelper();

    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    CharSequence chars = myDocument.getCharsSequence();

    final int start = mySegments.getSegmentStart(segmentNumber);
    final int end = mySegments.getSegmentEnd(segmentNumber);
    PsiElement element = file.findElementAt(start);
    String refText = chars.subSequence(start, end).toString();
    PsiClass refClass = helper.resolveReferencedClass(refText, element);
    if (aClass.equals(refClass)) return;

    if (element instanceof PsiIdentifier) {
      PsiElement parent = element.getParent();
      while (parent != null) {
        PsiElement tmp = parent.getParent();
        if (!(tmp instanceof PsiJavaCodeReferenceElement) || tmp.getTextRange().getEndOffset() > end) break;
        parent = tmp;
      }
      if (parent instanceof PsiJavaCodeReferenceElement && !((PsiJavaCodeReferenceElement) parent).isQualified()) {
        final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement) parent;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              ref.bindToElement(aClass);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }
  }

  private void calcResults(final boolean isQuick) {
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          BitSet calcedSegments = new BitSet();

          do {
            calcedSegments.clear();
            for (int i = myCurrentVariableNumber + 1; i < myTemplate.getVariableCount(); i++) {
              String variableName = myTemplate.getVariableNameAt(i);
              int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
              if (segmentNumber < 0) continue;
              Expression expression = myTemplate.getExpressionAt(i);
              Expression defaultValue = myTemplate.getDefaultValueAt(i);
              String oldValue = getVariableValue(variableName).getText();
              recalcSegment(segmentNumber, isQuick, expression, defaultValue);
              String newValue = getVariableValue(variableName).getText();
              if (!newValue.equals(oldValue)) {
                calcedSegments.set(segmentNumber);
              }
            }

            for (int i = 0; i < myTemplate.getSegmentsCount(); i++) {
              if (!calcedSegments.get(i)) {
                String variableName = myTemplate.getSegmentName(i);
                String newValue = getVariableValue(variableName).getText();
                int start = mySegments.getSegmentStart(i);
                int end = mySegments.getSegmentEnd(i);
                replaceString(newValue, start, end, i);
              }
            }
          }
          while (!calcedSegments.isEmpty());
        }
      }
    );
  }

  private void recalcSegment(int segmentNumber, boolean isQuick, Expression expressionNode, Expression defaultValue) {
    String oldValue = getExpressionString(segmentNumber);
    int start = mySegments.getSegmentStart(segmentNumber);
    int end = mySegments.getSegmentEnd(segmentNumber);
    ExpressionContext context = createExpressionContext(start);
    Result result;
    if (isQuick) {
      result = expressionNode.calculateQuickResult(context);
    }
    else {
      result = expressionNode.calculateResult(context);
      if (expressionNode instanceof ConstantNode) {
        if (result instanceof TextResult) {
          TextResult text = (TextResult)result;
          if (text.getText().equals("") && defaultValue != null) {
            result = defaultValue.calculateResult(context);
          }
        }
      }
      if (result == null && defaultValue != null) {
        result = defaultValue.calculateResult(context);
      }
    }
    if (result == null) return;

    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    PsiElement element = psiFile.findElementAt(start);
    if (result.equalsToText(oldValue, element)) return;

    String newValue = result.toString();
    if (newValue == null) newValue = "";

    if (element instanceof PsiJavaToken && ((PsiJavaToken)element).getTokenType() == JavaTokenType.STRING_LITERAL) {
      newValue = StringUtil.escapeStringCharacters(newValue);
    }

    replaceString(newValue, start, end, segmentNumber);

    if (result instanceof PsiTypeResult) {
      shortenReferences();
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
      updateTypeBindings(((PsiTypeResult)result).getType(), psiFile, segmentNumber);
    }
  }

  private void replaceString(String newValue, int start, int end, int segmentNumber) {
    String oldText = myDocument.getCharsSequence().subSequence(start, end).toString();
    if (!oldText.equals(newValue)) {
      mySegments.setNeighboursGreedy(segmentNumber, false);
      myDocument.replaceString(start, end, newValue);
      mySegments.replaceSegmentAt(segmentNumber, start, start + newValue.length());
      mySegments.setNeighboursGreedy(segmentNumber, true);
    }
  }

  public void previousTab() {
    if (isFinished()) {
      return;
    }
    int previousVariableNumber = getPreviousVariableNumber(myCurrentVariableNumber);
    if (previousVariableNumber >= 0) {
      focusCurrentHighlighter(false);
      calcResults(false);
      doReformat();
      setCurrentVariableNumber(previousVariableNumber);
      focusCurrentExpression();
    }
  }

  public void nextTab() {
    if (isFinished()) {
      return;
    }

    //some psi operations may block the document, unblock here
    unblockDocument();

    int nextVariableNumber = getNextVariableNumber(myCurrentVariableNumber);
    if (nextVariableNumber == -1) {
      calcResults(false);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          reformat();
        }
      });
      finishTemplateEditing();
      return;
    }
    focusCurrentHighlighter(false);
    calcResults(false);
    doReformat();
    setCurrentVariableNumber(nextVariableNumber);
    focusCurrentExpression();
  }

  private ExpressionContext createExpressionContext(final int start) {
    return new ExpressionContext() {
      public Project getProject() {
        return myProject;
      }

      public Editor getEditor() {
        return myEditor;
      }

      public int getStartOffset() {
        return start;
      }

      public int getTemplateStartOffset() {
        if (myTemplateRange == null) {
          return -1;
        }
        return myTemplateRange.getStartOffset();
      }

      public int getTemplateEndOffset() {
        if (myTemplateRange == null) {
          return -1;
        }
        return myTemplateRange.getEndOffset();
      }

      public Map getProperties() {
        return myProperties;
      }
    };
  }

  public void gotoEnd() {
    calcResults(false);
    doReformat();
    finishTemplateEditing();
  }

  private void finishTemplateEditing() {
    if (myTemplate == null) return;
    int endSegmentNumber = myTemplate.getEndSegmentNumber();
    int offset = -1;
    if (endSegmentNumber >= 0) {
      offset = mySegments.getSegmentStart(endSegmentNumber);
    } else {
      if (!myTemplate.isSelectionTemplate() && !myTemplate.isInline()) { //do not move caret to the end of range for selection templates
        offset = myTemplateRange.getEndOffset();
      }
    }

    if (offset >= 0) {
      myEditor.getCaretModel().moveToOffset(offset);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }

    myEditor.getSelectionModel().removeSelection();
    int selStart = myTemplate.getSelectionStartSegmentNumber();
    int selEnd = myTemplate.getSelectionEndSegmentNumber();
    if (selStart >= 0 && selEnd >= 0) {
      myEditor.getSelectionModel().setSelection(
        mySegments.getSegmentStart(selStart),
        mySegments.getSegmentStart(selEnd)
      );
    }

    fireTemplateFinished();
    myListeners.clear();
    setCurrentVariableNumber(-1);
    myProject = null;
  }

  private int getNextVariableNumber(int currentVariableNumber) {
    for (int i = currentVariableNumber + 1; i < myTemplate.getVariableCount(); i++) {
      if (checkIfTabStop(i)) {
        return i;
      }
    }
    return -1;
  }

  private int getPreviousVariableNumber(int currentVariableNumber) {
    for (int i = currentVariableNumber - 1; i >= 0; i--) {
      if (checkIfTabStop(i)) {
        return i;
      }
    }
    return -1;
  }

  private boolean checkIfTabStop(int currentVariableNumber) {
    Expression expression = myTemplate.getExpressionAt(currentVariableNumber);
    if (expression == null) {
      return false;
    }
    if (myTemplate.isAlwaysStopAt(currentVariableNumber)) {
      return true;
    }
    String variableName = myTemplate.getVariableNameAt(currentVariableNumber);
    int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
    if (segmentNumber <= 0) return false;
    int start = mySegments.getSegmentStart(segmentNumber);
    ExpressionContext context = createExpressionContext(start);
    Result result = expression.calculateResult(context);
    if (result == null) {
      return true;
    }
    LookupItem[] items = expression.calculateLookupItems(context);
    return items != null && items.length > 1;
  }

  private IntArrayList initEmptyVariables() {
    int endSegmentNumber = myTemplate.getEndSegmentNumber();
    int selStart = myTemplate.getSelectionStartSegmentNumber();
    int selEnd = myTemplate.getSelectionEndSegmentNumber();
    IntArrayList indices = new IntArrayList();
    for (int i = 0; i < myTemplate.getSegmentsCount(); i++) {
      int length = mySegments.getSegmentEnd(i) - mySegments.getSegmentStart(i);
      if (length != 0) continue;
      if (i == endSegmentNumber || i == selStart || i == selEnd) continue;

      String name = myTemplate.getSegmentName(i);
      for (int j = 0; j < myTemplate.getVariableCount(); j++) {
        if (myTemplate.getVariableNameAt(j).equals(name)) {
          Expression e = myTemplate.getExpressionAt(j);
          @NonNls String marker = "a";
          if (e instanceof MacroCallNode) {
            marker = ((MacroCallNode)e).getMacro().getDefaultValue();
          }
          int start = mySegments.getSegmentStart(i);
          int end = start + marker.length();
          myDocument.insertString(start, marker);
          mySegments.replaceSegmentAt(i, start, end);
          indices.add(i);
          break;
        }
      }
    }
    return indices;
  }

  private void restoreEmptyVariables(IntArrayList indices) {
    for (int i = 0; i < indices.size(); i++) {
      int index = indices.get(i);
      myDocument.deleteString(mySegments.getSegmentStart(index), mySegments.getSegmentEnd(index));
    }
  }

  private void initTabStopHighlighters() {
    for (int i = 0; i < myTemplate.getVariableCount(); i++) {
      String variableName = myTemplate.getVariableNameAt(i);
      int segmentNumber = myTemplate.getVariableSegmentNumber(variableName);
      if (segmentNumber < 0) continue;
      RangeHighlighter segmentHighlighter = getSegmentHighlighter(segmentNumber, false, false);
      myTabStopHighlighters.add(segmentHighlighter);
    }

    int endSegmentNumber = myTemplate.getEndSegmentNumber();
    if (endSegmentNumber >= 0) {
      RangeHighlighter segmentHighlighter = getSegmentHighlighter(endSegmentNumber, false, true);
      myTabStopHighlighters.add(segmentHighlighter);
    }
  }

  private RangeHighlighter getSegmentHighlighter(int segmentNumber, boolean isSelected, boolean isEnd) {
    TextAttributes attributes = isSelected
                                ? new TextAttributes(null, null, Color.red, EffectType.BOXED, Font.PLAIN)
                                : new TextAttributes();
    TextAttributes endAttributes = new TextAttributes();

    RangeHighlighter segmentHighlighter;
    int start = mySegments.getSegmentStart(segmentNumber);
    int end = mySegments.getSegmentEnd(segmentNumber);
    if (isEnd) {
      segmentHighlighter = myEditor.getMarkupModel()
        .addRangeHighlighter(start, end, HighlighterLayer.LAST + 1, endAttributes, HighlighterTargetArea.EXACT_RANGE);
    }
    else {
      segmentHighlighter = myEditor.getMarkupModel()
        .addRangeHighlighter(start, end, HighlighterLayer.LAST + 1, attributes, HighlighterTargetArea.EXACT_RANGE);
    }
    segmentHighlighter.setGreedyToLeft(true);
    segmentHighlighter.setGreedyToRight(true);
    return segmentHighlighter;
  }

  private void focusCurrentHighlighter(boolean toSelect) {
    if (isFinished()) {
      return;
    }
    if (myCurrentVariableNumber >= myTabStopHighlighters.size()) {
      return;
    }
    RangeHighlighter segmentHighlighter = myTabStopHighlighters.get(myCurrentVariableNumber);
    if (segmentHighlighter != null) {
      RangeHighlighter newSegmentHighlighter = getSegmentHighlighter(getCurrentSegmentNumber(), toSelect, false);
      if (newSegmentHighlighter != null) {
        myEditor.getMarkupModel().removeHighlighter(segmentHighlighter);
        myTabStopHighlighters.set(myCurrentVariableNumber, newSegmentHighlighter);
      }
    }
  }

  private void reformat() {
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    if (file != null) {
      CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
      if (myTemplate.isToShortenLongNames()) {
        try {
          PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
          codeStyleManager.shortenClassReferences(file, myTemplateRange.getStartOffset(), myTemplateRange.getEndOffset());
          unblockDocument();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      if (myTemplate.isToReformat()) {
        try {
          int endSegmentNumber = myTemplate.getEndSegmentNumber();
          PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
          PsiElement marker;
          RangeMarker rangeMarker = null;
          if (endSegmentNumber >= 0) {
            int endVarOffset = mySegments.getSegmentStart(endSegmentNumber);
            marker = codeStyleManager.insertNewLineIndentMarker(file, endVarOffset);
            if(marker != null) rangeMarker = myDocument.createRangeMarker(marker.getTextRange());
          }
          codeStyleManager.reformatText(file, myTemplateRange.getStartOffset(), myTemplateRange.getEndOffset());
          PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);

          if (rangeMarker != null && rangeMarker.isValid()) {
            //[ven] TODO: [max] correct javadoc reformatting to eliminate isValid() check!!!
            mySegments.replaceSegmentAt(endSegmentNumber, rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
            myDocument.deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      else if (myTemplate.isToIndent()) {
        if (!myTemplateIndented) {
          smartIndent(myTemplateRange.getStartOffset(), myTemplateRange.getEndOffset());
          myTemplateIndented = true;
        }
      }
    }
  }

  private void smartIndent(int startOffset, int endOffset) {
    int startLineNum = myDocument.getLineNumber(startOffset);
    int endLineNum = myDocument.getLineNumber(endOffset);
    if (endLineNum == startLineNum) {
      return;
    }

    int indentLineNum = startLineNum;

    int lineLength = 0;
    for (; indentLineNum >= 0; indentLineNum--) {
      lineLength = myDocument.getLineEndOffset(indentLineNum) - myDocument.getLineStartOffset(indentLineNum);
      if (lineLength > 0) {
        break;
      }
    }
    if (indentLineNum < 0) {
      return;
    }
    StringBuffer buffer = new StringBuffer();
    CharSequence text = myDocument.getCharsSequence();
    for (int i = 0; i < lineLength; i++) {
      char ch = text.charAt(myDocument.getLineStartOffset(indentLineNum) + i);
      if (ch != ' ' && ch != '\t') {
        break;
      }
      buffer.append(ch);
    }
    if (buffer.length() == 0) {
      return;
    }
    String stringToInsert = buffer.toString();
    for (int i = startLineNum + 1; i <= endLineNum; i++) {
      myDocument.insertString(myDocument.getLineStartOffset(i), stringToInsert);
    }
  }

  public void addTemplateStateListener(TemplateEditingListener listener) {
    myListeners.add(listener);
  }

  private void fireTemplateFinished() {
    TemplateEditingListener[] listeners = myListeners.toArray(new TemplateEditingListener[myListeners.size()]);
    for (TemplateEditingListener listener : listeners) {
      listener.templateFinished(myTemplate);
    }
  }

  public Map getProperties() {
    return myProperties;
  }

  public TemplateImpl getTemplate() {
    return myTemplate;
  }

  void reset() {
    myListeners = new ArrayList<TemplateEditingListener>();
  }
}
