/*
 * @author max
 */
package com.intellij.concurrency;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.impl.ApplicationImpl;
import org.jetbrains.annotations.NonNls;

import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@NonNls
public class JobSchedulerImpl extends JobScheduler implements Disposable {
  public static final int CORES_COUNT = /*1;//*/Runtime.getRuntime().availableProcessors();

  private static final ThreadFactory WORKERS_FACTORY = new ThreadFactory() {
    int i;
    public Thread newThread(final Runnable r) {
      return new Thread(r, "JobScheduler pool "+i++);
    }
  };

  private static final Lock ourSuspensionLock = new ReentrantLock();

  private static final PriorityBlockingQueue<Runnable> ourQueue = new PriorityBlockingQueue<Runnable>() {
    public Runnable poll() {
      final Runnable result = super.poll();

      ourSuspensionLock.lock();
      try {
        return result;
      }
      finally {
        ourSuspensionLock.unlock();
      }
    }

    public Runnable poll(final long timeout, final TimeUnit unit) throws InterruptedException {
      final Runnable result = super.poll(timeout, unit);

      ourSuspensionLock.lock();
      try {
        return result;
      }
      finally {
        ourSuspensionLock.unlock();
      }
    }
  };
  private static final ThreadPoolExecutor ourExecutor = new ThreadPoolExecutor(CORES_COUNT, Integer.MAX_VALUE, 60 * 10, TimeUnit.SECONDS,
                                                                               ourQueue, WORKERS_FACTORY) {
    protected void beforeExecute(final Thread t, final Runnable r) {
      PrioritizedFutureTask task = (PrioritizedFutureTask)r;
      if (task.isParentThreadHasReadAccess()) {
        ApplicationImpl.setExceptionalThreadWithReadAccessFlag(true);
      }
      task.signalStarted();

      // TODO: hook up JobMonitor into thread locals
      super.beforeExecute(t, r);
    }

    protected void afterExecute(final Runnable r, final Throwable t) {
      super.afterExecute(r, t);
      ApplicationImpl.setExceptionalThreadWithReadAccessFlag(false);
      PrioritizedFutureTask task = (PrioritizedFutureTask)r;
      task.signalDone();
      // TODO: cleanup JobMonitor
    }
  };

  private static volatile long ourJobsCounter = 0;

  public static void execute(Runnable task) {
    ourExecutor.execute(task);
  }

  public static int currentTaskIndex() {
    final PrioritizedFutureTask topTask = (PrioritizedFutureTask)ourQueue.peek();
    return topTask == null ? 0 : topTask.getTaskIndex();
  }

  public static long currentJobIndex() {
    return ourJobsCounter++;
  }

  public static void suspend() {
    ourSuspensionLock.lock();
  }

  public static void resume() {
    ourSuspensionLock.unlock();
  }

  public <T> Job<T> createJob(String title, int priority) {
    return new JobImpl<T>(title, priority);
  }

  public void dispose() {
    ((ThreadPoolExecutor)getScheduler()).getQueue().clear();
  }
}
