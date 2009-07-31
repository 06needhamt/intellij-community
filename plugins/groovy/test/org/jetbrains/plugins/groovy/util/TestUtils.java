/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.util;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class, that contains various methods for testing
 *
 * @author Ilya.Sergey
 */
public abstract class TestUtils {
  public static final String TEMP_FILE = "temp.groovy";
  public static final String GSP_TEMP_FILE = "temp.gsp";
  public static final String CARET_MARKER = "<caret>";
  public static final String BEGIN_MARKER = "<begin>";
  public static final String END_MARKER = "<end>";
  public static final String GRAILS_JAR = "grails-web.jar";
  public static final String GROOVY_JAR = "groovy-all.jar";

  public static String getMockJdkHome() {
    return getTestDataPath() + "/mockJDK";
  }

  public static String getMockGroovyLibraryHome() {
    return getTestDataPath() + "/mockGroovyLib";
  }

  public static PsiFile createPseudoPhysicalGroovyFile(final Project project, final String text) throws IncorrectOperationException {
    return createPseudoPhysicalFile(project, TEMP_FILE, text);
  }


  public static PsiFile createPseudoPhysicalFile(final Project project, final String fileName, final String text) throws IncorrectOperationException {
    return PsiFileFactory.getInstance(project).createFileFromText(
        fileName,
        FileTypeManager.getInstance().getFileTypeByFileName(fileName),
        text,
        LocalTimeCounter.currentTime(),
        true);
  }

  private static @NonNls String TEST_DATA_PATH = null;

  public static String getTestDataPath() {
    if (TEST_DATA_PATH == null) {
      TEST_DATA_PATH = FileUtil.toSystemIndependentName(PathManager.getHomePath()) + "/svnPlugins/groovy/testdata";
    }
    return TEST_DATA_PATH;
  }

  /**
   * Removes CARET_MARKER from file text
   *
   * @param text
   * @return
   */
  public static String removeCaretMarker(String text) {
    int index = text.indexOf(CARET_MARKER);
    return text.substring(0, index) + text.substring(index + CARET_MARKER.length());
  }

  public static String removeBeginMarker(String text) {
    int index = text.indexOf(BEGIN_MARKER);
    return text.substring(0, index) + text.substring(index + BEGIN_MARKER.length());
  }

  public static String removeEndMarker(String text) {
    int index = text.indexOf(END_MARKER);
    return text.substring(0, index) + text.substring(index + END_MARKER.length());
  }

  public static String[] getInputAndResult(File myTestFile) throws IOException {
    String content = new String(FileUtil.loadFileText(myTestFile));
    Assert.assertNotNull(content);

    List<String> input = new ArrayList<String>();

    int separatorIndex;
    content = StringUtil.replace(content, "\r", ""); // for MACs

    // Adding input  before -----
    while ((separatorIndex = content.indexOf("-----")) >= 0) {
      input.add(content.substring(0, separatorIndex - 1));
      content = content.substring(separatorIndex);
      while (StringUtil.startsWithChar(content, '-') ||
          StringUtil.startsWithChar(content, '\n')) {
        content = content.substring(1);
      }
    }
    // Result - after -----
    String result = content;
    while (StringUtil.startsWithChar(result, '-') ||
        StringUtil.startsWithChar(result, '\n') ||
        StringUtil.startsWithChar(result, '\r')) {
      result = result.substring(1);
    }

    Assert.assertTrue("No data found in source file", input.size() > 0);
    Assert.assertNotNull(result);
    Assert.assertNotNull(input);

    String inputString = input.toArray(new String[input.size()])[0];
    return new String[]{inputString, result};
  }
}
