// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ArrayUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties
import java.util.*

/**
 * @author egor
 */
internal abstract class BreakpointIntentionAction(protected val myBreakpoint: XBreakpoint<*>, text: String) : AnAction(text) {

  internal class AddCallerNotFilter(breakpoint: XBreakpoint<*>, private val myCaller: String) :
    BreakpointIntentionAction(breakpoint, "Do not stop if caller is: $myCaller") {

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isCALLER_FILTERS_ENABLED = true
        callerFilters = ArrayUtil.remove(callerFilters, myCaller)
        callerExclusionFilters = ArrayUtil.append(callerExclusionFilters, myCaller)
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  internal class AddCallerFilter(breakpoint: XBreakpoint<*>, private val myCaller: String) :
    BreakpointIntentionAction(breakpoint, "Stop only if caller is: $myCaller") {

    override fun actionPerformed(e: AnActionEvent) {
      with(myBreakpoint.properties as JavaBreakpointProperties<*>) {
        isCALLER_FILTERS_ENABLED = true
        callerFilters = ArrayUtil.append(callerExclusionFilters, myCaller)
        callerExclusionFilters = ArrayUtil.remove(callerExclusionFilters, myCaller)
      }
      (myBreakpoint as XBreakpointBase<*, *, *>).fireBreakpointChanged()
    }
  }

  companion object {
    @JvmStatic
    fun getIntentions(breakpoint: XBreakpoint<*>, currentSession: XDebugSession?): List<AnAction> {
      val process = currentSession?.debugProcess
      if (process is JavaDebugProcess) {
        val res = ArrayList<AnAction>()
        val debugProcess = process.debuggerSession.process
        debugProcess.managerThread.invokeAndWait(object : DebuggerContextCommandImpl(debugProcess.debuggerContext) {

          override fun getPriority() = PrioritizedTask.Priority.HIGH

          override fun threadAction(suspendContext: SuspendContextImpl) {
            if (Registry.`is`("debugger.breakpoints.caller.filter")) {
              try {
                val thread = suspendContext.thread
                if (thread != null && thread.frameCount() > 1) {
                  val parentFrame = thread.frame(1)
                  if (parentFrame != null) {
                    val method = DebuggerUtilsEx.getMethod(parentFrame.location())
                    if (method != null) {
                      val key = DebuggerUtilsEx.methodKey(parentFrame.location().method())
                      res.add(BreakpointIntentionAction.AddCallerFilter(breakpoint, key))
                      res.add(BreakpointIntentionAction.AddCallerNotFilter(breakpoint, key))
                    }
                  }
                }
              }
              catch (e: EvaluateException) {
                LineBreakpoint.LOG.warn(e)
              }

            }
          }
        })
        return res
      }
      return emptyList()
    }
  }
}
