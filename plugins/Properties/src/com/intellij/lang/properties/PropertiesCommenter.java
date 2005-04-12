package com.intellij.lang.properties;

import com.intellij.lang.Commenter;

/**
 * @author max
 */
public class PropertiesCommenter implements Commenter {
  public String getLineCommentPrefix() {
    return "#";
  }

  public boolean isLineCommentPrefixOnZeroColumn() {
    return true;
  }

  public String getBlockCommentPrefix() {
    return null;
  }

  public String getBlockCommentSuffix() {
    return null;
  }
}
