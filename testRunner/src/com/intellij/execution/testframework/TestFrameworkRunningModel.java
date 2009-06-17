/*
 * User: anna
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.openapi.Disposable;

public interface TestFrameworkRunningModel extends Disposable {
  TestConsoleProperties getProperties();

  void setFilter(final Filter filter);

  boolean isRunning();

  TestTreeView getTreeView();

  boolean hasTestSuites();

  AbstractTestProxy getRoot();

  void selectAndNotify(final AbstractTestProxy testProxy);
}