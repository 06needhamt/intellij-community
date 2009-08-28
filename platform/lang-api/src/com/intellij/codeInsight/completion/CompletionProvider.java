/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.util.ProcessingContext;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class CompletionProvider<V extends CompletionParameters> {
  private final boolean myStartInReadAction;

  protected CompletionProvider() {
    this(true);
  }

  protected CompletionProvider(final boolean startInReadAction) {
    myStartInReadAction = startInReadAction;
  }

  protected abstract void addCompletions(@NotNull V parameters, final ProcessingContext context, @NotNull CompletionResultSet result);

  public final void addCompletionVariants(@NotNull final V parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
    if (myStartInReadAction) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          addCompletions(parameters, context, result);
        }
      });
    } else {
      addCompletions(parameters, context, result);
    }
  }
}
