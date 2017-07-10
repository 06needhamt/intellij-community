/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.lang.jvm;

import com.intellij.lang.jvm.types.JvmClassType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JvmClass extends JvmTypeParametersOwner, JvmTypeDeclarator {

  @NotNull
  @NonNls
  String getQualifiedName();

  @NotNull
  JvmClassKind classKind();

  @Nullable
  JvmClassType superClassType();

  @NotNull
  Iterable<JvmClassType> interfaceTypes();

  //

  @NotNull
  JvmConstructor[] getConstructors();

  @NotNull
  JvmMethod[] getMethods();

  @NotNull
  JvmField[] getFields();

  @NotNull
  JvmClass[] getInnerClasses();
}
