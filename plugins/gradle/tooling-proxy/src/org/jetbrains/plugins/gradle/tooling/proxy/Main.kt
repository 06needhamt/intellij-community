// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.proxy

import org.apache.log4j.ConsoleAppender
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.internal.remote.internal.inet.InetEndpoint
import org.gradle.launcher.cli.action.BuildActionSerializer
import org.gradle.launcher.daemon.protocol.BuildEvent
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer
import org.gradle.launcher.daemon.protocol.Failure
import org.gradle.launcher.daemon.protocol.Success
import org.gradle.tooling.*
import org.gradle.tooling.events.OperationType
import org.slf4j.LoggerFactory
import java.io.File

object Main {
  const val LOCAL_BUILD_PROPERTY = "idea.gradle.target.local"
  private lateinit var LOG: org.slf4j.Logger
  private lateinit var serverConnector: TargetTcpServerConnector
  private lateinit var incomingConnectionHandler: TargetIncomingConnectionHandler

  @JvmStatic
  fun main(args: Array<String>) {
    initLogging(args)
    try {
      doMain()
    }
    finally {
      if (::serverConnector.isInitialized) {
        serverConnector.stop()
      }
    }
  }

  private fun doMain() {
    serverConnector = TargetTcpServerConnector(DefaultExecutorFactory(), InetAddressFactory(),
                                               DaemonMessageSerializer.create(BuildActionSerializer.create()))
    incomingConnectionHandler = TargetIncomingConnectionHandler()
    val address = serverConnector.start(incomingConnectionHandler) { LOG.error("connection error") } as InetEndpoint
    println("Gradle target server hostName: ${address.candidates.first().hostName} port: ${address.port}")
    waitForIncomingConnection()
    waitForBuildParameters()

    val targetBuildParameters = incomingConnectionHandler.targetBuildParameters()
    LOG.debug("targetBuildParameters: $targetBuildParameters")

    val connector = createConnector { incomingConnectionHandler.dispatch(BuildEvent(it)) }
    val workingDirectory = File(".").canonicalFile
    LOG.debug("Working directory: ${workingDirectory.absolutePath}")
    connector.forProjectDirectory(workingDirectory.absoluteFile)
    connector.connect().use { connection ->
      val resultHandler: ResultHandler<Any?> = object : ResultHandler<Any?> {
        override fun onComplete(result: Any?) {
          LOG.debug("operation result: $result")
          incomingConnectionHandler.dispatch(Success(result))
        }

        override fun onFailure(connectionException: GradleConnectionException?) {
          LOG.debug("GradleConnectionException: $connectionException")
          incomingConnectionHandler.dispatch(Failure(connectionException))
        }
      }
      val operation = when (targetBuildParameters) {
        is BuildLauncherParameters -> connection.newBuild().apply { forTasks(*targetBuildParameters.tasks.toTypedArray()) }
        is TestLauncherParameters -> connection.newTestLauncher()
        is ModelBuilderParameters<*> -> connection.model(targetBuildParameters.modelType).apply {
          forTasks(*targetBuildParameters.tasks.toTypedArray())
        }
        is BuildActionParameters<*> -> connection.action(targetBuildParameters.buildAction).apply {
          forTasks(*targetBuildParameters.tasks.toTypedArray())
        }
        is PhasedBuildActionParameters<*> -> connection.action()
          .projectsLoaded(targetBuildParameters.projectsLoadedAction, IntermediateResultHandler {
            //resultHandler.onComplete(it)
          })
          .buildFinished(targetBuildParameters.buildFinishedAction, IntermediateResultHandler {
            resultHandler.onComplete(it)
          })
          .build().apply {
            forTasks(*targetBuildParameters.tasks.toTypedArray())
          }
      }
      operation.apply {
        setStandardError(System.err)
        setStandardOutput(System.out)
        addProgressListener(
          org.gradle.tooling.events.ProgressListener {
            // empty listener to supply tasks progress events subscription
          },
          OperationType.TASK
        )
        withArguments(targetBuildParameters.arguments)
        setJvmArguments(targetBuildParameters.jvmArguments)

        when (this) {
          is BuildLauncher -> run(resultHandler)
          is TestLauncher -> run(resultHandler)
          is ModelBuilder<*> -> get(resultHandler)
          is BuildActionExecuter<*> -> run(resultHandler)
        }
      }
    }
  }

  private fun waitForIncomingConnection() {
    waitFor({ incomingConnectionHandler.isConnected() },
            "Waiting for incoming connection....",
            "Incoming connection timeout")
  }

  private fun waitForBuildParameters() {
    waitFor({ incomingConnectionHandler.isBuildParametersReceived() },
            "Waiting for target build parameters....",
            "Target build parameters were not received")
  }

  private fun waitFor(handler: () -> Boolean, waitingMessage: String, timeOutMessage: String) {
    val startTime = System.currentTimeMillis()
    while (!handler.invoke() && (System.currentTimeMillis() - startTime) < 5000) {
      LOG.debug(waitingMessage)
      val lock = Object()
      synchronized(lock) {
        try {
          lock.wait(100)
        }
        catch (ignore: InterruptedException) {
        }
      }
    }
    check(handler.invoke()) { timeOutMessage }
  }

  private fun initLogging(args: Array<String>) {
    val loggingArguments = listOf("--debug", "--info", "-warn", "--error", "--trace")
    val loggingLevel = args.find { it in loggingArguments }?.drop(2) ?: "error"
    Logger.getRootLogger().apply {
      addAppender(ConsoleAppender(PatternLayout("%d{dd/MM HH:mm:ss} %-5p %C{1}.%M - %m%n")))
      level = Level.toLevel(loggingLevel, Level.ERROR)
    }

    LOG = LoggerFactory.getLogger(Main::class.java)
  }
}