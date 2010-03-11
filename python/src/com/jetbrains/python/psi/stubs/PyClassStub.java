/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.PyQualifiedName;

public interface PyClassStub extends NamedStub<PyClass> {
  PyQualifiedName[] getSuperClasses();
}