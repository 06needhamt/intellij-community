/*
 * @author max
 */
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyStubElementType;
import com.jetbrains.python.psi.impl.PyNamedParameterImpl;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;

import java.io.IOException;

public class PyNamedParameterElementType extends PyStubElementType<PyNamedParameterStub, PyNamedParameter> {
  private static final int POSITIONAL_CONTAINER = 1;
  private static final int KEYWORD_CONTAINER = 2;
  private static final int HAS_DEFAULT_VALUE = 4;

  public PyNamedParameterElementType() {
    super("NAMED_PARAMETER");
  }

  public PyNamedParameter createPsi(final PyNamedParameterStub stub) {
    return new PyNamedParameterImpl(stub);
  }

  public PyNamedParameterStub createStub(final PyNamedParameter psi, final StubElement parentStub) {
    return new PyNamedParameterStubImpl(psi.getName(), psi.isPositionalContainer(), psi.isKeywordContainer(), psi.hasDefaultValue(), parentStub);
  }

  public PsiElement createElement(final ASTNode node) {
    return new PyNamedParameterImpl(node);
  }

  public void serialize(final PyNamedParameterStub stub, final StubOutputStream dataStream)
      throws IOException {
    dataStream.writeName(stub.getName());

    byte flags = 0;
    if (stub.isPositionalContainer()) flags |= POSITIONAL_CONTAINER;
    if (stub.isKeywordContainer()) flags |= KEYWORD_CONTAINER;
    if (stub.hasDefaultValue()) flags |= HAS_DEFAULT_VALUE;
    dataStream.writeByte(flags);
  }

  public PyNamedParameterStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    byte flags = dataStream.readByte();
    return new PyNamedParameterStubImpl(name,
                                        (flags & POSITIONAL_CONTAINER) != 0,
                                        (flags & KEYWORD_CONTAINER) != 0,
                                        (flags & HAS_DEFAULT_VALUE) != 0,
                                        parentStub);
  }
}