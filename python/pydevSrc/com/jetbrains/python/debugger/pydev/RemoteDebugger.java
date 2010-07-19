/*
 * Author: atotic
 * Created on Mar 23, 2004
 * License: Common Public License v1.0
 */
package com.jetbrains.python.debugger.pydev;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyThreadInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class RemoteDebugger {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.remote.RemoteDebugger");

  private static final String LOCAL_VERSION = "0.1";
  public static final String TEMP_VAR_PREFIX = "__py_debug_temp_var_";

  private static final SecureRandom ourRandom = new SecureRandom();

  private final IPyDebugProcess myDebugProcess;
  private final ServerSocket myServerSocket;
  private final int myTimeout;
  private Socket mySocket;
  private volatile boolean myConnected = false;
  private int mySequence = -1;
  private final Map<String, PyThreadInfo> myThreads = new ConcurrentHashMap<String, PyThreadInfo>();
  private final Map<Integer, ProtocolFrame> myResponseQueue = new HashMap<Integer, ProtocolFrame>();
  private final TempVarsHolder myTempVars = new TempVarsHolder();

  public RemoteDebugger(final IPyDebugProcess debugProcess, final ServerSocket serverSocket, final int timeout) {
    myDebugProcess = debugProcess;
    myServerSocket = serverSocket;
    myTimeout = timeout * 1000;  // to milliseconds
  }

  public IPyDebugProcess getDebugProcess() {
    return myDebugProcess;
  }

  public boolean isConnected() {
    return myConnected;
  }

  public void waitForConnect() throws Exception {
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      mySocket = myServerSocket.accept();
    }
    finally {
      myServerSocket.close();
    }

    try {
      final DebuggerReader reader = new DebuggerReader();
      ApplicationManager.getApplication().executeOnPooledThread(reader);
    }
    catch (Exception e) {
      mySocket.close();
      throw e;
    }

    myConnected = true;
  }

  public void disconnect() {
    myConnected = false;

    if (mySocket != null && !mySocket.isClosed()) {
      try {
        mySocket.close();
      }
      catch (IOException ignore) {
      }
      mySocket = null;
    }
  }

  public String handshake() throws PyDebuggerException {
    final VersionCommand command = new VersionCommand(this, LOCAL_VERSION);
    command.execute();
    return command.getRemoteVersion();
  }

  public PyDebugValue evaluate(final String threadId, final String frameId, final String expression, final boolean execute) throws PyDebuggerException {
    final EvaluateCommand command = new EvaluateCommand(this, threadId, frameId, expression, execute);
    command.execute();
    return command.getValue();
  }

  public List<PyDebugValue> loadFrame(final String threadId, final String frameId) throws PyDebuggerException {
    final GetFrameCommand command = new GetFrameCommand(this, threadId, frameId);
    command.execute();
    return command.getVariables();
  }

  // todo: don't generate temp variables for qualified expressions - just split 'em
  public List<PyDebugValue> loadVariable(final String threadId, final String frameId, final PyDebugValue var) throws PyDebuggerException {
    setTempVariable(threadId, frameId, var);
    final GetVariableCommand command = new GetVariableCommand(this, threadId, frameId, composeName(var), var);
    command.execute();
    return command.getVariables();
  }

  public void changeVariable(final String threadId, final String frameId, final PyDebugValue var, final String value)
      throws PyDebuggerException {
    setTempVariable(threadId, frameId, var);
    doChangeVariable(threadId, frameId, var.getEvaluationExpression(), value);
  }

  private void doChangeVariable(final String threadId, final String frameId, final String varName, final String value)
      throws PyDebuggerException {
    final ChangeVariableCommand command = new ChangeVariableCommand(this, threadId, frameId, varName, value);
    command.execute();
  }

  private static String composeName(final PyDebugValue var) {
    final StringBuilder sb = new StringBuilder(var.getTempName());
    PyDebugValue p = var;
    while ((p = p.getParent()) != null) {
      sb.insert(0, '\t').insert(0, p.getTempName());
    }
    return sb.toString();
  }

  // todo: change variable in lists doesn't work - either fix in pydevd or format var name appropriately
  private void setTempVariable(final String threadId, final String frameId, final PyDebugValue var) {
    final PyDebugValue topVar = var.getTopParent();
    if (myDebugProcess.isVariable(topVar.getName())) {
      return;
    }
    if (myTempVars.contains(threadId, frameId, topVar.getTempName())) {
      return;
    }

    topVar.setTempName(generateTempName());
    try {
      doChangeVariable(threadId, frameId, topVar.getTempName(), topVar.getName());
      myTempVars.put(threadId, frameId, topVar.getTempName());
    }
    catch (PyDebuggerException e) {
      LOG.error(e);
      topVar.setTempName(null);
    }
  }

  private void clearTempVariables(final String threadId) {
    final Map<String, Set<String>> threadVars = myTempVars.get(threadId);
    if (threadVars == null || threadVars.size() == 0) return;

    for (Map.Entry<String, Set<String>> entry : threadVars.entrySet()) {
      final Set<String> frameVars = entry.getValue();
      if (frameVars == null || frameVars.size() == 0) continue;

      final String expression = "del " + StringUtil.join(frameVars, ",");
      try {
        evaluate(threadId, entry.getKey(), expression, true);
      }
      catch (PyDebuggerException e) {
        LOG.error(e);
      }
    }

    myTempVars.clear(threadId);
  }

  private static String generateTempName() {
    return new StringBuilder(32).append(TEMP_VAR_PREFIX).append(ourRandom.nextInt(Integer.MAX_VALUE)).toString();
  }

  public Collection<PyThreadInfo> getThreads() {
    return Collections.unmodifiableCollection(new ArrayList<PyThreadInfo>(myThreads.values()));
  }

  int getNextSequence() {
    mySequence += 2;
    return mySequence;
  }

  void placeResponse(final int sequence, final ProtocolFrame response) {
    synchronized (myResponseQueue) {
      if (response == null || myResponseQueue.containsKey(sequence)) {
        myResponseQueue.put(sequence, response);
      }
      if (response != null) {
        myResponseQueue.notifyAll();
      }
    }
  }

  @Nullable
  ProtocolFrame waitForResponse(final int sequence) {
    ProtocolFrame response;
    long until = System.currentTimeMillis() + myTimeout;

    synchronized (myResponseQueue) {
      do {
        try {
          myResponseQueue.wait(1000);
        }
        catch (InterruptedException ignore) { }
        response = myResponseQueue.get(sequence);
      }
      while (response == null && System.currentTimeMillis() < until);
      myResponseQueue.remove(sequence);
    }

    return response;
  }

  public void execute(@NotNull final AbstractCommand command) {
    if (command instanceof ResumeCommand) {
      final String threadId = ((ResumeCommand)command).getThreadId();
      clearTempVariables(threadId);
    }

    try {
      command.execute();
    }
    catch (PyDebuggerException e) {
      LOG.error(e);
    }
  }

  void sendFrame(final ProtocolFrame frame) {
    logFrame(frame, true);

    try {
      final byte[] packed = frame.pack();
      final OutputStream os = mySocket.getOutputStream();
      os.write(packed);
      os.write('\n');
      os.flush();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  // todo: drop
  private static void logFrame(ProtocolFrame frame, boolean out) {
    System.out.format("%1$tH:%1$tM:%1$tS.%1$tL %2$s %3$s\n", new Date(), (out ? "<<<" : ">>>"), frame);
  }


  private class DebuggerReader implements Runnable {
    private final InputStream myInputStream;

    private DebuggerReader() throws IOException {
      this.myInputStream = mySocket.getInputStream();
    }

    public void run() {
      final BufferedReader reader = new BufferedReader(new InputStreamReader(myInputStream, Charset.forName("UTF-8")));
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          processResponse(line);
        }
      }
      catch (SocketException ignore) {
        // disconnected
      }
      catch (Exception e) {
        LOG.error(e);
      }
      finally {
        closeReader(reader);
      }
    }

    private void processResponse(final String line) {
      try {
        final ProtocolFrame frame = new ProtocolFrame(line);
        logFrame(frame, false);

        if (frame.getCommand() == AbstractCommand.CREATE_THREAD ||
            frame.getCommand() == AbstractCommand.KILL_THREAD ||
            frame.getCommand() == AbstractCommand.RESUME_THREAD ||
            frame.getCommand() == AbstractCommand.SUSPEND_THREAD) {
          processThreadEvent(frame);
        }
        else {
          placeResponse(frame.getSequence(), frame);
        }
      }
      catch (Throwable t) {
        // shouldn't interrupt reader thread
        LOG.error(t);
      }
    }

    // todo: extract response processing
    private void processThreadEvent(ProtocolFrame frame) throws PyDebuggerException {
      switch (frame.getCommand()) {
        case AbstractCommand.CREATE_THREAD: {
          final PyThreadInfo thread = ProtocolParser.parseThread(frame.getPayload(), myDebugProcess.getPositionConverter());
          if (!thread.getId().equals("-1")) {  // ignore pydevd threads
            myThreads.put(thread.getId(), thread);
          }
          break;
        }
        case AbstractCommand.SUSPEND_THREAD: {
          final PyThreadInfo event = ProtocolParser.parseThread(frame.getPayload(), myDebugProcess.getPositionConverter());
          final PyThreadInfo thread = myThreads.get(event.getId());
          if (thread != null) {
            thread.updateState(PyThreadInfo.State.SUSPENDED, event.getFrames());
            myDebugProcess.threadSuspended(thread);
          }
          break;
        }
        case AbstractCommand.RESUME_THREAD: {
          final String id = frame.getPayload().split("\t")[0];
          final PyThreadInfo thread = myThreads.get(id);
          if (thread != null) {
            thread.updateState(PyThreadInfo.State.RUNNING, null);
          }
          break;
        }
        case AbstractCommand.KILL_THREAD: {
          final String id = frame.getPayload();
          final PyThreadInfo thread = myThreads.get(id);
          if (thread != null) {
            thread.updateState(PyThreadInfo.State.KILLED, null);
            myThreads.remove(id);
          }
          break;
        }
      }
    }

    private void closeReader(BufferedReader reader) {
      try {
        reader.close();
      }
      catch (IOException ignore) {
      }
    }
  }


  private static class TempVarsHolder {
    private final Map<String, Map<String, Set<String>>> myData = new HashMap<String, Map<String, Set<String>>>();

    public boolean contains(final String threadId, final String frameId, final String name) {
      final Map<String, Set<String>> threadVars = myData.get(threadId);
      if (threadVars == null) return false;

      final Set<String> frameVars = threadVars.get(frameId);
      if (frameVars == null) return false;

      return frameVars.contains(name);
    }

    private void put(final String threadId, final String frameId, final String name) {
      Map<String, Set<String>> threadVars = myData.get(threadId);
      if (threadVars == null) myData.put(threadId, (threadVars = new HashMap<String, Set<String>>()));

      Set<String> frameVars = threadVars.get(frameId);
      if (frameVars == null) threadVars.put(frameId, (frameVars = new HashSet<String>()));

      frameVars.add(name);
    }

    private Map<String, Set<String>> get(final String threadId) {
      return myData.get(threadId);
    }

    private void clear(final String threadId) {
      final Map<String, Set<String>> threadVars = myData.get(threadId);
      if (threadVars != null) {
        threadVars.clear();
      }
    }
  }

}
