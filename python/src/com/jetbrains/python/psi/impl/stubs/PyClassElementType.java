// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.stubs.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author max
 */
public class PyClassElementType extends PyStubElementType<PyClassStub, PyClass> {

  @Nullable
  private List<PyCustomClassStubType> myCustomStubTypes;

  public PyClassElementType() {
    this("CLASS_DECLARATION");
  }

  public PyClassElementType(@NotNull @NonNls String debugName) {
    super(debugName);
  }

  @Override
  @NotNull
  public PsiElement createElement(@NotNull final ASTNode node) {
    return new PyClassImpl(node);
  }

  @Override
  public PyClass createPsi(@NotNull final PyClassStub stub) {
    return new PyClassImpl(stub);
  }

  @Override
  @NotNull
  public PyClassStub createStub(@NotNull final PyClass psi, final StubElement parentStub) {
    final PyCustomClassStub customStub = StreamEx
      .of(getCustomStubTypes())
      .map(type -> type.createStub(psi))
      .findFirst(Objects::nonNull)
      .orElse(null);

    return new PyClassStubImpl(psi.getName(),
                               parentStub,
                               getSuperClassQNames(psi),
                               ContainerUtil.map(getSubscriptedSuperClasses(psi), PsiElement::getText),
                               ContainerUtil.map(psi.getSuperClassExpressions(), PsiElement::getText),
                               PyPsiUtils.asQualifiedName(psi.getMetaClassExpression()),
                               psi.getOwnSlots(),
                               PyPsiUtils.strValue(psi.getDocStringExpression()),
                               getStubElementType(),
                               customStub);
  }

  @NotNull
  public static Map<QualifiedName, QualifiedName> getSuperClassQNames(@NotNull final PyClass pyClass) {
    final Map<QualifiedName, QualifiedName> result = new LinkedHashMap<>();

    for (PyExpression expression : PyClassImpl.getUnfoldedSuperClassExpressions(pyClass)) {
      final QualifiedName importedQName = PyPsiUtils.asQualifiedName(expression);
      final QualifiedName originalQName = resolveOriginalSuperClassQName(expression);

      result.put(importedQName, originalQName);
    }

    return result;
  }

  @NotNull
  private static List<PySubscriptionExpression> getSubscriptedSuperClasses(@NotNull PyClass pyClass) {
    return ContainerUtil.mapNotNull(pyClass.getSuperClassExpressions(), x -> as(x, PySubscriptionExpression.class));
  }

  /**
   * If the class' stub is present, return subscription expressions in the base classes list, converting
   * their saved text chunks into {@link PyExpressionCodeFragment} and extracting top-level expressions
   * from them. Otherwise, get suitable expressions directly from AST, but process them in the same way as
   * if they were going to be saved in the stub.
   */
  @NotNull
  public static List<PySubscriptionExpression> getSubscriptedSuperClassesStubLike(@NotNull PyClass pyClass) {
    final PyClassStub classStub = pyClass.getStub();
    if (classStub == null) {
      return getSubscriptedSuperClasses(pyClass);
    }
    return ContainerUtil.mapNotNull(classStub.getSuperClassesText(),
                                    x -> as(PyUtil.createExpressionFromFragment(x, pyClass.getContainingFile()),
                                            PySubscriptionExpression.class));
  }

  @Nullable
  private static QualifiedName resolveOriginalSuperClassQName(@NotNull PyExpression superClassExpression) {
    if (superClassExpression instanceof PyReferenceExpression) {
      final PyReferenceExpression reference = (PyReferenceExpression)superClassExpression;
      final String referenceName = reference.getName();

      if (referenceName == null) {
        return PyPsiUtils.asQualifiedName(superClassExpression);
      }

      final Optional<QualifiedName> qualifiedName = PyResolveUtil.resolveLocally(reference)
        .stream()
        .filter(PyImportElement.class::isInstance)
        .map(PyImportElement.class::cast)
        .filter(element -> element.getAsName() != null)
        .map(PyImportElement::getImportedQName)
        .findAny();

      if (qualifiedName.isPresent()) {
        return qualifiedName.get();
      }
    }

    return PyPsiUtils.asQualifiedName(superClassExpression);
  }

  @Override
  public void serialize(@NotNull final PyClassStub pyClassStub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(pyClassStub.getName());

    final Map<QualifiedName, QualifiedName> superClasses = pyClassStub.getSuperClasses();
    dataStream.writeByte(superClasses.size());
    for (Map.Entry<QualifiedName, QualifiedName> entry : superClasses.entrySet()) {
      QualifiedName.serialize(entry.getKey(), dataStream);
      QualifiedName.serialize(entry.getValue(), dataStream);
    }

    final List<String> subscriptedBaseClassesText = pyClassStub.getSubscriptedSuperClasses();
    final List<String> baseClassesText = pyClassStub.getSuperClassesText();

    dataStream.writeByte(baseClassesText.size());
    for (String text : baseClassesText) {
      boolean isParametrized = subscriptedBaseClassesText.contains(text);
      dataStream.writeBoolean(isParametrized);
      dataStream.writeName(text);
    }

    QualifiedName.serialize(pyClassStub.getMetaClass(), dataStream);

    PyFileElementType.writeNullableList(dataStream, pyClassStub.getSlots());

    final String docString = pyClassStub.getDocString();
    dataStream.writeUTFFast(docString != null ? docString : "");

    serializeCustomStub(pyClassStub, dataStream);
  }

  private static void serializeCustomStub(@NotNull PyClassStub stub, @NotNull StubOutputStream stream) throws IOException {
    final PyCustomClassStub customStub = stub.getCustomStub(PyCustomClassStub.class);

    final boolean hasCustomStub = customStub != null;
    stream.writeBoolean(hasCustomStub);

    if (hasCustomStub) {
      stream.writeName(customStub.getTypeClass().getCanonicalName());
      customStub.serialize(stream);
    }
  }

  @Override
  @NotNull
  public PyClassStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    final String name = dataStream.readNameString();

    final int superClassCount = dataStream.readByte();
    final Map<QualifiedName, QualifiedName> superClasses = new LinkedHashMap<>();
    for (int i = 0; i < superClassCount; i++) {
      superClasses.put(QualifiedName.deserialize(dataStream), QualifiedName.deserialize(dataStream));
    }

    final byte baseClassesCount = dataStream.readByte();
    final ArrayList<String> parametrizedBaseClasses = new ArrayList<>();
    final ArrayList<String> baseClassesText = new ArrayList<>();
    for (int i = 0; i < baseClassesCount; i++) {
      final boolean isParametrized = dataStream.readBoolean();
      String ref = dataStream.readNameString();
      baseClassesText.add(ref);
      if (ref != null && isParametrized) {
        parametrizedBaseClasses.add(ref);
      }
    }


    final QualifiedName metaClass = QualifiedName.deserialize(dataStream);

    final List<String> slots = PyFileElementType.readNullableList(dataStream);

    final String docStringInStub = dataStream.readUTFFast();
    final String docString = docStringInStub.length() > 0 ? docStringInStub : null;

    final PyCustomClassStub customStub = deserializeCustomStub(dataStream);

    return new PyClassStubImpl(name, parentStub, superClasses, parametrizedBaseClasses, baseClassesText, metaClass, slots, docString,
                               getStubElementType(), customStub);
  }

  @Nullable
  private PyCustomClassStub deserializeCustomStub(@NotNull StubInputStream stream) throws IOException {
    if (stream.readBoolean()) {
      final String typeName = stream.readNameString();
      for (PyCustomClassStubType type : getCustomStubTypes()) {
        if (type.getClass().getCanonicalName().equals(typeName)) {
          return type.deserializeStub(stream);
        }
      }
      throw new IOException("Unknown custom class stub type " + typeName);
    }

    return null;
  }

  @Override
  public void indexStub(@NotNull final PyClassStub stub, @NotNull final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(PyClassNameIndex.KEY, name);
      sink.occurrence(PyClassNameIndexInsensitive.KEY, name.toLowerCase());
    }

    for (String attribute : PyClassAttributesIndex.getAllDeclaredAttributeNames(stub.getPsi())) {
      sink.occurrence(PyClassAttributesIndex.KEY, attribute);
    }

    stub.getSuperClasses().values()
      .stream()
      .filter(Objects::nonNull)
      .map(QualifiedName::getLastComponent)
      .filter(Objects::nonNull)
      .forEach(className -> sink.occurrence(PySuperClassIndex.KEY, className));
  }

  @NotNull
  protected IStubElementType getStubElementType() {
    return PyElementTypes.CLASS_DECLARATION;
  }

  @NotNull
  private List<PyCustomClassStubType> getCustomStubTypes() {
    if (myCustomStubTypes == null) {
      myCustomStubTypes = PyCustomClassStubType.EP_NAME.getExtensionList();
    }
    return myCustomStubTypes;
  }
}