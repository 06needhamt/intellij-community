/*
 * @author max
 */
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.source.JavaFileElementType;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.cls.ClsFormatException;

public class ClassFileStubBuilder implements BinaryFileStubBuilder {
  public boolean acceptsFile(final VirtualFile file) {
    return !isInner(file.getNameWithoutExtension());
  }

  private static boolean isInner(final String name) {
    return name.indexOf('$') >= 0;
  }

  public StubElement buildStubTree(final VirtualFile file, final byte[] content, final Project project) {
    try {
      return ClsStubBuilder.build(file, content);
    }
    catch (ClsFormatException e) {
      return null;
    }
  }

  public int getStubVersion() {
    return JavaFileElementType.STUB_VERSION;
  }
}