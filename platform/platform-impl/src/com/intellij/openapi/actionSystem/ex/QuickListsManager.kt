// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.ex

import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.QuickSwitchSchemeAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.BundledQuickListsProvider
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.SchemeManager
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import gnu.trove.THashSet
import java.util.function.Function

class QuickListsManager {
  private val mySchemeManager: SchemeManager<QuickList>
  private val myActionManager by lazy { ActionManager.getInstance() }

  init {
    mySchemeManager = SchemeManagerFactory.getInstance().create("quicklists",
                                                                object : LazySchemeProcessor<QuickList, QuickList>(
                                                                  QuickList.DISPLAY_NAME_TAG) {
                                                                  override fun createScheme(dataHolder: SchemeDataHolder<QuickList>,
                                                                                            name: String,
                                                                                            attributeProvider: Function<in String, String?>,
                                                                                            isBundled: Boolean): QuickList {
                                                                    val item = QuickList()
                                                                    item.readExternal(dataHolder.read())
                                                                    dataHolder.updateDigest(item)
                                                                    return item
                                                                  }
                                                                }, presentableName = IdeBundle.message("quick.lists.presentable.name"))

    for (provider in BundledQuickListsProvider.EP_NAME.extensionList) {
      for (path in provider.bundledListsRelativePaths) {
        mySchemeManager.loadBundledScheme(path, provider)
      }
    }
    mySchemeManager.loadSchemes()
    registerActions()
  }

  companion object {
    @JvmStatic
    val instance: QuickListsManager
      get() = ServiceManager.getService(QuickListsManager::class.java)
  }

  val schemeManager: SchemeManager<QuickList>
    get() = mySchemeManager

  val allQuickLists: Array<QuickList>
    get() {
      return mySchemeManager.allSchemes.toTypedArray()
    }

  private fun registerActions() {
    // to prevent exception if 2 or more targets have the same name
    val registeredIds = THashSet<String>()
    for (scheme in mySchemeManager.allSchemes) {
      val actionId = scheme.actionId
      if (registeredIds.add(actionId)) {
        myActionManager.registerAction(actionId, InvokeQuickListAction(scheme))
      }
    }
  }

  private fun unregisterActions() {
    for (oldId in myActionManager.getActionIds(QuickList.QUICK_LIST_PREFIX)) {
      myActionManager.unregisterAction(oldId)
    }
  }

  // used by external plugin
  fun setQuickLists(quickLists: List<QuickList>) {
    unregisterActions()
    mySchemeManager.setSchemes(quickLists)
    registerActions()
  }
}

class InvokeQuickListAction(private val myQuickList: QuickList) : QuickSwitchSchemeAction() {
  init {
    myActionPlace = ActionPlaces.ACTION_PLACE_QUICK_LIST_POPUP_ACTION
    templatePresentation.description = myQuickList.description
    templatePresentation.setText(myQuickList.name, false)
  }

  override fun fillActions(project: Project, group: DefaultActionGroup, dataContext: DataContext) {
    val actionManager = ActionManager.getInstance()
    for (actionId in myQuickList.actionIds) {
      if (QuickList.SEPARATOR_ID == actionId) {
        group.addSeparator()
      }
      else {
        val action = actionManager.getAction(actionId)
        if (action != null) {
          group.add(action)
        }
      }
    }
  }
}

internal class QuickListPreloaded : PreloadingActivity() {
  override fun preload(indicator: ProgressIndicator) {
    QuickListsManager.instance
  }
}
