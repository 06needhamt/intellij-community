/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.*;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author peter
 */
public abstract class LightCodeInsightFixtureTestCase extends UsefulTestCase{
  protected CodeInsightTestFixture myFixture;

  protected void setUp() throws Exception {
    super.setUp();

    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  /**
   * Return relative path to the test data.
   *
   * @return relative path to the test data.
   */
  @NonNls
  protected String getBasePath() {
    return "";
  }

  /**
   * Return absolute path to the test data. Not intended to be overrided.
   *
   * @return absolute path to the test data.
   */
  @NonNls
  protected final String getTestDataPath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/') + getBasePath();
  }

  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }

  protected void runTest() throws Throwable {
    new WriteCommandAction(getProject()) {
      protected void run(Result result) throws Throwable {
        LightCodeInsightFixtureTestCase.super.runTest();
      }
    }.execute();
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected PsiManager getPsiManager() {
    return PsiManager.getInstance(getProject());
  }

  public PsiElementFactory getElementFactory() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory();
  }

  protected PsiFile createLightFile(final FileType fileType, final String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText("a." + fileType.getDefaultExtension(), fileType, text);
  }

  public PsiFile createLightFile(final String fileName, final Language language, final String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, language, text, false, true);
  }

}