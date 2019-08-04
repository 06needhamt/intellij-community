// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ApplicationLoader")
package com.intellij.idea

import com.intellij.diagnostic.*
import com.intellij.diagnostic.StartUpMeasurer.Phases
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.customize.CustomizeIDEWizardDialog
import com.intellij.ide.customize.CustomizeIDEWizardStepsProvider
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.*
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogEarthquakeShaker
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemPropertyBean
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.RegistryKeyBean
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WeakFocusStackManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.SystemDock
import com.intellij.openapi.wm.impl.WindowManagerImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.AppIcon
import com.intellij.ui.AppUIUtil
import com.intellij.ui.CustomProtocolHandler
import com.intellij.ui.mac.MacOSApplicationProvider
import com.intellij.ui.mac.touchbar.TouchBarsManager
import com.intellij.util.ArrayUtilRt
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.exists
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.accessibility.ScreenReader
import net.miginfocom.layout.PlatformDefaults
import org.jetbrains.annotations.ApiStatus
import java.awt.EventQueue
import java.beans.PropertyChangeListener
import java.io.File
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.JOptionPane
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

private val SAFE_JAVA_ENV_PARAMETERS = arrayOf(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY)
private val LOG = Logger.getInstance("#com.intellij.idea.IdeaApplication")

private var filesToLoad: List<File> = emptyList()
private var wizardStepProvider: CustomizeIDEWizardStepsProvider? = null

private fun executeInitAppInEdt(rawArgs: Array<String>,
                                initAppActivity: Activity,
                                pluginDescriptorsFuture: CompletableFuture<List<IdeaPluginDescriptor>>) {
  val args = processProgramArguments(rawArgs)
  StartupUtil.patchSystem(LOG)
  val starter = createAppStarter(args, pluginDescriptorsFuture)
  val headless = Main.isHeadless()
  val app = initAppActivity.runChild("create app") {
    ApplicationImpl(java.lang.Boolean.getBoolean(PluginManagerCore.IDEA_IS_INTERNAL_PROPERTY), false, headless, Main.isCommandLine(),
                    ApplicationManagerEx.IDEA_APPLICATION)
  }

  starter.premain(args)

  // this code is here for one simple reason - here we have application,
  // and after plugin loading we don't have - ApplicationManager.getApplication() can be used, but it doesn't matter
  // but it is very important to call registerRegistryAndMessageBusAndComponent immediately after application creation
  // and do not place any time-consuming code in between (e.g. showLicenseeInfoOnSplash)
  var future = registerRegistryAndContainerAndInitStore(pluginDescriptorsFuture, app, initAppActivity)

  if (!headless) {
    initAppActivity.runChild("icon loader activation") {
      // todo investigate why in test mode dummy icon manager is not suitable
      IconLoader.activate()
      IconLoader.setStrictGlobally(app.isInternal)
    }
  }

  // preload services only after icon activation
  future = future.thenCompose {
    val preloadServiceActivity = StartUpMeasurer.start("preload services")
    preloadServices(app)
      .thenRun(Runnable {
        preloadServiceActivity.end()
      })
  }

  if (!headless) {
    if (SystemInfo.isMac) {
      initAppActivity.runChild("mac app init") {
        MacOSApplicationProvider.initApplication()
      }

      // ensure that TouchBarsManager is loaded before WelcomeFrame/project
      // do not wait completion - it is thread safe and not required for application start
      AppExecutorUtil.getAppExecutorService().execute {
        ParallelActivity.PREPARE_APP_INIT.run("mac touchbar") {
          TouchBarsManager.isTouchBarAvailable()
        }
      }
    }

    // disabled due to https://youtrack.jetbrains.com/issue/JBR-1399
    //initAppActivity.runChild("showLicenseeInfoOnSplash") {
    //  SplashManager.showLicenseeInfoOnSplash(LOG)
    //}

    AppExecutorUtil.getAppExecutorService().execute {
      AsyncProcessIcon("")
      AsyncProcessIcon.Big("")
      AnimatedIcon.Blinking(AllIcons.Ide.FatalError)
      AnimatedIcon.FS()
      AllIcons.Ide.Shadow.Top.iconHeight
    }

    initAppActivity.runChild("migLayout") {
      //IDEA-170295
      PlatformDefaults.setLogicalPixelBase(PlatformDefaults.BASE_FONT_SIZE)
    }
    WeakFocusStackManager.getInstance()
  }

  future.thenRun(Runnable {
    // this invokeLater() call is needed not only because current thread maybe not EDT, but to place the app starting code on a freshly minted IdeEventQueue instance
    val placeOnEventQueueActivity = initAppActivity.startChild(Phases.PLACE_ON_EVENT_QUEUE)
    EventQueue.invokeLater {
      placeOnEventQueueActivity.end()
      StartupUtil.installExceptionHandler()
      initAppActivity.end()

      app.loadComponents(SplashManager.getProgressIndicator())
      if (!headless) {
        addActivateAndWindowsCliListeners(app)
      }

      (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
        starter.main(args)
      }

      if (PluginManagerCore.isRunningFromSources()) {
        AppExecutorUtil.getAppExecutorService().execute {
          AppUIUtil.updateWindowIcon(JOptionPane.getRootFrame())
        }
      }
    }

    // execute in parallel to loading components - this functionality should be used only by plugin functionality,
    // that used after start-up
    ParallelActivity.PREPARE_APP_INIT.run("init system properties") {
      SystemPropertyBean.initSystemProperties()
    }
  })
}

@ApiStatus.Internal
fun registerRegistryAndContainerAndInitStore(pluginDescriptorsFuture: CompletableFuture<List<IdeaPluginDescriptor>>,
                                             app: ApplicationImpl,
                                             initAppActivity: Activity?): CompletableFuture<Void?> {
  return pluginDescriptorsFuture
    .thenCompose { pluginDescriptors ->
      val future = CompletableFuture.runAsync(Runnable {
        ParallelActivity.PREPARE_APP_INIT.run("add registry keys") {
          RegistryKeyBean.addKeysFromPlugins()
        }
      }, AppExecutorUtil.getAppExecutorService())

      initAppActivity.runChild("app component registration") {
        app.registerComponents(pluginDescriptors)
      }

      // yes, at this moment initSystemProperties or RegistryKeyBean.addKeysFromPlugins maybe not yet performed, but it doesn't affect because not used.
      initConfigurationStore(app, null)

      future
    }
}

private fun addActivateAndWindowsCliListeners(app: ApplicationImpl) {
  StartupUtil.addExternalInstanceListener { args ->
    val ref = AtomicReference<Future<CliResult>>()
    app.invokeAndWait {
      LOG.info("ApplicationImpl.externalInstanceListener invocation")
      val realArgs = if (args.isEmpty()) args else args.subList(1, args.size)
      val projectAndFuture = CommandLineProcessor.processExternalCommandLine(realArgs, args.firstOrNull())
      ref.set(projectAndFuture.getSecond())
      val frame = when (val project = projectAndFuture.getFirst()) {
        null -> WindowManager.getInstance().findVisibleFrame()
        else -> WindowManager.getInstance().getIdeFrame(project) as JFrame
      }
      if (frame != null) {
        if (frame is IdeFrame) {
          AppIcon.getInstance().requestFocus(frame as IdeFrame)
        }
        else {
          frame.toFront()
          DialogEarthquakeShaker.shake(frame)
        }
      }
    }

    ref.get()
  }

  MainRunner.LISTENER = WindowsCommandLineListener { currentDirectory, args ->
    val argsList = args.toList()
    LOG.info("Received external Windows command line: current directory $currentDirectory, command line $argsList")
    if (argsList.isEmpty()) return@WindowsCommandLineListener 0
    var state = app.defaultModalityState
    for (starter in ApplicationStarter.EP_NAME.iterable) {
      if (starter.canProcessExternalCommandLine() && argsList[0] == starter.commandName && starter.allowAnyModalityState()) {
        state = app.anyModalityState
      }
    }

    val ref = AtomicReference<Future<CliResult>>()
    app.invokeAndWait({ ref.set(CommandLineProcessor.processExternalCommandLine(argsList, currentDirectory).getSecond()) }, state)
    CliResult.getOrWrapFailure(ref.get(), 1).returnCode
  }
}

private fun createAppStarter(args: Array<String>, pluginsLoaded: Future<*>): ApplicationStarter {
  if (args.isEmpty()) {
    return IdeStarter()
  }

  pluginsLoaded.get()

  val starter = findStarter(args[0]) ?: IdeStarter()
  if (Main.isHeadless() && !starter.isHeadless) {
    Main.showMessage("Startup Error", "Application cannot start in headless mode", true)
    exitProcess(Main.NO_GRAPHICS)
  }
  return starter
}

fun initApplication(rawArgs: Array<String>) {
  val initAppActivity = MainRunner.startupStart.endAndStart(Phases.INIT_APP)
  val pluginDescriptorsFuture = CompletableFuture<List<IdeaPluginDescriptor>>()
  EventQueue.invokeLater {
    executeInitAppInEdt(rawArgs, initAppActivity, pluginDescriptorsFuture)
  }

  val plugins = try {
    initAppActivity.runChild("plugin descriptors loading") {
      PluginManagerCore.getLoadedPlugins(MainRunner::class.java.classLoader)
    }
  }
  catch (e: Throwable) {
    pluginDescriptorsFuture.completeExceptionally(e)
    return
  }

  pluginDescriptorsFuture.complete(plugins)
}

fun findStarter(key: String?): ApplicationStarter? {
  for (starter in ApplicationStarter.EP_NAME.iterable) {
    if (starter == null) {
      break
    }

    if (starter.commandName == key) {
      return starter
    }
  }
  return null
}

fun openFilesOnLoading(files: List<File>) {
  filesToLoad = files
}

fun setWizardStepsProvider(provider: CustomizeIDEWizardStepsProvider) {
  wizardStepProvider = provider
}

fun initConfigurationStore(app: ApplicationImpl, configPath: String?) {
  val beforeApplicationLoadedActivity = StartUpMeasurer.start("beforeApplicationLoaded")
  val effectiveConfigPath = FileUtilRt.toSystemIndependentName(configPath ?: PathManager.getConfigPath())
  for (listener in ApplicationLoadListener.EP_NAME.iterable) {
    try {
      (listener ?: break).beforeApplicationLoaded(app, effectiveConfigPath)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  val initStoreActivity = beforeApplicationLoadedActivity.endAndStart("init app store")

  // we set it after beforeApplicationLoaded call, because app store can depends on stream provider state
  app.stateStore.setPath(effectiveConfigPath)
  LoadingPhase.setCurrentPhase(LoadingPhase.CONFIGURATION_STORE_INITIALIZED)
  initStoreActivity.end()
}

open class IdeStarter : ApplicationStarter {
  override fun isHeadless() = false

  override fun getCommandName(): String? = null

  override fun canProcessExternalCommandLine() = true

  override fun processExternalCommandLineAsync(args: Array<String>, currentDirectory: String?): Future<CliResult> {
    LOG.info("Request to open in $currentDirectory with parameters: ${args.joinToString(separator = ",")}")
    if (args.isEmpty()) {
      return CliResult.ok()
    }

    val filename = args[0]
    val file = if (currentDirectory == null) Paths.get(filename) else Paths.get(currentDirectory, filename)
    if (file.exists()) {
      var line = -1
      if (args.size > 2 && CustomProtocolHandler.LINE_NUMBER_ARG_NAME == args[1]) {
        try {
          line = args[2].toInt()
        }
        catch (ignore: NumberFormatException) {
          LOG.error("Wrong line number: ${args[2]}")
        }

      }
      PlatformProjectOpenProcessor.doOpenProject(file, OpenProjectTask(), line)
    }
    return CliResult.error(1, "Can't find file: $file")
  }

  private fun loadProjectFromExternalCommandLine(commandLineArgs: List<String>): Project? {
    var project: Project? = null
    if (commandLineArgs.firstOrNull() != null) {
      LOG.info("ApplicationLoader.loadProject")
      val currentDirectory = System.getProperty("ide.launcher.initialWorkingDir")
      LOG.info("ide.launcher.initialWorkingDir: $currentDirectory")
      project = CommandLineProcessor.processExternalCommandLine(commandLineArgs, currentDirectory).getFirst()
    }
    return project
  }

  override fun main(args: Array<String>) {
    val frameInitActivity = StartUpMeasurer.start(Phases.FRAME_INITIALIZATION)

    val app = ApplicationManager.getApplication()
    // Event queue should not be changed during initialization of application components.
    // It also cannot be changed before initialization of application components because IdeEventQueue uses other
    // application components. So it is proper to perform replacement only here.
    frameInitActivity.runChild("set window manager") {
      IdeEventQueue.getInstance().setWindowManager(WindowManager.getInstance() as WindowManagerImpl)
    }

    val commandLineArgs = args.toList()

    val appFrameCreatedActivity = frameInitActivity.startChild("call appFrameCreated")
    val lifecyclePublisher = app.messageBus.syncPublisher(AppLifecycleListener.TOPIC)
    lifecyclePublisher.appFrameCreated(commandLineArgs)
    appFrameCreatedActivity.end()

    // must be after appFrameCreated because some listeners can mutate state of RecentProjectsManager
    val willOpenProject = commandLineArgs.isNotEmpty() || filesToLoad.isNotEmpty() || RecentProjectsManager.getInstance().willReopenProjectOnStart()

    // temporary check until the JRE implementation has been checked and bundled
    if (java.lang.Boolean.getBoolean("ide.popup.enablePopupType")) {
      @Suppress("SpellCheckingInspection")
      System.setProperty("jbre.popupwindow.settype", "true")
    }

    val shouldShowWelcomeFrame = !willOpenProject || JetBrainsProtocolHandler.getCommand() != null
    val doShowWelcomeFrame = if (shouldShowWelcomeFrame) WelcomeFrame.prepareToShow() else null
    showWizardAndWelcomeFrame(when (doShowWelcomeFrame) {
      null -> null
      else -> Runnable {
        doShowWelcomeFrame.run()
        lifecyclePublisher.welcomeScreenDisplayed()
      }
    })

    frameInitActivity.end()

    AppExecutorUtil.getAppExecutorService().run {
      LifecycleUsageTriggerCollector.onIdeStart()
    }

    TransactionGuard.submitTransaction(app, Runnable {
      val project = when {
        filesToLoad.isNotEmpty() -> ProjectUtil.tryOpenFileList(null, filesToLoad, "MacMenu")
        commandLineArgs.isNotEmpty() -> loadProjectFromExternalCommandLine(commandLineArgs)
        else -> null
      }

      app.messageBus.syncPublisher(AppLifecycleListener.TOPIC).appStarting(project)

      if (project == null && !JetBrainsProtocolHandler.appStartedWithCommand()) {
        RecentProjectsManager.getInstance().reopenLastProjectsOnStart()
      }

      EventQueue.invokeLater {
        PluginManager.reportPluginError()
      }
    })

    if (!app.isHeadlessEnvironment) {
      postOpenUiTasks(app)
    }
  }

  private fun showWizardAndWelcomeFrame(showWelcomeFrame: Runnable?) {
    wizardStepProvider?.let { wizardStepsProvider ->
      val wizardDialog = object : CustomizeIDEWizardDialog(wizardStepsProvider, null, false, true) {
        override fun doOKAction() {
          super.doOKAction()
          showWelcomeFrame?.run()
        }
      }
      if (wizardDialog.showIfNeeded()) {
        return
      }
    }
    showWelcomeFrame?.run()
  }

  private fun postOpenUiTasks(app: Application) {
    if (SystemInfo.isMac) {
      ApplicationManager.getApplication().executeOnPooledThread {
        TouchBarsManager.onApplicationInitialized()
        if (TouchBarsManager.isTouchBarAvailable())
          CustomActionsSchema.addSettingsGroup(IdeActions.GROUP_TOUCHBAR, "Touch Bar")
      }
    }

    app.invokeLater {
      val updateSystemDockActivity = StartUpMeasurer.start("system dock menu")
      SystemDock.updateMenu()
      updateSystemDockActivity.end()
    }
    app.invokeLater {
      val generalSettings = GeneralSettings.getInstance()
      generalSettings.addPropertyChangeListener(GeneralSettings.PROP_SUPPORT_SCREEN_READERS, app,
                                                PropertyChangeListener { e -> ScreenReader.setActive(e.newValue as Boolean) })
      ScreenReader.setActive(generalSettings.isSupportScreenReaders)
    }
  }
}

/**
 * Method looks for `-Dkey=value` program arguments and stores some of them in system properties.
 * We should use it for a limited number of safe keys.
 * One of them is a list of ids of required plugins
 *
 * @see SAFE_JAVA_ENV_PARAMETERS
 */
@Suppress("SpellCheckingInspection")
private fun processProgramArguments(args: Array<String>): Array<String> {
  val arguments = ArrayList<String>()
  val safeKeys = SAFE_JAVA_ENV_PARAMETERS.toList()
  for (arg in args) {
    if (arg.startsWith("-D")) {
      val keyValue = arg.substring(2).split('=')
      if (keyValue.size == 2 && safeKeys.contains(keyValue[0])) {
        System.setProperty(keyValue[0], keyValue[1])
        continue
      }
    }
    if (SplashManager.NO_SPLASH == arg) {
      continue
    }

    arguments.add(arg)
  }
  return ArrayUtilRt.toStringArray(arguments)
}

@ApiStatus.Internal
fun preloadServices(app: ApplicationImpl): CompletableFuture<Void?> {
  val toPreload = mutableListOf<String>()
  val picoContainer = app.picoContainer
  for (plugin in PluginManagerCore.getLoadedPlugins()) {
    for (service in (plugin as IdeaPluginDescriptorImpl).app.services) {
      if (service.preload) {
        toPreload.add(service.getInterface())
      }
    }
  }

  if (toPreload.isEmpty()) {
    return CompletableFuture.completedFuture(null)
  }

  val appExecutorService = AppExecutorUtil.getAppExecutorService()

  val maxThreads = Runtime.getRuntime().availableProcessors()
  val bucketSize = max(toPreload.size / maxThreads, 1)
  return CompletableFuture.allOf(*Array(min(toPreload.size, maxThreads)) {
    val startIndex = it * bucketSize
    val list = toPreload.subList(startIndex, min(startIndex + bucketSize, toPreload.size))
    CompletableFuture.runAsync(Runnable {
      for (key in list) {
        picoContainer.getComponentInstance(key)
      }
    }, appExecutorService)
  })
}