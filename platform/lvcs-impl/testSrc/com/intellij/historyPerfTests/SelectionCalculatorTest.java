package com.intellij.historyPerfTests;

import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.ui.models.NullProgress;
import com.intellij.history.integration.ui.models.SelectionCalculator;
import com.intellij.history.integration.TestIdeaGateway;
import com.intellij.history.utils.RunnableAdapter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

@Ignore
public class SelectionCalculatorTest extends LocalVcsPerformanceTestCase {
  private List<Revision> rr;
  private SelectionCalculator c;

  @Before
  public void setUp() {
    buildVcsTree();
    for (int i = 0; i < 100; i++) {
      vcs.changeFileContent("root/file1", cf("content" + i), -1);
    }
    rr = vcs.getRevisionsFor("root/file1");
    c = new SelectionCalculator(new TestIdeaGateway(), rr, 0, 0);
  }

  @Test
  public void testCalculationFromTheScratch() {
    assertExecutionTime(7800, new RunnableAdapter() {
      public void doRun() throws Exception {
        c.getSelectionFor(rr.get(100), new NullProgress());
      }
    });
  }

  @Test
  public void testUsingCache() {
    c.getSelectionFor(rr.get(100), new NullProgress());

    assertExecutionTime(1, new RunnableAdapter() {
      public void doRun() throws Exception {
        c.getSelectionFor(rr.get(1), new NullProgress());
        c.getSelectionFor(rr.get(50), new NullProgress());
        c.getSelectionFor(rr.get(100), new NullProgress());
      }
    });
  }
}
