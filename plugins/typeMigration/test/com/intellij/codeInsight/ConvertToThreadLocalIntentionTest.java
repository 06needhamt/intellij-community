/*
 * User: anna
 * Date: 28-Oct-2009
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

public class ConvertToThreadLocalIntentionTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return "/intentions/threadLocal";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/typeMigration/testData";
  }

  public void test() throws Exception {
    doAllTests();
  }
}