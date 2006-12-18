package com.intellij.openapi.progress.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiLock;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ProgressManagerImpl extends ProgressManager implements ApplicationComponent {
  @NonNls private static final String PROCESS_CANCELED_EXCEPTION = "idea.ProcessCanceledException";

  private final ConcurrentHashMap<Thread, ProgressIndicator> myThreadToIndicatorMap = new ConcurrentHashMap<Thread, ProgressIndicator>();

  private static volatile boolean ourNeedToCheckCancel = false;
  private static volatile int ourLockedCheckCounter = 0;
  private List<ProgressFunComponentProvider> myFunComponentProviders = new ArrayList<ProgressFunComponentProvider>();

  public ProgressManagerImpl(Application application) {
    if (!application.isUnitTestMode() && !Comparing.equal(System.getProperty(PROCESS_CANCELED_EXCEPTION), "disabled")) {
      new Thread("Progress Cancel Checker") {
        public void run() {
          while (true) {
            try {
              sleep(10);
            }
            catch (InterruptedException e) {
            }
            ourNeedToCheckCancel = true;
          }
        }
      }.start();
    }
  }

  public void checkCanceled() {
    // Q: how about 2 cancelable progresses in time??
    if (ourNeedToCheckCancel) { // smart optimization!
      ourNeedToCheckCancel = false;
      final ProgressIndicator progress = getProgressIndicator();
      if (progress != null) {
        try {
          progress.checkCanceled();
        }
        catch (ProcessCanceledException e) {
          if (!Thread.holdsLock(PsiLock.LOCK)) {
            ourLockedCheckCounter = 0;
            progress.checkCanceled();
          }
          else {
            ourLockedCheckCounter++;
            if (ourLockedCheckCounter > 10) {
              ourLockedCheckCounter = 0;
              ourNeedToCheckCancel = true;
            }
          }
        }
      }
    }
  }

  public JComponent getProvidedFunComponent(Project project, String processId) {
    for (ProgressFunComponentProvider provider : myFunComponentProviders) {
      JComponent cmp = provider.getProgressFunComponent(project, processId);
      if (cmp != null) return cmp;
    }
    return null;
  }

  public void setCancelButtonText(String cancelButtonText) {
    ProgressIndicator progressIndicator = getProgressIndicator();
    if (progressIndicator != null) {
      if (progressIndicator instanceof SmoothProgressAdapter && cancelButtonText != null) {
        ProgressIndicator original = ((SmoothProgressAdapter)progressIndicator).getOriginal();
        if (original instanceof ProgressWindow) {
          ((ProgressWindow)original).setCancelButtonText(cancelButtonText);
        }
      }
    }

  }

  public void registerFunComponentProvider(ProgressFunComponentProvider provider) {
    myFunComponentProviders.add(provider);
  }

  public void removeFunComponentProvider(ProgressFunComponentProvider provider) {
    myFunComponentProviders.remove(provider);
  }

  public String getComponentName() {
    return "ProgressManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public boolean hasProgressIndicator() {
    return !myThreadToIndicatorMap.isEmpty();
  }

  public boolean hasModalProgressIndicator() {
    for (ProgressIndicator indicator : myThreadToIndicatorMap.values()) {
      if (indicator.isModal()) {
        return true;
      }
    }
    return false;
  }

  public void runProcess(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    Thread currentThread = Thread.currentThread();

    ProgressIndicator oldIndicator;
    if (progress == null) {
      oldIndicator = myThreadToIndicatorMap.remove(currentThread);
    }
    else {
      oldIndicator = myThreadToIndicatorMap.put(currentThread, progress);
    }
    synchronized (process) {
      process.notify();
    }
    try {
      if (progress != null && !progress.isRunning()) {
        progress.start();
      }
      process.run();
    }
    finally {
      if (progress != null && progress.isRunning()) {
        progress.stop();
      }
      if (oldIndicator == null) {
        myThreadToIndicatorMap.remove(currentThread);
      }
      else {
        myThreadToIndicatorMap.put(currentThread, oldIndicator);
      }
    }
  }
  public void executeProcessUnderProgress(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    Thread currentThread = Thread.currentThread();

    ProgressIndicator oldIndicator;
    if (progress == null) {
      oldIndicator = myThreadToIndicatorMap.remove(currentThread);
    }
    else {
      oldIndicator = myThreadToIndicatorMap.put(currentThread, progress);
    }
    try {
      process.run();
    }
    finally {
      if (oldIndicator == null) {
        myThreadToIndicatorMap.remove(currentThread);
      }
      else {
        myThreadToIndicatorMap.put(currentThread, oldIndicator);
      }
    }
  }

  public ProgressIndicator getProgressIndicator() {
    return myThreadToIndicatorMap.get(Thread.currentThread());
  }

  public boolean runProcessWithProgressSynchronously(Runnable process, String progressTitle, boolean canBeCanceled, Project project) {
    return ((ApplicationEx)ApplicationManager.getApplication())
      .runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project);
  }

  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull String progressTitle,
                                                   @NotNull final Runnable process,
                                                   @Nullable final Runnable successRunnable,
                                                   @Nullable final Runnable canceledRunnable) {
    runProcessWithProgressAsynchronously(project, progressTitle, process, successRunnable, canceledRunnable, PerformInBackgroundOption.DEAF);
  }

  public void runProcessWithProgressAsynchronously(final @NotNull Project project,
                                                   final @NotNull @Nls String progressTitle,
                                                   final @NotNull Runnable process,
                                                   final @Nullable Runnable successRunnable,
                                                   final @Nullable Runnable canceledRunnable,
                                                   final @NotNull PerformInBackgroundOption option) {
    final ProgressIndicator progressIndicator = new BackgroundableProcessIndicator(project,
                                                                                   progressTitle,
                                                                                   option,
                                                                                   "Cancel",
                                                                                   "Stop " + progressTitle);

    //noinspection HardCodedStringLiteral
    Runnable action = new Runnable() {
      public void run() {
        boolean canceled = false;
        try {
          ProgressManager.getInstance().runProcess(process, progressIndicator);
        }
        catch (ProcessCanceledException e) {
          canceled = true;
        }

        if (canceled && canceledRunnable != null) {
          ApplicationManager.getApplication().invokeLater(canceledRunnable, ModalityState.NON_MODAL);
        }
        else if (!canceled && successRunnable != null) {
          ApplicationManager.getApplication().invokeLater(successRunnable, ModalityState.NON_MODAL);
        }
      }
    };

    synchronized (process) {
      ApplicationManager.getApplication().executeOnPooledThread(action);
      try {
        process.wait();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}