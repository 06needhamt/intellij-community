package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

import com.intellij.cvsSupport2.errorHandling.CvsProcessException;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;

public class CvsListenerWithProgress extends CvsMessagesAdapter implements ICvsCommandStopper,ErrorRegistry {
  private ProgressIndicator myProgressIndicator;
  private String myLastError;

  public CvsListenerWithProgress(ProgressIndicator progressIndicator) {
    myProgressIndicator = progressIndicator;
  }

  public static CvsListenerWithProgress createOnProgress() {
    return new CvsListenerWithProgress(ProgressManager.getInstance().getProgressIndicator());
  }

  public void addFileMessage(FileMessage message) {
    if (myProgressIndicator != null) {
      message.showMessageIn(myProgressIndicator);
    }
  }

  public ProgressIndicator getProgressIndicator() {
    if (myProgressIndicator == null) {
      myProgressIndicator = ProgressManager.getInstance().getProgressIndicator();
    }
    return myProgressIndicator;
  }

  public boolean isAborted() {
    if (myLastError != null) throw new CvsProcessException(myLastError);
    final ProgressIndicator progressIndicator = getProgressIndicator();
    if (progressIndicator == null) return false;
    return progressIndicator.isCanceled();
  }

  public void registerError(String description) {
    myLastError = description;
  }
}
