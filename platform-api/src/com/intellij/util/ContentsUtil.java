package com.intellij.util;

import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.Content;

public class ContentsUtil {
  private ContentsUtil() {
  }

  public static void addOrReplaceContent(ContentManager manager, Content content, boolean select) {
    final String contentName = content.getDisplayName();

    Content[] contents = manager.getContents();
    for(Content oldContent: contents) {
      if (!oldContent.isPinned() && oldContent.getDisplayName().equals(contentName)) {
        manager.removeContent(oldContent, true);
      }
    }
    
    manager.addContent(content);
    if (select) {
      manager.setSelectedContent(content);
    }
  }

  public static void addContent(final ContentManager manager, final Content content, final boolean select) {
    manager.addContent(content);
    if (select) {
      manager.setSelectedContent(content);
    }
  }
}
