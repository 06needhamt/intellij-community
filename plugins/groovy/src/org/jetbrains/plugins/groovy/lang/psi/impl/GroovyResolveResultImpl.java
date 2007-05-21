/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;

/**
 * @author ven
 */
public class GroovyResolveResultImpl implements GroovyResolveResult {
  private PsiElement myElement;
  private boolean myIsAccessible;

  public GroovyResolveResultImpl(PsiElement element, boolean isAccessible) {
    myElement = element;
    myIsAccessible = isAccessible;
  }

  public boolean isAccessible() {
    return myIsAccessible;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement;
  }

  public boolean isValidResult() {
    return isAccessible();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GroovyResolveResultImpl that = (GroovyResolveResultImpl) o;

    if (myIsAccessible != that.myIsAccessible) return false;
    if (!myElement.equals(that.myElement)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myElement.hashCode();
    result = 31 * result + (myIsAccessible ? 1 : 0);
    return result;
  }
}
