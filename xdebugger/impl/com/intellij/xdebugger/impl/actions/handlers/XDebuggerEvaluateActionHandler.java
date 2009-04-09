package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.evaluate.EvaluationDialog;
import com.intellij.xdebugger.impl.evaluate.EvaluationDialogMode;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XDebuggerEvaluateActionHandler extends XDebuggerSuspendedActionHandler {
  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    XStackFrame stackFrame = session.getCurrentStackFrame();
    if (stackFrame == null || editorsProvider == null) return;
    final XDebuggerEvaluator evaluator = stackFrame.getEvaluator();
    if (evaluator == null) return;

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    String expression = editor != null ? editor.getSelectionModel().getSelectedText() : null;
    if (expression == null) {
      XValue value = XDebuggerTreeActionBase.getSelectedValue(dataContext);
      if (value != null) {
        expression = value.getEvaluationExpression();
      }
    }
    if (expression == null) {
      expression = "";
    }
    final EvaluationDialogMode mode = expression.indexOf('\n') == -1 ? EvaluationDialogMode.EXPRESSION : EvaluationDialogMode.CODE_FRAGMENT;
    final EvaluationDialog dialog = new EvaluationDialog(session, editorsProvider, evaluator, expression, mode, 
                                                         stackFrame.getSourcePosition());
    dialog.show();
  }

  protected boolean isEnabled(final @NotNull XDebugSession session, final DataContext dataContext) {
    if (!super.isEnabled(session, dataContext)) {
      return false;
    }

    XStackFrame stackFrame = session.getCurrentStackFrame();
    return stackFrame != null && stackFrame.getEvaluator() != null && session.getDebugProcess().getEditorsProvider() != null;
  }
}
