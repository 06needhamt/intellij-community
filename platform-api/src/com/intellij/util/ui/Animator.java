/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;

public abstract class Animator implements Disposable {

  private String myName;
  private int myTotalFrames;
  private int myCycleLength;
  private Timer myTimer;

  private int myCurrentFrame;
  private int myQueuedFrames = 0;

  private final boolean myRepeatable;

  private int myRepeatCount;

  private boolean myLastAnimated;

  private boolean myForward = true;

  public Animator(final String name,
                  final int totalFrames,
                  final int cycleLength,
                  boolean repeatable,
                  final int interCycleGap,
                  final int maxRepeatCount) {

    this(name, totalFrames, cycleLength, repeatable, interCycleGap, maxRepeatCount, true);
  }

  public Animator(final String name,
                  final int totalFrames,
                  final int cycleLength,
                  boolean repeatable,
                  final int interCycleGap,
                  final int maxRepeatCount, boolean forward) {
    myName = name;
    myTotalFrames = totalFrames;
    myCycleLength = cycleLength;
    myRepeatable = repeatable;
    myForward = forward;
    myCurrentFrame = forward ? 0 : totalFrames;

    myTimer = ApplicationManager.getApplication().isUnitTestMode() ?
              new Timer(myName, myCycleLength / myTotalFrames) {
                {
                  dispose();
                }
                @Override
                public boolean isRunning() {
                  return true;
                }

                @Override
                protected void onTimer() throws InterruptedException {

                }
              }
              : new Timer(myName, myCycleLength / myTotalFrames) {
      protected void onTimer() throws InterruptedException {
        boolean repaint = true;
        if (!isAnimated()) {
          if (myLastAnimated) {
            myCurrentFrame = myForward ? 0 : myTotalFrames;
            myQueuedFrames = 0;
            myLastAnimated = false;
          }
          else {
            repaint = false;
          }
        }
        else {
          myLastAnimated = true;

          if (myQueuedFrames > myTotalFrames) return;

          boolean toNextFrame = myForward ? myCurrentFrame + 1 < myTotalFrames : myCurrentFrame - 1 >= 0;

          if (toNextFrame && myForward) {
            myCurrentFrame++;
          } else if (toNextFrame && !myForward) {
            myCurrentFrame--;
          } else {
            if (myRepeatable) {
              if (maxRepeatCount == -1 || myRepeatCount < maxRepeatCount) {
                myRepeatCount++;
                myCurrentFrame = 0;
                if (interCycleGap > 0) {
                  Thread.sleep(interCycleGap - getSpan());
                }
              }
              else {
                repaint = false;
                suspend();
                myRepeatCount = 0;
                cycleEnd();
              }
            }
            else {
              repaint = false;
              suspend();
              cycleEnd();
            }
          }
        }

        if (repaint) {
          myQueuedFrames++;
          // paint to EDT
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myQueuedFrames--;
              paintNow(myCurrentFrame, (float)myTotalFrames, (float)myCycleLength);
            }
          });
        }
      }
    };
  }

  @SuppressWarnings({"SSBasedInspection"})
  // paint to EDT
  private void cycleEnd() throws InterruptedException {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        paintCycleEnd();
      }
    });
    onAnimationMaxCycleReached();
  }

  protected void paintCycleEnd() {

  }

  protected void onAnimationMaxCycleReached() throws InterruptedException {

  }

  public void suspend() {
    myTimer.suspend();
  }

  public void resume() {
    myTimer.resume();
  }

  public void setTakInitialDelay(boolean take) {
    myTimer.setTakeInitialDelay(take);
  }

  public abstract void paintNow(float frame, final float totalFrames, final float cycle);

  public void dispose() {
    myTimer.dispose();
  }

  public boolean isRunning() {
    return myTimer.isRunning() && myLastAnimated;
  }

  public boolean isAnimated() {
    return true;
  }

  public void reset() {
    myCurrentFrame = 0;
    myRepeatCount = 0;
  }

  public final boolean isForward() {
    return myForward;
  }

  public boolean isDisposed() {
    return myTimer.isDisposed();
  }
}
