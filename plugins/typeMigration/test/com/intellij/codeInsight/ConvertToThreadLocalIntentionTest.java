/*
 * User: anna
 * Date: 28-Oct-2009
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;

public class ConvertToThreadLocalIntentionTest extends LightQuickFixTestCase {

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  protected String getBasePath() {
    return "/intentions/threadLocal";
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/typeMigration/testData";
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

  public void test() throws Exception {
    doAllTests();
  }
}