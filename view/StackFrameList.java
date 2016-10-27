package org.jetbrains.debugger.memory.view;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.utils.StackFrameDescriptor;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

class StackFrameList extends JBList {
  private static final char ANONYMOUS_CLASS_DELIMITER = '$';
  private static final MyOpenFilesState myEditorState = new MyOpenFilesState();

  private List<StackFrameDescriptor> myStackFrames;
  private final Project myProject;
  private final GlobalSearchScope myScope;
  private final MyListModel myModel = new MyListModel();

  StackFrameList(@NotNull Project project,
                 @NotNull List<StackFrameDescriptor> stack,
                 @NotNull GlobalSearchScope searchScope) {
    super();

    myStackFrames = new ArrayList<>(stack);
    myProject = project;
    myScope = searchScope;

    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    //noinspection unchecked
    setModel(myModel);

    setCellRenderer(new ColoredListCellRenderer<StackFrameDescriptor>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends StackFrameDescriptor> list,
                                           StackFrameDescriptor value, int index, boolean isSelected, boolean hasFocus) {
        append(String.format("%s:%d, %s", value.methodName(), value.line(), value.className()));
        String packageName = value.packageName();
        if (!StringUtils.isEmpty(packageName)) {
          append(String.format(" (%s)", value.packageName()), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
        }
      }
    });
  }

  void setFrame(@NotNull List<StackFrameDescriptor> stack) {
    myModel.update(stack);
  }

  void navigateToSelectedValue(boolean focusOnEditor) {
    StackFrameDescriptor selectedValue = (StackFrameDescriptor) getSelectedValue();
    if (selectedValue != null) {
      navigateToFrame(selectedValue, focusOnEditor);
    }
  }

  private void navigateToFrame(@NotNull StackFrameDescriptor frame, boolean focusOnEditor) {
    String path = frame.path();
    int anonymousClassDelimiterIndex = path.indexOf(ANONYMOUS_CLASS_DELIMITER);
    int pathLength = anonymousClassDelimiterIndex > 0 ? anonymousClassDelimiterIndex : path.length();
    path = path.substring(0, pathLength);
    PsiClass psiClass = DebuggerUtils.findClass(path, myProject, myScope);
    if (psiClass != null) {
      ApplicationManager.getApplication().runReadAction(() -> {
        PsiElement navigationElement = psiClass.getNavigationElement();
        VirtualFile file = PsiUtilCore.getVirtualFile(navigationElement);

        if (file == null) {
          file = psiClass.getContainingFile().getVirtualFile();
        }

        OpenFileHyperlinkInfo info =
            new OpenFileHyperlinkInfo(myProject, file, frame.line() - 1);
        OpenFileDescriptor descriptor = info.getDescriptor();
        if (descriptor != null) {
          FileEditorManager manager = FileEditorManager.getInstance(myProject);
          VirtualFile lastFile = myEditorState.myLastOpenedFile;
          if (myEditorState.myIsNeedToCloseLastOpenedFile && lastFile != null &&
              manager.isFileOpen(lastFile) && !lastFile.equals(descriptor.getFile())) {
            manager.closeFile(myEditorState.myLastOpenedFile);
          }

          descriptor.setScrollType(ScrollType.CENTER);

          if (lastFile == null || !lastFile.equals(descriptor.getFile())) {
            myEditorState.myIsNeedToCloseLastOpenedFile = !manager.isFileOpen(descriptor.getFile());
          }

          List<FileEditor> fileEditors = manager.openEditor(descriptor, focusOnEditor);
          if (!fileEditors.isEmpty()) {
            myEditorState.myLastOpenedFile = descriptor.getFile();
          }
        }
      });
    }
  }

  private static class MyOpenFilesState {
    VirtualFile myLastOpenedFile;
    boolean myIsNeedToCloseLastOpenedFile;
  }

  private class MyListModel extends AbstractListModel<StackFrameDescriptor> {

    void update(@NotNull List<StackFrameDescriptor> newFrame) {
      fireIntervalRemoved(this, 0, getSize());
      myStackFrames = newFrame;
      fireIntervalAdded(this, 0, getSize());
    }

    @Override
    public int getSize() {
      return myStackFrames.size();
    }

    @Override
    public StackFrameDescriptor getElementAt(int index) {
      return myStackFrames.get(index);
    }
  }
}
