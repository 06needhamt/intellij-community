package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.Processor;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyLineBreakpointType extends XLineBreakpointType<XBreakpointProperties> {
  private final PyDebuggerEditorsProvider myEditorsProvider = new PyDebuggerEditorsProvider();

  public PyLineBreakpointType() {
    super("python-line", "Python Line Breakpoint");
  }

  public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull final Project project) {
    final Ref<Boolean> stoppable = Ref.create(false);
    if (file.getFileType() == PythonFileType.INSTANCE) {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>() {
          public boolean process(PsiElement psiElement) {
            if (psiElement instanceof PsiWhiteSpace || psiElement instanceof PsiComment) return true;
            // Python debugger seems to be able to stop on pretty much everything
            stoppable.set(true);
            return false;
          }
        });
      }
    }
    return stoppable.get();
  }

  @Nullable
  public XBreakpointProperties createBreakpointProperties(@NotNull final VirtualFile file, final int line) {
    return null;
  }

  @Override
  public String getBreakpointsDialogHelpTopic() {
    return "reference.dialogs.breakpoints";
  }

  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }
}
