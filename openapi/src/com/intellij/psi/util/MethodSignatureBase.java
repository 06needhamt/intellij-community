/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;

import java.util.Arrays;

public abstract class MethodSignatureBase implements MethodSignature {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.util.MethodSignatureBase");

  private final PsiSubstitutor mySubstitutor;
  protected final PsiType[] myParameterTypes;
  protected final PsiTypeParameter[] myTypeParameters;

  protected MethodSignatureBase(PsiSubstitutor substitutor, PsiType[] parameterTypes, PsiTypeParameter[] typeParameters) {
    mySubstitutor = substitutor;
    if (parameterTypes == null) {
      myParameterTypes = PsiType.EMPTY_ARRAY;
    } else {
      myParameterTypes = new PsiType[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; i++) {
        PsiType type = parameterTypes[i];
        if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
        myParameterTypes[i] = substitutor.substitute(type);
      }
    }
    myTypeParameters = typeParameters == null ? PsiTypeParameter.EMPTY_ARRAY : typeParameters;
  }

  protected MethodSignatureBase(PsiSubstitutor substitutor, PsiParameterList parameterList, PsiTypeParameterList typeParameterList) {
    LOG.assertTrue(substitutor != null);
    mySubstitutor = substitutor;
    if (parameterList != null) {
      final PsiParameter[] parameters = parameterList.getParameters();
      myParameterTypes = new PsiType[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        PsiType type = parameters[i].getType();
        if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
        myParameterTypes[i] = substitutor.substitute(type);
      }
    }
    else {
      myParameterTypes = PsiType.EMPTY_ARRAY;
    }

    myTypeParameters = typeParameterList == null ? PsiTypeParameter.EMPTY_ARRAY : typeParameterList.getTypeParameters();
  }

  public PsiType[] getParameterTypes() {
    return myParameterTypes;
  }

  public PsiTypeParameter[] getTypeParameters() {
    return myTypeParameters;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MethodSignature)) return false;

    final MethodSignature methodSignature = (MethodSignature)o;
    return MethodSignatureUtil.areSignaturesEqual(methodSignature, this);
  }

  public int hashCode() {
    return MethodSignatureUtil.computeHashCode(this);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    String s = "MethodSignature: ";
    final PsiTypeParameter[] typeParameters = getTypeParameters();
    if (typeParameters.length != 0) {
      String sep = "<";
      for (PsiTypeParameter typeParameter : typeParameters) {
        s += sep + typeParameter.getName();
        sep = ", ";
      }
      s += ">";
    }
    s += getName() + "(" + Arrays.asList(getParameterTypes()) + ")";
    return s;
  }

  public PsiSubstitutor getSubstitutor() {
    return mySubstitutor;
  }

}
