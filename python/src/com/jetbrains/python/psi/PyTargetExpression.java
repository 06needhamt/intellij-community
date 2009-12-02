/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.07.2005
 * Time: 22:52:10
 * To change this template use File | Settings | File Templates.
 */
public interface PyTargetExpression extends PyQualifiedExpression, PsiNamedElement, StubBasedPsiElement<PyTargetExpressionStub> {
  PyTargetExpression[] EMPTY_ARRAY = new PyTargetExpression[0];
  
  /*
  @Nullable
  PyExpression getQualifier();
  */
/**
 * Find the value that maps to this target expression in an enclosing assignment expression.
 * Does not work with other expressions (e.g. if the target is in a 'for' loop).
 * @return the expression assigned to target via an enclosing assignment expression, or null.
 */
@Nullable
PyExpression findAssignedValue();
}
