/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.patterns;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ProcessingContext;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author nik
 */
public class VirtualFilePattern extends TreeElementPattern<VirtualFile, VirtualFile, VirtualFilePattern> {
  public VirtualFilePattern() {
    super(VirtualFile.class);
  }

  public VirtualFilePattern ofType(final FileType type) {
    return with(new PatternCondition<VirtualFile>("ofType") {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        return type.equals(virtualFile.getFileType());
      }
    });
  }

  public VirtualFilePattern withName(final ElementPattern<String> namePattern) {
    return with(new PatternCondition<VirtualFile>("withName") {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        return namePattern.getCondition().accepts(virtualFile.getName(), context);
      }
    });
  }

  public VirtualFilePattern xmlWithRootTag(final ElementPattern<String> tagNamePattern) {
    return with(new PatternCondition<VirtualFile>("xmlWithRootTag") {
      public boolean accepts(@NotNull final VirtualFile virtualFile, final ProcessingContext context) {
        try {
          String tagName = NanoXmlUtil.parseHeaderWithException(virtualFile).getRootTagLocalName();
          return tagName != null && tagNamePattern.getCondition().accepts(tagName, context);
        }
        catch (IOException e) {
          return false;
        }
      }
    });
  }

  protected VirtualFile getParent(@NotNull final VirtualFile t) {
    return t.getParent();
  }

  protected VirtualFile[] getChildren(@NotNull final VirtualFile file) {
    return file.getChildren();
  }
}
