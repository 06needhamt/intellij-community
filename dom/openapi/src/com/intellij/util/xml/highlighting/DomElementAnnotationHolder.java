/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public interface DomElementAnnotationHolder extends Iterable<DomElementProblemDescriptor>{

  DomElementProblemDescriptor createProblem(DomElement domElement, @Nullable String message);

  DomElementProblemDescriptor createProblem(DomElement domElement, DomCollectionChildDescription childDescription, @Nullable String message);

  DomElementProblemDescriptor createProblem(DomElement domElement, HighlightSeverity highlightType, String message);

  DomElementResolveProblemDescriptor createResolveProblem(@NotNull GenericDomValue element, @NotNull PsiReference reference);
}
