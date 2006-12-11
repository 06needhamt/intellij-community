/*
* Copyright 2000-2005 JetBrains s.r.o.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.intellij.util.concurrency;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

/**
 * This is the 3rd version of SwingWorker (also known as
 * SwingWorker 3), an abstract class that you subclass to
 * perform GUI-related work in a dedicated thread.  For
 * instructions on using this class, see:
 * 
 * http://java.sun.com/docs/books/tutorial/uiswing/misc/threads.html
 *
 * Note that the API changed slightly in the 3rd version:
 * You must now invoke start() on the SwingWorker after
 * creating it.
 */
public abstract class SwingWorker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.concurrency.SwingWorker");
  private Object value;
  // see getValue(), setValue()

  private ModalityState myModalityState;

  /**
   * Class to maintain reference to current worker thread
   * under separate synchronization control.
   */
  private static class ThreadVar {
    private Runnable myTask;
    private Future<?> myTaskFuture;

    ThreadVar(Runnable task) {
      myTask = task;
    }

    synchronized Runnable getTask() {
      return myTask;
    }

    synchronized Future<?> getFuture() {
      return myTaskFuture;
    }

    synchronized void setFuture(Future<?> future) {
      myTaskFuture = future;
    }

    synchronized void clear() {
      myTask = null;
      myTaskFuture = null;
    }
  }

  private ThreadVar myThreadVar;
  /**
   * Get the value produced by the worker thread, or null if it
   * hasn't been constructed yet.
   */

  protected synchronized Object getValue() {
    return value;
  }

  /**
   * Set the value produced by worker thread
   */

  private synchronized void setValue(Object x) {
    value = x;
  }

  /**
   * Compute the value to be returned by the <code>get</code> method.
   */

  public abstract Object construct();

  /**
   * Called on the event dispatching thread (not on the worker thread)
   * after the <code>construct</code> method has returned.
   */

  public void finished() {
  }

  /**
   * Called in the worker thread in case a RuntimeException or Error occurred
   * if the <code>construct</code> method has thrown an uncaught Throwable.
   */
  public void onThrowable() {
  }

  /**
   * A new method that interrupts the worker thread.  Call this method
   * to force the worker to stop what it's doing.
   */

  public void interrupt() {
    Future<?> future = myThreadVar.getFuture();
    if (future != null){
      future.cancel(true);
    }
    myThreadVar.clear();
  }

  /**
   * Return the value created by the <code>construct</code> method.
   * Returns null if either the constructing thread or the current
   * thread was interrupted before a value was produced.
   *
   * @return the value created by the <code>construct</code> method
   */

  public Object get() {
    while(true){
      Future t = myThreadVar.getFuture();
      if (t == null){
        return getValue();
      }
      try{
        t.get();
      }
      catch(InterruptedException e){
        Thread.currentThread().interrupt();
        // propagate
        return null;
      }
      catch (ExecutionException e) {
        return null;
      }
    }
  }

  /**
   * Start a thread that will call the <code>construct</code> method
   * and then exit.
   */

  public SwingWorker() {
    myModalityState = ModalityState.current();

    final Runnable doFinished = new Runnable() {
      public void run() {
        finished();
      }
    };

    Runnable doConstruct = new Runnable() {
      public void run() {
        try {
          try{
          setValue(construct());
          if (LOG.isDebugEnabled()) {
              LOG.debug("construct() terminated");
            }
          }
          catch (Throwable e) {
            LOG.error(e);
            onThrowable();
            throw new RuntimeException(e);
          }
          finally{
            myThreadVar.clear();
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug("invoking 'finished' action");
          }
          ApplicationManager.getApplication().invokeLater(doFinished, myModalityState);
        }
        finally {
          Thread.interrupted(); // reset interrupted before returning to pool
        }
      }
    };

    myThreadVar = new ThreadVar(doConstruct);
  }

  /**
   * Start the worker thread.
   */

  public void start() {
    Runnable t = myThreadVar.getTask();
    if (t != null){
      myThreadVar.setFuture(ApplicationManager.getApplication().executeOnPooledThread(t));
    }
  }
}
