package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.OutputObjectRegistryImpl;
import com.intellij.rt.execution.junit.segments.Packet;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import junit.framework.*;

public class TestResultsSender implements TestListener, TestSkippingListener {
  private final OutputObjectRegistryImpl myRegistry;
  private final PacketProcessor myErr;
  private final JUnit4API JUnit4API;
  private TestMeter myCurrentTestMeter;
  private Test myCurrentTest;

  public TestResultsSender(OutputObjectRegistryImpl packetFactory, PacketProcessor segmentedErr, final JUnit4API isJunit4) {
    myRegistry = packetFactory;
    myErr = segmentedErr;
    JUnit4API = isJunit4;
  }

  public synchronized void addError(Test test, Throwable throwable) {
    if (throwable instanceof AssertionError) {
      // junit4 makes no distinction between errors and failures
      doAddFailure(test, (Error)throwable);
    }
    else if (JUnit4API != null && JUnit4API.isTestIgnored(throwable)) {
      startTest(test);
      stopMeter(test);
      prepareIgnoredPacket(test).send();
    }
    else {
      stopMeter(test);
      prepareDefectPacket(test, throwable).send();
    }
  }

  public synchronized void addFailure(Test test, AssertionFailedError assertion) {
    doAddFailure(test, assertion);
  }

  private void doAddFailure(final Test test, final Error assertion) {
    stopMeter(test);
    createExceptionNotification(assertion).createPacket(myRegistry, test).send();
  }

  private static PacketFactory createExceptionNotification(Error assertion) {
    if (assertion instanceof KnownException) return ((KnownException)assertion).getPacketFactory();
    if (assertion instanceof ComparisonFailure || assertion.getClass().getName().equals("org.junit.ComparisonFailure")) {
      return ComparisonDetailsExtractor.create(assertion);
    }
    return new ExceptionPacketFactory(PoolOfTestStates.FAILED_INDEX, assertion);
  }

  private Packet prepareDefectPacket(Test test, Throwable assertion) {
    return myRegistry.createPacket().
            setTestState(test, PoolOfTestStates.ERROR_INDEX).
            addThrowable(assertion);
  }
  private Packet prepareIgnoredPacket(Test test) {
    return myRegistry.createPacket().setTestState(test, PoolOfTestStates.IGNORED_INDEX).addObject(test);
  }

  public synchronized void endTest(Test test) {
    stopMeter(test);
    Packet packet = myRegistry.createPacket().setTestState(test, PoolOfTestStates.COMPLETE_INDEX);
    myCurrentTestMeter.writeTo(packet);
    packet.send();
    myRegistry.forget(test);
  }

  private void stopMeter(Test test) {
    if (!test.equals(myCurrentTest)) {
      myCurrentTestMeter = new TestMeter();
      //noinspection HardCodedStringLiteral
      System.err.println("Wrong test finished. Last started: " + myCurrentTest+" stopped: " + test+"; "+test.getClass());
    }
    myCurrentTestMeter.stop();
  }

  private void switchOutput(Packet switchPacket) {
    switchPacket.send();
    switchPacket.sendThrough(myErr);
  }

  public synchronized void startTest(Test test) {
    myCurrentTest = test;
    myRegistry.createPacket().setTestState(test, PoolOfTestStates.RUNNING_INDEX).send();
    switchOutput(myRegistry.createPacket().switchInputTo(test));
    myCurrentTestMeter = new TestMeter();
  }

  public synchronized  void onTestSkipped(TestCase test, Test peformedTest) {
    myRegistry.createPacket().
        setTestState(test, PoolOfTestStates.SKIPPED_INDEX).
        addObject(peformedTest).
        send();
  }
}
