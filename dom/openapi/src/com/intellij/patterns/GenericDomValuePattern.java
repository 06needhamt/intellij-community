/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class GenericDomValuePattern<T> extends DomElementPattern<GenericDomValue<T>, GenericDomValuePattern<T>>{
  private static final InitialPatternCondition CONDITION = new InitialPatternCondition(GenericDomValue.class) {
    public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
      return o instanceof GenericDomValue;
    }
  };

  protected GenericDomValuePattern() {
    super(CONDITION);
  }

  protected GenericDomValuePattern(final Class<T> aClass) {
    super(new InitialPatternCondition(aClass) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof GenericDomValue && aClass.equals(DomUtil.getGenericValueParameter(((GenericDomValue)o).getDomElementType()));
      }

    });
  }

  public GenericDomValuePattern<T> withStringValue(final ElementPattern<String> pattern) {
    return with(new PatternCondition<GenericDomValue<T>>("withStringValue") {
      public boolean accepts(@NotNull final GenericDomValue<T> genericDomValue, final ProcessingContext context) {
        return pattern.getCondition().accepts(genericDomValue.getStringValue(), context);
      }

    });
  }

  public GenericDomValuePattern<T> withValue(@NotNull final T value) {
    return withValue(StandardPatterns.object(value));
  }

  public GenericDomValuePattern<T> withValue(final ElementPattern pattern) {
    return with(new PatternCondition<GenericDomValue<T>>("withValue") {
      public boolean accepts(@NotNull final GenericDomValue<T> genericDomValue, final ProcessingContext context) {
        return pattern.getCondition().accepts(genericDomValue.getValue(), context);
      }
    });
  }
}
