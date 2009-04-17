package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DoubleArrayList;
import com.intellij.util.containers.Stack;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProgressIndicatorBase extends UserDataHolderBase implements ProgressIndicatorEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressIndicatorBase");

  private volatile String myText;
  private volatile double myFraction;
  private volatile String myText2;

  private volatile boolean myCanceled;
  private volatile boolean myRunning;

  private volatile boolean myIndeterminate;

  private final Stack<String> myTextStack = new Stack<String>();
  private final DoubleArrayList myFractionStack = new DoubleArrayList();
  private final Stack<String> myText2Stack = new Stack<String>();
  private volatile int myNonCancelableCount = 0;

  private ProgressIndicator myModalityProgress = null;
  private ModalityState myModalityState = ModalityState.NON_MODAL;
  private boolean myModalityEntered = false;

  private final CopyOnWriteArrayList<ProgressIndicatorEx> myStateDelegates = ContainerUtil.createEmptyCOWList();
  private final WeakList<TaskInfo> myFinished = new WeakList<TaskInfo>();
  private boolean myWasStarted;

  private TaskInfo myOwnerTask;
  private static final IndicatorAction CHECK_CANCELED_ACTION = new IndicatorAction() {
    public void execute(final ProgressIndicatorEx each) {
      each.checkCanceled();
    }
  };

  public void start() {
    synchronized (this) {
      LOG.assertTrue(!isRunning(), "Attempt to start ProgressIndicator which is already running");
      myText = "";
      myFraction = 0;
      myText2 = "";
      myCanceled = false;
      myRunning = true;
      myWasStarted = true;

      delegateRunningChange(new IndicatorAction() {
        public void execute(final ProgressIndicatorEx each) {
          each.start();
        }
      });
    }

    enterModality();
  }

  protected final void enterModality() {
    if (myModalityProgress == this) {
      if (!EventQueue.isDispatchThread()) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            doEnterModality();
          }
        });
      }
      else {
        doEnterModality();
      }
    }
  }

  private void doEnterModality() {
    if (!myModalityEntered) {
      LaterInvocator.enterModal(this);
      myModalityEntered = true;
    }
  }

  public void stop() {
    LOG.assertTrue(myRunning, "stop() should be called only if start() called before");
    myRunning = false;

    delegateRunningChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.stop();
      }
    });
    exitModality();
  }

  protected final void exitModality() {
    if (myModalityProgress == this) {
      if (!EventQueue.isDispatchThread()) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            doExitModality();
          }
        });
      }
      else {
        doExitModality();
      }
    }
  }

  private void doExitModality() {
    if (myModalityEntered) {
      LaterInvocator.leaveModal(this);
      myModalityEntered = false;
    }
  }

  public boolean isRunning() {
    return myRunning;
  }

  public void cancel() {
    myCanceled = true;

    delegateRunningChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.cancel();
      }
    });
  }

  public void finish(@NotNull final TaskInfo task) {
    synchronized (myFinished) {
      if (myFinished.contains(task)) return;

      myFinished.add(task);
    }

    delegateRunningChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.finish(task);
      }
    });
  }

  public boolean isFinished(@NotNull final TaskInfo task) {
    synchronized (myFinished) {
      return myFinished.contains(task);
    }
  }

  protected void setOwnerTask(TaskInfo owner) {
    myOwnerTask = owner;
  }

  public void processFinish() {
    if (myOwnerTask != null) {
      finish(myOwnerTask);
    }
  }

  public boolean isCanceled() {
    return myCanceled;
  }

  public final void checkCanceled() {
    if (isCanceled() && myNonCancelableCount == 0) {
      throw new ProcessCanceledException();
    }

    delegate(CHECK_CANCELED_ACTION);
  }

  public void setText(final String text) {
    myText = text;

    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.setText(text);
      }
    });
  }

  public String getText() {
    return myText;
  }

  public void setText2(final String text) {
    myText2 = text;

    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.setText2(text);
      }
    });
  }

  public String getText2() {
    return myText2;
  }

  public double getFraction() {
    return myFraction;
  }

  public void setFraction(final double fraction) {
    myFraction = fraction;

    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.setFraction(fraction);
      }
    });
  }

  public synchronized void pushState() {
    myTextStack.push(myText);
    myFractionStack.add(myFraction);
    myText2Stack.push(myText2);
    setText("");
    setFraction(0);
    setText2("");

    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.pushState();
      }
    });
  }

  public synchronized void popState() {
    LOG.assertTrue(!myTextStack.isEmpty());
    setText(myTextStack.pop());
    setFraction(myFractionStack.remove(myFractionStack.size() - 1));
    setText2(myText2Stack.pop());

    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.popState();
      }
    });
  }

  public void startNonCancelableSection() {
    myNonCancelableCount++;

    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.startNonCancelableSection();
      }
    });
  }

  public void finishNonCancelableSection() {
    myNonCancelableCount--;

    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.finishNonCancelableSection();
      }
    });
  }

  protected boolean isCancelable() {
    return myNonCancelableCount == 0;
  }

  public final boolean isModal() {
    return myModalityProgress != null;
  }

  public final ModalityState getModalityState() {
    return myModalityState;
  }

  public void setModalityProgress(ProgressIndicator modalityProgress) {
    LOG.assertTrue(!isRunning());
    myModalityProgress = modalityProgress;
    ModalityState currentModality = ApplicationManager.getApplication().getCurrentModalityState();
    myModalityState = myModalityProgress != null ? ((ModalityStateEx)currentModality).appendProgress(myModalityProgress) : currentModality;
  }

  public boolean isIndeterminate() {
    return myIndeterminate;
  }

  public void setIndeterminate(final boolean indeterminate) {
    myIndeterminate = indeterminate;


    delegateProgressChange(new IndicatorAction() {
      public void execute(final ProgressIndicatorEx each) {
        each.setIndeterminate(indeterminate);
      }
    });
  }

  public final void addStateDelegate(@NotNull ProgressIndicatorEx delegate) {
    delegate.initStateFrom(this);
    myStateDelegates.addIfAbsent(delegate);
  }

  private void delegateProgressChange(IndicatorAction action) {
    delegate(action);
    onProgressChange();
  }

  private void delegateRunningChange(IndicatorAction action) {
    delegate(action);
    onRunningChange();
  }

  private void delegate(IndicatorAction action) {
    for (ProgressIndicatorEx each : myStateDelegates) {
      action.execute(each);
    }
  }

  private interface IndicatorAction {
    void execute(ProgressIndicatorEx each);
  }


  protected void onProgressChange() {

  }

  protected void onRunningChange() {

  }

  @NotNull
  public Stack<String> getTextStack() {
    return myTextStack;
  }

  @NotNull
  public DoubleArrayList getFractionStack() {
    return myFractionStack;
  }

  @NotNull
  public Stack<String> getText2Stack() {
    return myText2Stack;
  }

  public int getNonCancelableCount() {
    return myNonCancelableCount;
  }


  public boolean isModalityEntered() {
    return myModalityEntered;
  }

  public boolean wasStarted() {
    return myWasStarted;
  }

  public synchronized void initStateFrom(@NotNull final ProgressIndicatorEx indicator) {
    myRunning = indicator.isRunning();
    myCanceled = indicator.isCanceled();
    myModalityEntered = indicator.isModalityEntered();
    myFraction = indicator.getFraction();
    myIndeterminate = indicator.isIndeterminate();
    myNonCancelableCount = indicator.getNonCancelableCount();

    myTextStack.clear();
    myTextStack.addAll(indicator.getTextStack());
    myText = indicator.getText();

    myText2Stack.clear();
    myText2Stack.addAll(indicator.getText2Stack());
    myText2 = indicator.getText2();

    myFractionStack.clear();
    final double[] fractions = indicator.getFractionStack().toArray();
    for (double eachFraction : fractions) {
      myFractionStack.add(eachFraction);
    }
    myFraction = indicator.getFraction();
  }

}
