/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.testFramework.fixtures;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

/**
 * @author peter
 */
public interface HeavyIdeaTestFixture extends IdeaProjectTestFixture {
  PsiFile addFileToProject(@NonNls String rootPath, @NonNls String relativePath, @NonNls String fileText) throws IOException;
}
