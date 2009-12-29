package com.jetbrains.python.debugger;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.python.debugger.local.PyLocalPositionConverter;
import com.jetbrains.python.debugger.pydev.*;
import org.jetbrains.annotations.NotNull;

import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static javax.swing.SwingUtilities.invokeLater;

/**
 * @author yole
 */
// todo: bundle messages
// todo: pydevd supports module reloading - look for a way to use the feature
// todo: smart step into
public class PyDebugProcess extends XDebugProcess {

  private final PyPositionConverter myPositionConverter = new PyLocalPositionConverter();
  private final RemoteDebugger myDebugger;
  private final XBreakpointHandler[] myBreakpointHandlers;
  private final PyDebuggerEditorsProvider myEditorsProvider;
  private final ProcessHandler myProcessHandler;
  private final ExecutionConsole myExecutionConsole;
  private final Map<PySourcePosition, XLineBreakpoint> myRegisteredBreakpoints = new ConcurrentHashMap<PySourcePosition, XLineBreakpoint>();
  private volatile PyThreadInfo mySuspendedThread = null;

  protected PyDebugProcess(@NotNull XDebugSession session,
                           final ServerSocket serverSocket,
                           final ExecutionConsole executionConsole,
                           final ProcessHandler processHandler) {
    super(session);
    session.setPauseActionSupported(true);  // todo: implement and drop
    myDebugger = new RemoteDebugger(this, serverSocket, 10);
    myBreakpointHandlers = new XBreakpointHandler[]{new PyLineBreakpointHandler(this)};
    myEditorsProvider = new PyDebuggerEditorsProvider();
    myProcessHandler = processHandler;
    myExecutionConsole = executionConsole;
  }

  public PyPositionConverter getPositionConverter() {
    return myPositionConverter;
  }

  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return myBreakpointHandlers;
  }

  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  protected ProcessHandler doGetProcessHandler() {
    return myProcessHandler;
  }

  @NotNull
  public ExecutionConsole createConsole() {
    return myExecutionConsole;
  }

  @Override
  public void sessionInitialized() {
    super.sessionInitialized();
    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Connecting to debugger", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setText("Connecting to debugger...");
        try {
          myDebugger.waitForConnect();
          handshake();
          registerBreakpoints();
          myDebugger.sendCommand(new RunCommand());
        }
        catch (final Exception e) {
          myProcessHandler.destroyProcess();
          invokeLater(new Runnable() {
            public void run() {
              Messages.showErrorDialog("Unable to establish connection with debugger:\n" + e.getMessage(), "Connecting to debugger");
            }
          });
        }
      }
    });
  }

  private void handshake() throws PyDebuggerException {
    final String remoteVersion = myDebugger.handshake();
    ((ConsoleView)myExecutionConsole).print("Connected to pydevd (version " + remoteVersion + ")\n", ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  private void registerBreakpoints() {
    for (Map.Entry<PySourcePosition, XLineBreakpoint> entry : myRegisteredBreakpoints.entrySet()) {
      addBreakpoint(entry.getKey(), entry.getValue());
    }
  }

  public void startStepOver() {
    resume(ResumeCommand.Mode.STEP_OVER);
  }

  public void startStepInto() {
    resume(ResumeCommand.Mode.STEP_INTO);
  }

  public void startStepOut() {
    resume(ResumeCommand.Mode.STEP_OUT);
  }

  public void stop() {
    myDebugger.disconnect();
  }

  public void resume() {
    resume(ResumeCommand.Mode.RESUME);
  }

  private void resume(final ResumeCommand.Mode mode) {
    if (myDebugger.isConnected() && mySuspendedThread != null) {
      final ResumeCommand command = new ResumeCommand(mySuspendedThread.getId(), mode);
      mySuspendedThread = null;
      myDebugger.sendCommand(command);
    }
  }

  public void runToPosition(@NotNull XSourcePosition position) {
    if (myDebugger.isConnected() && mySuspendedThread != null) {
      final PySourcePosition pyPosition = myPositionConverter.convert(position);
      myDebugger.sendCommand(new SetBreakpointCommand(pyPosition.getFile(), pyPosition.getLine()));  // set temp. breakpoint
      resume(ResumeCommand.Mode.RESUME);
    }
  }

  public PyDebugValue evaluate(final String expression) throws PyDebuggerException {
    PyStackFrame frame = currentFrame();
    return myDebugger.evaluate(frame.getThreadId(), frame.getFrameId(), expression);
  }

  public List<PyDebugValue> loadFrame() throws PyDebuggerException {
    PyStackFrame frame = currentFrame();
    return myDebugger.loadFrame(frame.getThreadId(), frame.getFrameId());
  }

  private PyStackFrame currentFrame() throws PyDebuggerException {
    if (!myDebugger.isConnected()) {
      throw new PyDebuggerException("Disconnected");
    }

    final PyStackFrame frame = (PyStackFrame)getSession().getCurrentStackFrame();
    if (frame == null) {
      throw new PyDebuggerException("Process is running");
    }

    return frame;
  }

  public void addBreakpoint(PySourcePosition position, XLineBreakpoint breakpoint) {
    myRegisteredBreakpoints.put(position, breakpoint);
    if (myDebugger.isConnected()) {
      myDebugger.sendCommand(new SetBreakpointCommand(position.getFile(), position.getLine()));
    }
  }

  public void removeBreakpoint(PySourcePosition position) {
    myRegisteredBreakpoints.remove(position);
    if (myDebugger.isConnected()) {
      myDebugger.sendCommand(new RemoveBreakpointCommand(position.getFile(), position.getLine()));
    }
  }

  public Collection<PyThreadInfo> getThreads() {
    return myDebugger.getThreads();
  }

  public void threadSuspended(final PyThreadInfo threadInfo) {
    if (mySuspendedThread != null) {
      // todo: XDebugSession supports only one suspend context 
      final ResumeCommand command = new ResumeCommand(threadInfo.getId(), ResumeCommand.Mode.RESUME);
      myDebugger.sendCommand(command);
      return;
    }
    mySuspendedThread = threadInfo;

    final List<PyStackFrameInfo> frames = threadInfo.getFrames();
    if (frames != null) {
      final PySuspendContext suspendContext = new PySuspendContext(this, threadInfo);

      XLineBreakpoint breakpoint = null;
      if (threadInfo.isStopOnBreakpoint()) {
        final PySourcePosition position = frames.get(0).getPosition();
        breakpoint = myRegisteredBreakpoints.get(position);
        if (breakpoint == null) {
          myDebugger.sendCommand(new RemoveBreakpointCommand(position.getFile(), position.getLine()));  // remove temp. breakpoint
        }
      }

      if (breakpoint != null) {
        if (!getSession().breakpointReached(breakpoint, suspendContext)) {
          resume();
        }
      }
      else {
        getSession().positionReached(suspendContext);
      }
    }
  }

}
