package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.containers.DoubleArrayList;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class ProgressIndicatorBase implements ProgressIndicator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressIndicatorBase");

  private volatile String myText;
  private volatile double myFraction;
  private volatile String myText2;

  private volatile boolean myCanceled;
  private volatile boolean myRunning;

  private volatile boolean myIndeterminate;

  private final ArrayList<String> myTextStack = new ArrayList<String>();
  private final DoubleArrayList myFractionStack = new DoubleArrayList();
  private final ArrayList<String> myText2Stack = new ArrayList<String>();
  private volatile int myNonCancelableCount = 0;

  private ProgressIndicator myModalityProgress = null;
  private ModalityState myModalityState = ModalityState.NON_MODAL;
  private boolean myModalityEntered = false;

  public void start(){
    LOG.assertTrue(!isRunning());
    synchronized(this){
      myText = "";
      myFraction = 0;
      myText2 = "";
      myCanceled = false;
      myRunning = true;
    }

    enterModality();
  }

  protected final void enterModality() {
    if (myModalityProgress == this){
      if (!EventQueue.isDispatchThread()){
        SwingUtilities.invokeLater(
          new Runnable() {
            public void run() {
              doEnterModality();
            }
          }
        );
      }
      else{
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

  public void stop(){
    LOG.assertTrue(myRunning, "stop() should be called only if start() called before");
    myRunning = false;
    exitModality();
  }

  protected final void exitModality() {
    if (myModalityProgress == this){
      if (!EventQueue.isDispatchThread()){
        SwingUtilities.invokeLater(
          new Runnable() {
            public void run() {
              doExitModality();
            }
          }
        );
      }
      else{
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

  public void cancel(){
    myCanceled = true;
  }

  public boolean isCanceled(){
    return myCanceled;
  }

  public final void checkCanceled(){
    if (isCanceled() && myNonCancelableCount == 0) {
      throw new ProcessCanceledException();
    }
  }

  public void setText(String text){
    myText = text;
  }

  public String getText(){
    return myText;
  }

  public void setText2(String text){
    myText2 = text;
  }

  public String getText2(){
    return myText2;
  }

  public double getFraction() {
    return myFraction;
  }

  public void setFraction(double fraction) {
    myFraction = fraction;
  }

  public void pushState(){
    synchronized(this){
      myTextStack.add(myText);
      myFractionStack.add(myFraction);
      myText2Stack.add(myText2);
      setText("");
      setFraction(0);
      setText2("");
    }
  }

  public void popState(){
    synchronized(this){
      LOG.assertTrue(!myTextStack.isEmpty());
      setText(myTextStack.remove(myTextStack.size() - 1));
      setFraction(myFractionStack.remove(myFractionStack.size() - 1));
      setText2(myText2Stack.remove(myText2Stack.size() - 1));
    }
  }

  public void startNonCancelableSection(){
    myNonCancelableCount++;
  }

  public void finishNonCancelableSection(){
    myNonCancelableCount--;
  }

  protected boolean isCancelable() {
    return myNonCancelableCount == 0;
  }

  public final boolean isModal(){
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

  public void setIndeterminate(boolean indeterminate) {
    myIndeterminate = indeterminate;
  }

  public void restart() {
    if (myRunning) {
      myRunning = false;
      exitModality();
    }
    start();
  }
}
