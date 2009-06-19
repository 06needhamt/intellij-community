/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author peter
 */
public abstract class LookupElement extends UserDataHolderBase {
  public static final LookupElement[] EMPTY_ARRAY = new LookupElement[0];
  private final NotNullLazyValue<LookupElementRenderer> myRenderer = new NotNullLazyValue<LookupElementRenderer>() {
    @NotNull
    protected LookupElementRenderer compute() {
      return getRenderer();
    }
  };
  private PrefixMatcher myPrefixMatcher = PrefixMatcher.FALSE_MATCHER;

  @NotNull
  public abstract String getLookupString();

  public Set<String> getAllLookupStrings() {
    return Collections.singleton(getLookupString());
  }

  public boolean setPrefixMatcher(@NotNull final PrefixMatcher matcher) {
    myPrefixMatcher = matcher;
    return isPrefixMatched();
  }

  public final boolean isPrefixMatched() {
    return myPrefixMatcher.prefixMatches(this);
  }

  @NotNull
  public final PrefixMatcher getPrefixMatcher() {
    return myPrefixMatcher;
  }

  @NotNull
  public Object getObject() {
    return this;
  }

  public abstract InsertHandler<? extends LookupElement> getInsertHandler();

  public void handleInsert(InsertionContext context) {
    final InsertHandler<? extends LookupElement> handler = getInsertHandler();
    if (handler != null) {
      //noinspection unchecked
      ((InsertHandler)handler).handleInsert(context, this);
    }
  }

  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.SETTINGS_DEPENDENT;
  }

  @Override
  public String toString() {
    return getLookupString();
  }

  @NotNull
  protected abstract LookupElementRenderer<? extends LookupElement> getRenderer();

  public void renderElement(LookupElementPresentation presentation) {
    //noinspection unchecked
    myRenderer.getValue().renderElement(this, presentation);
  }

  public int getGrouping() {
    return 0;
  }
}
