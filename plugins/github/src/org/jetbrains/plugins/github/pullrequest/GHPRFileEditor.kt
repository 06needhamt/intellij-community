// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.pullrequest.data.GHPRTimelineLoader
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.timeline.*
import org.jetbrains.plugins.github.ui.GHListLoaderPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.handleOnEdt
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRFileEditor(progressManager: ProgressManager,
                              private val fileTypeRegistry: FileTypeRegistry,
                              private val project: Project,
                              private val editorFactory: EditorFactory,
                              private val dataProvider: GithubPullRequestDataProvider,
                              requestExecutor: GithubApiRequestExecutor,
                              repository: GHRepositoryCoordinates,
                              avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                              private val currentUser: GHUser,
                              private val pullRequestDetails: GHPullRequestShort)
  : UserDataHolderBase(), FileEditor {

  private val propertyChangeSupport = PropertyChangeSupport(this)
  private val mainPanel = Wrapper()
  private val contentPanel: JPanel

  init {
    val detailsModel = SingleValueModel(pullRequestDetails)
    val timelineModel = GHPRTimelineMergingModel()
    Disposer.register(this, Disposable {
      timelineModel.removeAll()
    })

    val loader = GHPRTimelineLoader(progressManager, requestExecutor, repository.serverPath, repository.repositoryPath,
                                    dataProvider.number, timelineModel)
    Disposer.register(this, loader)

    fun handleReviewsThreads() {
      dataProvider.reviewThreadsRequest.handleOnEdt(this) { threads, _ ->
        if (threads != null) timelineModel.setReviewsThreads(threads)
      }
    }

    fun handleDetails() {
      dataProvider.detailsRequest.handleOnEdt(this@GHPRFileEditor) { pr, _ ->
        if (pr != null) detailsModel.value = pr
      }
    }
    dataProvider.addRequestsChangesListener(this, object : GithubPullRequestDataProvider.RequestsChangedListener {
      override fun detailsRequestChanged() = handleDetails()
      override fun reviewThreadsRequestChanged() = handleReviewsThreads()
    })
    handleDetails()
    handleReviewsThreads()

    val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, mainPanel)

    val header = GHPRHeaderPanel(detailsModel, avatarIconsProvider)
    val timeline = GHPRTimelineComponent(timelineModel,
                                         createItemComponentFactory(dataProvider.reviewService, timelineModel, avatarIconsProvider))
    val loadingIcon = AsyncProcessIcon("Loading").apply {
      isVisible = false
    }

    contentPanel = object : ScrollablePanel(), ComponentWithEmptyText, Disposable {
      init {
        isOpaque = false
        border = JBUI.Borders.empty(UIUtil.LARGE_VGAP, UIUtil.DEFAULT_HGAP * 2)

        val maxWidth = (GithubUIUtil.getFontEM(this) * 42).toInt()

        layout = MigLayout(LC().gridGap("0", "0")
                             .insets("0", "0", "0", "0")
                             .fillX()
                             .flowY()).apply {
          columnConstraints = "[:$maxWidth:$maxWidth]push"
        }

        emptyText.clear()

        add(header)
        add(timeline)
        add(loadingIcon, CC().alignX("center"))
      }

      override fun getEmptyText() = timeline.emptyText

      override fun dispose() {}
    }

    val loaderPanel = object : GHListLoaderPanel<GHPRTimelineLoader>(loader, contentPanel, true) {
      override val loadingText = ""

      override fun createCenterPanel(content: JComponent) = Wrapper(content)

      override fun setLoading(isLoading: Boolean) {
        loadingIcon.isVisible = isLoading
      }

      override fun updateUI() {
        super.updateUI()
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
      }
    }
    Disposer.register(this, loaderPanel)
    Disposer.register(loaderPanel, contentPanel)
    Disposer.register(contentPanel, loadingIcon)

    mainPanel.setContent(loaderPanel)
  }

  private fun createItemComponentFactory(reviewService: GHPRReviewServiceAdapter,
                                         timelineModel: GHPRTimelineMergingModel,
                                         avatarIconsProvider: GHAvatarIconsProvider)
    : GHPRTimelineItemComponentFactory {

    val diffFactory = GHPRReviewThreadDiffComponentFactory(fileTypeRegistry, project, editorFactory)
    val eventsFactory = GHPRTimelineEventComponentFactoryImpl(avatarIconsProvider)
    return GHPRTimelineItemComponentFactory(project, reviewService, avatarIconsProvider, timelineModel, diffFactory, eventsFactory,
                                            currentUser)
  }

  override fun getName(): String = pullRequestDetails.title

  override fun getComponent(): JComponent = mainPanel
  override fun getPreferredFocusedComponent(): JComponent? = contentPanel

  override fun isModified(): Boolean = false
  override fun isValid(): Boolean = true

  override fun selectNotify() {}
  override fun deselectNotify() {}

  override fun addPropertyChangeListener(listener: PropertyChangeListener) = propertyChangeSupport.addPropertyChangeListener(listener)
  override fun removePropertyChangeListener(listener: PropertyChangeListener) = propertyChangeSupport.removePropertyChangeListener(listener)

  override fun setState(state: FileEditorState) {}
  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null

  override fun getCurrentLocation(): FileEditorLocation? = null

  override fun dispose() {}
}
