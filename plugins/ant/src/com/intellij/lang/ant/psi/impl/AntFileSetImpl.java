/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: May 4, 2007
 */
public class AntFileSetImpl extends AntFilesProviderImpl{
  public AntFileSetImpl(final AntElement parent, final XmlTag sourceElement) {
    super(parent, sourceElement);
  }

  public AntFileSetImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition, @NonNls final String nameElementAttribute) {
    super(parent, sourceElement, definition, nameElementAttribute);
  }

  public AntFileSetImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  @NotNull
  protected List<File> getFiles(final AntPattern pattern) {
    final File singleFile = getCanonicalFile(computeAttributeValue(getSourceElement().getAttributeValue("file")));
    if (singleFile == null || pattern.hasIncludePatterns()) {
      // if singleFile is specified, there are no implicit includes
      final File root = getCanonicalFile(computeAttributeValue(getSourceElement().getAttributeValue("dir")));
      if (root != null) {
        final ArrayList<File> files = new ArrayList<File>();
        if (singleFile != null) {
          files.add(singleFile);
        }
        new FilesCollector().collectFiles(files, root, "", pattern);
        return files;
      }
    }
    if (singleFile != null) {
      return Collections.singletonList(singleFile);
    }
    return Collections.emptyList();
  }
  
  private static class FilesCollector {
    private static final int MAX_DIRS_TO_PROCESS = 100;
    private int myDirsProcessed = 0;
    private boolean myDirCheckEnabled = false;
    
    public void collectFiles(List<File> container, File from, String relativePath, final AntPattern pattern) {
      if (myDirsProcessed > MAX_DIRS_TO_PROCESS) {
        return;
      }
      final File[] children = from.listFiles();
      if (children != null && children.length > 0) {
        if (myDirCheckEnabled) {
          if (!pattern.couldBeIncluded(relativePath)) {
            return;
          }
        }
        else {
          myDirCheckEnabled = true;
        }                                        
        myDirsProcessed++;
        for (File child : children) {
          final String childPath = makePath(relativePath, child.getName());
          if (pattern.acceptPath(childPath)) {
            container.add(child);
          }
          collectFiles(container, child, childPath, pattern);
        }
      }
    }

    private static String makePath(final String parentPath, final String name) {
      if (parentPath.length() == 0) {
        return name;
      }
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        return builder.append(parentPath).append("/").append(name).toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }
    
  }
  
}
