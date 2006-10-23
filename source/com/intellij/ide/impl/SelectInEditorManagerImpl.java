package com.intellij.ide.impl;

import com.intellij.ide.SelectInEditorManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author MYakovlev
 * Date: Jul 1, 2002
 */
public class SelectInEditorManagerImpl extends SelectInEditorManager implements ProjectComponent, FocusListener, CaretListener{
  private Project myProject;
  private RangeHighlighter mySegmentHighlighter;
  private Editor myEditor;

  public SelectInEditorManagerImpl(Project project){
    myProject = project;
  }

  public void projectOpened(){
  }

  public void projectClosed(){
  }

  @NotNull
  public String getComponentName(){
    return "SelectInEditorManager";
  }

  public void initComponent() { }

  public void disposeComponent(){
    releaseAll();
  }

  public void selectInEditor(VirtualFile file, final int startOffset, final int endOffset, final boolean toSelectLine, final boolean toUseNormalSelection){
    releaseAll();
    openEditor(file, endOffset);
    final Editor editor = openEditor(file, startOffset);

    SwingUtilities.invokeLater(new Runnable(){ // later to let focus listener chance to handle events
      public void run() {
        if (editor != null && !editor.isDisposed()) {
          doSelect(toUseNormalSelection, editor, toSelectLine, startOffset, endOffset);
        }
      }
    });
  }

  private void doSelect(final boolean toUseNormalSelection, @NotNull final Editor editor,
                        final boolean toSelectLine,
                        final int startOffset,
                        final int endOffset) {
    if(toUseNormalSelection) {
      DocumentEx doc = (DocumentEx) editor.getDocument();
      if (toSelectLine){
        int lineNumber = doc.getLineNumber(startOffset);
        if (lineNumber >= 0 && lineNumber < doc.getLineCount()) {
          editor.getSelectionModel().setSelection(doc.getLineStartOffset(lineNumber), doc.getLineEndOffset(lineNumber) + doc.getLineSeparatorLength(lineNumber));
        }
      }
      else {
        editor.getSelectionModel().setSelection(startOffset, endOffset);
      }
      return;
    }

    TextAttributes selectionAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

    releaseAll();

    if (toSelectLine){
      DocumentEx doc = (DocumentEx) editor.getDocument();
      int lineNumber = doc.getLineNumber(startOffset);
      if (lineNumber >= 0 && lineNumber < doc.getLineCount()){
        mySegmentHighlighter = editor.getMarkupModel().addRangeHighlighter(doc.getLineStartOffset(lineNumber),
                                                                           doc.getLineEndOffset(lineNumber) + doc.getLineSeparatorLength(lineNumber),
                                                                           HighlighterLayer.LAST + 1,
                                                                           selectionAttributes, HighlighterTargetArea.EXACT_RANGE);
      }
    }
    else{
      mySegmentHighlighter = editor.getMarkupModel().addRangeHighlighter(startOffset,
                                                                         endOffset,
                                                                         HighlighterLayer.LAST + 1,
                                                                         selectionAttributes, HighlighterTargetArea.EXACT_RANGE);
    }
    myEditor = editor;
    myEditor.getContentComponent().addFocusListener(this);
    myEditor.getCaretModel().addCaretListener(this);
  }

  public void focusGained(FocusEvent e) {
    releaseAll();
  }

  public void focusLost(FocusEvent e) {
  }

  public void caretPositionChanged(CaretEvent e) {
    releaseAll();
  }

  private void releaseAll() {
    if (mySegmentHighlighter != null && myEditor != null){
      myEditor.getMarkupModel().removeHighlighter(mySegmentHighlighter);
      myEditor.getContentComponent().removeFocusListener(this);
      myEditor.getCaretModel().removeCaretListener(this);
      mySegmentHighlighter = null;
      myEditor = null;
    }
  }

  private Editor openEditor(VirtualFile file, int textOffset){
    if (file == null || !file.isValid()){
      return null;
    }
    OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, file, textOffset);
    return FileEditorManager.getInstance(myProject).openTextEditor(descriptor, false);
  }
}
