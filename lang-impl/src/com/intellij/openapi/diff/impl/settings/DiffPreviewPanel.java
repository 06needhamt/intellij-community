package com.intellij.openapi.diff.impl.settings;

import com.intellij.application.options.colors.ColorAndFontSettingsListener;
import com.intellij.application.options.colors.PreviewPanel;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.impl.incrementalMerge.Change;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeList;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeSearchHelper;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.EditorPlace;
import com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

public class DiffPreviewPanel implements PreviewPanel {
  private MergePanel2.AsComponent myMergePanelComponent;
  private JComponent myWholePanel;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public DiffPreviewPanel() {
    myMergePanelComponent.setToolbarEnabled(false);
    MergePanel2 mergePanel = getMergePanel();
    mergePanel.setEditorProperty(MergePanel2.LINE_NUMBERS, Boolean.FALSE);
    mergePanel.setEditorProperty(MergePanel2.LINE_MARKERS_AREA, Boolean.FALSE);
    mergePanel.setEditorProperty(MergePanel2.ADDITIONAL_LINES, 1);
    mergePanel.setEditorProperty(MergePanel2.ADDITIONAL_COLUMNS, 1);

    for (int i = 0; i < MergePanel2.EDITORS_COUNT; i++) {
      final EditorMouseListener motionListener = new EditorMouseListener(i);
      final EditorClickListener clickListener = new EditorClickListener(i);
      mergePanel.getEditorPlace(i).addListener(new EditorPlace.EditorListener() {
        public void onEditorCreated(EditorPlace place) {
          Editor editor = place.getEditor();
          editor.addEditorMouseMotionListener(motionListener);
          editor.addEditorMouseListener(clickListener);
          editor.getCaretModel().addCaretListener(clickListener);
        }

        public void onEditorReleased(Editor releasedEditor) {
          releasedEditor.removeEditorMouseMotionListener(motionListener);
          releasedEditor.removeEditorMouseListener(clickListener);
        }
      });
      Editor editor = mergePanel.getEditor(i);
      if (editor != null) {
        editor.addEditorMouseMotionListener(motionListener);
        editor.addEditorMouseListener(clickListener);
      }
    }
  }

  public Component getPanel() {
    return myWholePanel;
  }

  public void updateView() {
    MergeList mergeList = getMergePanel().getMergeList();
    if (mergeList != null) mergeList.updateMarkup();
    myMergePanelComponent.repaint();

  }

  public void setMergeRequest(Project project) {
    getMergePanel().setDiffRequest(new SampleMerge(project));
  }

  private MergePanel2 getMergePanel() {
    return myMergePanelComponent.getMergePanel();
  }

  public void setColorScheme(final EditorColorsScheme highlighterSettings) {
    getMergePanel().setColorScheme(highlighterSettings);
    getMergePanel().setEditorProperty(MergePanel2.HIGHLIGHTER_SETTINGS, highlighterSettings);
  }

  private class EditorMouseListener extends EditorMouseMotionAdapter {
    private final int myIndex;

    public EditorMouseListener(int index) {
      myIndex = index;
    }

    public void mouseMoved(EditorMouseEvent e) {
      MergePanel2 mergePanel = getMergePanel();
      Editor editor = mergePanel.getEditor(myIndex);
      if (MergeSearchHelper.findChangeAt(e, mergePanel, myIndex) != null) EditorUtil.setHandCursor(editor);
    }
  }

  private static class SampleMerge extends DiffRequest {
    public SampleMerge(Project project) {
      super(project);
    }

    public DiffContent[] getContents() {
      return new DiffContent[]{createContent(LEFT_TEXT), createContent(CENTER_TEXT), createContent(RIGHT_TEXT)};
    }

    private static SimpleContent createContent(String text) {
      return new SimpleContent(text, StdFileTypes.JAVA);
    }

    public String[] getContentTitles() { return new String[]{"", "", ""}; }
    public String getWindowTitle() { return DiffBundle.message("merge.color.options.dialog.title"); }
  }

  public void addListener(final ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }

  @NonNls private static final String LEFT_TEXT = "class MyClass {\n" +
                                                                            "  int value;\n" +
                                                                            "\n" +
                                                                            "  void leftOnly() {}\n" +
                                                                            "\n" +
                                                                            "  void foo() {\n" +
                                                                            "   // Left changes\n" +
                                                                            "  }\n" +
                                                                            "}";
  @NonNls private static final String CENTER_TEXT = "class MyClass {\n" +
                                            "  int value;\n" +
                                            "\n" +
                                            "  void foo() {\n" +
                                            "  }\n" +
                                            "\n" +
                                            "  void removedFromLeft() {}\n" +
                                            "}";
  @NonNls private static final String RIGHT_TEXT = "class MyClass {\n" +
                                           "  long value;\n" +
                                           "\n" +
                                           "  void foo() {\n" +
                                           "   // Left changes\n" +
                                           "  }\n" +
                                           "\n" +
                                           "  void removedFromLeft() {}\n" +
                                           "}";

  private class EditorClickListener extends EditorMouseAdapter implements CaretListener {
    private final int myIndex;

    public EditorClickListener(int i) {
      myIndex = i;
    }

    public void mouseClicked(EditorMouseEvent e) {
      select(MergeSearchHelper.findChangeAt(e, getMergePanel(), myIndex));
    }

    private void select(Change change) {
      if (change == null) return;
      myDispatcher.getMulticaster().selectionInPreviewChanged(change.getType().getTextDiffType().getDisplayName());
     }

    public void caretPositionChanged(CaretEvent e) {
      select(MergeSearchHelper.findChangeAt(e, getMergePanel(), myIndex));
    }
  }


}