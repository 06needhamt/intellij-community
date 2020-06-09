// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.*
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRReviewSubmitAction
import org.jetbrains.plugins.github.pullrequest.action.GHPRShowDiffAction
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandlerImpl
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesDiffHelper
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesDiffHelperImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsModelImpl
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRViewComponentFactory(private val actionManager: ActionManager,
                                        private val project: Project,
                                        private val dataContext: GHPRDataContext,
                                        private val viewController: GHPRViewController,
                                        pullRequest: GHPRIdentifier,
                                        disposable: Disposable) {
  private val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, disposable)

  private val diffHelper = GHPRChangesDiffHelperImpl(project, dataProvider,
                                                     dataContext.avatarIconsProviderFactory,
                                                     dataContext.securityService.currentUser)

  private val detailsLoadingModel = GHCompletableFutureLoadingModel<GHPullRequest>(disposable)
  private val commitsLoadingModel = GHCompletableFutureLoadingModel<List<GHCommit>>(disposable)
  private val changesLoadingModel = GHCompletableFutureLoadingModel<GHPRChangesProvider>(disposable)

  init {
    dataProvider.detailsData.loadDetails(disposable) {
      detailsLoadingModel.future = it
    }
    dataProvider.changesData.loadCommitsFromApi(disposable) {
      commitsLoadingModel.future = it
    }
    dataProvider.changesData.loadChanges(disposable) {
      changesLoadingModel.future = it
    }
    // pre-fetch to show diff quicker
    dataProvider.changesData.fetchBaseBranch()
    dataProvider.changesData.fetchHeadBranch()
  }

  private val reloadDetailsAction = actionManager.getAction("Github.PullRequest.Details.Reload")
  private val reloadChangesAction = actionManager.getAction("Github.PullRequest.Changes.Reload")
  private val diffAction = GHPRShowDiffAction().apply {
    ActionUtil.copyFrom(this, IdeActions.ACTION_SHOW_DIFF_COMMON)
  }
  private val reviewSubmitAction = GHPRReviewSubmitAction()

  private val detailsLoadingErrorHandler = GHLoadingErrorHandlerImpl(project, dataContext.securityService.account) {
    dataProvider.detailsData.reloadDetails()
  }
  private val changesLoadingErrorHandler = GHLoadingErrorHandlerImpl(project, dataContext.securityService.account) {
    dataProvider.changesData.reloadChanges()
  }

  private val uiDisposable = Disposer.newDisposable().also {
    Disposer.register(disposable, it)
  }

  fun create(): JComponent {
    val infoTabInfo = TabInfo(createInfoComponent()).apply {
      text = GithubBundle.message("pull.request.info")
      sideComponent = createReturnToListSideComponent()
    }
    val commitsTabInfo = TabInfo(createCommitsComponent()).apply {
      text = GithubBundle.message("pull.request.commits")
      sideComponent = createReturnToListSideComponent()
    }

    commitsLoadingModel.addStateChangeListener(object : GHLoadingModel.StateChangeListener {
      override fun onLoadingCompleted() {
        val commits = if (commitsLoadingModel.resultAvailable) commitsLoadingModel.result!! else null
        val commitsCount = commits?.size
        commitsTabInfo.text = if (commitsCount == null) GithubBundle.message("pull.request.commits")
        else GithubBundle.message("pull.request.commits.count", commitsCount)
      }
    })

    return object : SingleHeightTabs(project, uiDisposable) {
      override fun adjust(each: TabInfo?) {}
    }.apply {
      setDataProvider { dataId ->
        when {
          GHPRActionKeys.GIT_REPOSITORY.`is`(dataId) -> dataContext.gitRepositoryCoordinates.repository
          GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER.`is`(dataId) -> this@GHPRViewComponentFactory.dataProvider
          GHPRChangesDiffHelper.DATA_KEY.`is`(dataId) -> diffHelper
          else -> null
        }
      }
      addTab(infoTabInfo)
      addTab(commitsTabInfo)
    }
  }

  private fun createReturnToListSideComponent(): JComponent {
    return BorderLayoutPanel()
      .addToRight(LinkLabel<Any>(GithubBundle.message("pull.request.back.to.list"), AllIcons.Actions.Back) { _, _ ->
        viewController.viewList()
      }.apply {
        border = JBUI.Borders.emptyRight(8)
      })
      .andTransparent()
      .withBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM))
  }

  private fun createInfoComponent(): JComponent {
    val detailsLoadingPanel = GHLoadingPanelFactory(detailsLoadingModel,
                                                    null, GithubBundle.message("cannot.load.details"),
                                                    detailsLoadingErrorHandler).createWithUpdatesStripe(uiDisposable) { _, model ->
      val detailsModel = GHPRDetailsModelImpl(model,
                                              dataContext.securityService,
                                              dataContext.repositoryDataService,
                                              dataProvider.detailsData)
      GHPRDetailsComponent.create(detailsModel, dataContext.avatarIconsProviderFactory)
    }.also {
      reloadDetailsAction.registerCustomShortcutSet(it, uiDisposable)
    }

    val changesLoadingPanel = GHLoadingPanelFactory(changesLoadingModel, null,
                                                    GithubBundle.message("cannot.load.changes"),
                                                    changesLoadingErrorHandler)
      .createWithUpdatesStripe(uiDisposable) { parent, model ->
        createChangesTree(parent, model.map { it.changes }, GithubBundle.message("pull.request.does.not.contain.changes"))
      }.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }
    val toolbar = createChangesBrowserToolbar(changesLoadingPanel)
    val changesBrowser = BorderLayoutPanel().andTransparent()
      .addToTop(toolbar)
      .addToCenter(changesLoadingPanel)

    return OnePixelSplitter(true, "Github.PullRequest.Info.Component", 0.33f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()

      firstComponent = detailsLoadingPanel
      secondComponent = changesBrowser
    }
  }

  private fun createCommitsComponent(): JComponent {
    val commitSelectionListener = CommitSelectionListener()

    val commitsLoadingPanel = GHLoadingPanelFactory(commitsLoadingModel,
                                                    null, GithubBundle.message("cannot.load.commits"),
                                                    changesLoadingErrorHandler)
      .createWithUpdatesStripe(uiDisposable) { _, model ->
        GHPRCommitsBrowserComponent.create(model, commitSelectionListener)
      }

    val changesLoadingPanel = GHLoadingPanelFactory(changesLoadingModel,
                                                    GithubBundle.message("pull.request.select.commit.to.view.changes"),
                                                    GithubBundle.message("cannot.load.changes"),
                                                    changesLoadingErrorHandler)
      .createWithUpdatesStripe(uiDisposable) { parent, model ->
        createChangesTree(parent, createCommitChangesModel(model, commitSelectionListener),
                          GithubBundle.message("pull.request.commit.does.not.contain.changes"))
      }.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }
    val toolbar = createChangesBrowserToolbar(changesLoadingPanel)
    val changesBrowser = BorderLayoutPanel().andTransparent()
      .addToTop(toolbar)
      .addToCenter(changesLoadingPanel)

    return OnePixelSplitter(true, "Github.PullRequest.Commits.Component", 0.4f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()

      firstComponent = commitsLoadingPanel
      secondComponent = changesBrowser
    }.also {
      reloadChangesAction.registerCustomShortcutSet(it, uiDisposable)
    }
  }

  private fun createCommitChangesModel(changesModel: SingleValueModel<GHPRChangesProvider>,
                                       commitSelectionListener: CommitSelectionListener): SingleValueModel<List<Change>> {
    val model = SingleValueModel(changesModel.value.changesByCommits[commitSelectionListener.currentCommit].orEmpty())
    fun update() {
      val commit = commitSelectionListener.currentCommit
      model.value = changesModel.value.changesByCommits[commit].orEmpty()
    }
    commitSelectionListener.delegate = ::update
    changesModel.addAndInvokeValueChangedListener(::update)
    return model
  }

  private fun createChangesTree(parentPanel: JPanel, model: SingleValueModel<List<Change>>, emptyTextText: String): JComponent {

    val tree = object : ChangesTree(project, false, false) {
      override fun rebuildTree() {
        updateTreeModel(TreeModelBuilder(project, grouping).setChanges(model.value, null).build())
        if (isSelectionEmpty && !isEmpty) TreeUtil.selectFirstNode(this)
      }

      override fun getData(dataId: String) = super.getData(dataId) ?: VcsTreeModelData.getData(project, this, dataId)

    }.apply {
      emptyText.text = emptyTextText
    }.also {
      SelectionSaver.installOn(it)
      it.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          if (it.isSelectionEmpty && !it.isEmpty) TreeUtil.selectFirstNode(it)
        }
      })
    }

    model.addAndInvokeValueChangedListener(tree::rebuildTree)

    diffAction.registerCustomShortcutSet(CompositeShortcutSet(diffAction.shortcutSet, CommonShortcuts.DOUBLE_CLICK_1), tree)
    reloadChangesAction.registerCustomShortcutSet(tree, null)
    tree.installPopupHandler(DefaultActionGroup(diffAction, reloadChangesAction))

    DataManager.registerDataProvider(parentPanel) {
      if (tree.isShowing) tree.getData(it) else null
    }
    return ScrollPaneFactory.createScrollPane(tree, true)
  }

  private class CommitSelectionListener : (GHCommit?) -> Unit {
    var currentCommit: GHCommit? = null
    var delegate: (() -> Unit)? = null

    override fun invoke(commit: GHCommit?) {
      currentCommit = commit
      delegate?.invoke()
    }
  }

  private fun createChangesBrowserToolbar(target: JComponent)
    : TreeActionsToolbarPanel {

    val changesToolbarActionGroup = DefaultActionGroup(diffAction, reviewSubmitAction, Separator(),
                                                       actionManager.getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    val changesToolbar = actionManager.createActionToolbar("ChangesBrowser", changesToolbarActionGroup, true)
    val treeActionsGroup = DefaultActionGroup(actionManager.getAction(IdeActions.ACTION_EXPAND_ALL),
                                              actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL))
    return TreeActionsToolbarPanel(changesToolbar, treeActionsGroup, target)
  }
}