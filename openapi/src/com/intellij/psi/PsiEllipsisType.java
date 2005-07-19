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
package com.intellij.psi;


/**
 * @author ven
 */
public class PsiEllipsisType extends PsiArrayType {
  public PsiEllipsisType(PsiType componentType) {
    super(componentType);
  }

  public String getPresentableText() {
    return getComponentType().getPresentableText() + "...";
  }

  public String getCanonicalText() {
    return getComponentType().getCanonicalText() + "...";
  }

  public boolean equalsToText(String text) {
    return (text.endsWith("...") && getComponentType().equalsToText(text.substring(0, text.length() - 3))) ||
           super.equalsToText(text);
  }

  public PsiType toArrayType() {
    return getComponentType().createArrayType();
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitEllipsisType(this);
  }

  public boolean equals(Object obj) {
    return obj instanceof PsiEllipsisType && super.equals(obj);
  }

  public int hashCode() {
    return super.hashCode() * 5;
  }
}
