package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.NonNls;

/**
 * @author Roman Chernyatchik
 */
public abstract class SuiteFinishedState extends AbstractState {
  @NonNls private static final String EMPTY_SUITE_TEXT = SMTestsRunnerBundle.message("sm.test.runner.states.suite.is.empty");

  //This states are common for all instances and doesn't contains
  //instance-specific information

  public static SuiteFinishedState PASSED_SUITE = new SuiteFinishedState() {
    public Magnitude getMagnitude() {
      return Magnitude.PASSED_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "SUITE PASSED";
    }
  };
  public static SuiteFinishedState FAILED_SUITE = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      return true;
    }

    public Magnitude getMagnitude() {
      return Magnitude.FAILED_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "FAILED SUITE";
    }
  };

  public static SuiteFinishedState WITH_IGNORED_TESTS_SUITE = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      return true;
    }

    public Magnitude getMagnitude() {
      return Magnitude.IGNORED_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "WITH IGNORED TESTS SUITE";
    }
  };

  public static SuiteFinishedState ERROR_SUITE = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      return true;
    }

    public Magnitude getMagnitude() {
      return Magnitude.ERROR_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "ERROR SUITE";
    }
  };

  /**
   * Finished empty test suite
   */
  public static SuiteFinishedState EMPTY_SUITE = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      return false;
    }

    @Override
    public void printOn(final Printer printer) {
      super.printOn(printer);

      final String msg = EMPTY_SUITE_TEXT + PrintableTestProxy.NEW_LINE;
      printer.print(msg, ConsoleViewContentType.SYSTEM_OUTPUT);
    }


    public Magnitude getMagnitude() {
      return Magnitude.COMPLETE_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "EMPTY FINISHED SUITE";
    }
  };

  private SuiteFinishedState() {
  }

  public boolean isInProgress() {
    return false;
  }

  public boolean isDefect() {
    return false;
  }

  public boolean wasLaunched() {
    return true;
  }

  public boolean isFinal() {
    return true;
  }

  public boolean wasTerminated() {
    return false;
  }
}
