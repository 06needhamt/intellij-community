/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.CodeInsightBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author peter
 */
public abstract class ResolvingConverter<T> extends Converter<T> {
  public static final ResolvingConverter EMPTY_CONVERTER = new ResolvingConverter() {
    public Collection getVariants(final ConvertContext context) {
      return Collections.emptyList();
    }

    public Object fromString(final String s, final ConvertContext context) {
      return s;
    }

    public String toString(final Object t, final ConvertContext context) {
      return String.valueOf(t);
    }
  };

  public static final Converter<Boolean> BOOLEAN_CONVERTER = new ResolvingConverter<Boolean>() {
    public Boolean fromString(final String s, final ConvertContext context) {
      if ("true".equalsIgnoreCase(s)) {
        return Boolean.TRUE;
      }
      if ("false".equalsIgnoreCase(s)) {
        return Boolean.FALSE;
      }
      return null;
    }

    public String toString(final Boolean t, final ConvertContext context) {
      return t == null? null:t.toString();
    }

    @NotNull
    public Collection<? extends Boolean> getVariants(final ConvertContext context) {
      return Arrays.asList(Boolean.FALSE, Boolean.TRUE);
    }
  };

  public String getErrorMessage(@Nullable String s, final ConvertContext context) {
    return CodeInsightBundle.message("error.cannot.resolve.default.message", s);
  }

  @NotNull
  public abstract Collection<? extends T> getVariants(final ConvertContext context);

  public PsiElement getPsiElement(T resolvedValue) {
    if (resolvedValue instanceof PsiElement) {
      return (PsiElement)resolvedValue;
    }
    if (resolvedValue instanceof DomElement) {
      return ((DomElement)resolvedValue).getXmlElement();
    }
    return null;
  }

  public static abstract class WrappedResolvingConverter<T> extends ResolvingConverter<T> {

    private final Converter<T> myWrappedConverter;

    public WrappedResolvingConverter(Converter<T> converter) {

      myWrappedConverter = converter;
    }

    public T fromString(final String s, final ConvertContext context) {
      return myWrappedConverter.fromString(s, context);
    }

    public String toString(final T t, final ConvertContext context) {
      return myWrappedConverter.toString(t, context);
    }
  }
}
