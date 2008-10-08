package com.intellij.diagnostic.logging;

import com.intellij.openapi.util.Key;

/**
 * @author yole
 */
public class LogFragment {
  private String myText;
  private Key myOutputType;

  public LogFragment(final String text, final Key outputType) {
    myText = text;
    myOutputType = outputType;
  }

  public String getText() {
    return myText;
  }

  public Key getOutputType() {
    return myOutputType;
  }
}
