/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.lookup;

/**
 * @author Dmitry Avdeev
 * @deprecated use LookupElementBuilder
 */
public interface LookupValueWithTail {
  String getTailText();
}
