/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PySuperClassIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PyClassElementType extends PyStubElementType<PyClassStub, PyClass> {
  public PyClassElementType() {
    super("CLASS_DECLARATION");
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyClassImpl(node);
  }

  public PyClass createPsi(final PyClassStub stub) {
    return new PyClassImpl(stub);
  }

  public PyClassStub createStub(final PyClass psi, final StubElement parentStub) {
    final PyExpression[] exprs = psi.getSuperClassExpressions();
    List<String> superClasses = new ArrayList<String>();
    for (final PyExpression expression : exprs) {
      if (expression instanceof PyReferenceExpression) {
        final String referencedName = ((PyReferenceExpression)expression).getReferencedName();
        if (referencedName != null) {
          superClasses.add(referencedName);
        }
      }
    }
    return new PyClassStubImpl(psi.getName(), parentStub, ArrayUtil.toStringArray(superClasses));
  }

  public void serialize(final PyClassStub pyClassStub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(pyClassStub.getName());
    final String[] classes = pyClassStub.getSuperClasses();
    dataStream.writeByte(classes.length);
    for(String s: classes) {
      dataStream.writeName(s);
    }
  }

  public PyClassStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    int superClassCount = dataStream.readByte();
    String[] superClasses = new String[superClassCount];
    for(int i=0; i<superClassCount; i++) {
      superClasses[i] = StringRef.toString(dataStream.readName());
    }
    return new PyClassStubImpl(name, parentStub, superClasses);
  }

  public void indexStub(final PyClassStub stub, final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(PyClassNameIndex.KEY, name);
    }
    for(String s: stub.getSuperClasses()) {
      sink.occurrence(PySuperClassIndex.KEY, s);
    }
  }
}