package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"HardCodedStringLiteral"})
public class AddTestProcessAction extends AnAction {
  public AddTestProcessAction() {
    super("Add Test Process");
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    new Task.Backgroundable(project, "Test Process", true) {
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          Thread.currentThread().sleep(6000);

          countTo(1000, new Count() {
            public void onCount(int each) throws InterruptedException {
              indicator.setText("Found: " + each / 20 + 1);
              if (each / 10.0 == Math.round(each / 10.0)) {
                indicator.setText(null);
              }
              indicator.setFraction(each / 1000.0);
              Thread.currentThread().sleep(100);
              indicator.checkCanceled();
              indicator.setText2("bla bla bla");
            }
          });
          indicator.stop();
        }
        catch (Exception e1) {
          indicator.stop();
          return;
        }
      }
    }.queue();
  }

  private void countTo(int top, Count count) throws Exception {
    for (int i = 0; i < top; i++) {
      count.onCount(i);
    }
  }

  private static interface Count {
    void onCount(int each) throws InterruptedException;
  }
}
