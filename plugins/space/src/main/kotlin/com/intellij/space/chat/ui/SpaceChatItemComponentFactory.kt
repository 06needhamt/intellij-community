// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui

import circlet.client.api.M2TextItemContent
import circlet.client.api.Navigator
import circlet.client.api.mc.MCMessage
import circlet.code.api.*
import circlet.completion.mentions.MentionConverter
import circlet.m2.channel.M2ChannelVm
import circlet.platform.client.property
import circlet.platform.client.resolve
import circlet.platform.client.resolveAll
import circlet.principals.asUser
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.newui.HorizontalLayout
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.HtmlChunk.html
import com.intellij.space.chat.model.api.SpaceChatItem
import com.intellij.space.chat.ui.message.MessageTitleComponent
import com.intellij.space.chat.ui.thread.createThreadComponent
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.messages.SpaceBundle
import com.intellij.space.ui.SpaceAvatarProvider
import com.intellij.space.ui.resizeIcon
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.CONTRAST_BORDER_COLOR
import com.intellij.util.ui.codereview.InlineIconButton
import com.intellij.util.ui.codereview.SingleValueModelImpl
import com.intellij.util.ui.codereview.ToggleableContainer
import com.intellij.util.ui.codereview.timeline.TimelineItemComponentFactory
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextField
import com.intellij.util.ui.codereview.timeline.comment.SubmittableTextFieldModelBase
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.SpaceIcons
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.launch
import org.jetbrains.annotations.Nls
import runtime.Ui
import runtime.reactive.Property
import runtime.reactive.awaitLoaded
import runtime.reactive.map
import java.awt.*
import java.awt.event.ActionListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class SpaceChatItemComponentFactory(
  private val project: Project,
  private val lifetime: Lifetime,
  private val server: String,
  private val avatarProvider: SpaceAvatarProvider
) : TimelineItemComponentFactory<SpaceChatItem> {

  /**
   * Method should return [HoverableJPanel] because it is used to implement hovering properly
   *
   * @see SpaceChatPanel
   */
  override fun createComponent(item: SpaceChatItem): HoverableJPanel {
    val component =
      when (val details = item.details) {
        is CodeDiscussionAddedFeedEvent -> {
          val discussion = details.codeDiscussion.resolve()
          val resolved = lifetime.map(details.codeDiscussion.property()) { it.resolved }
          val thread = item.thread!!
          createDiff(project, discussion, resolved, thread) ?: createUnsupportedMessageTypePanel(item.link)
        }
        is M2TextItemContent -> {
          val messagePanel = createSimpleMessageComponent(item)
          val thread = item.thread
          if (thread == null) {
            messagePanel
          }
          else {
            val threadComponent = createThreadComponent(project, lifetime, thread)
            JPanel(VerticalLayout(0)).apply {
              isOpaque = false
              add(messagePanel, VerticalLayout.FILL_HORIZONTAL)
              add(threadComponent, VerticalLayout.FILL_HORIZONTAL)
            }
          }
        }
        is ReviewRevisionsChangedEvent -> {
          val review = details.review?.resolve()
          if (review == null) {
            EventMessagePanel(createSimpleMessageComponent(item))
          }
          else {
            details.createComponent(review)
          }
        }
        is ReviewCompletionStateChangedEvent -> EventMessagePanel(createSimpleMessageComponent(item))
        is ReviewerChangedEvent -> {
          val user = details.uid.resolve().link(server)
          val text = when (details.changeType) {
            ReviewerChangedType.Joined -> SpaceBundle.message("chat.reviewer.added", user)
            ReviewerChangedType.Left -> SpaceBundle.message("chat.reviewer.removed", user)
          }
          EventMessagePanel(createSimpleMessageComponent(text))
        }
        is MergeRequestMergedEvent -> EventMessagePanel(
          createSimpleMessageComponent(
            HtmlChunk.raw(
              SpaceBundle.message(
                "chat.review.merged",
                HtmlChunk.text(details.sourceBranch).bold(), // NON-NLS
                HtmlChunk.text(details.targetBranch).bold() // NON-NLS
              )
            ).wrapWith(html()).toString()
          )
        )
        is MergeRequestBranchDeletedEvent -> EventMessagePanel(
          createSimpleMessageComponent(
            HtmlChunk.raw(
              SpaceBundle.message("chat.review.deleted.branch", HtmlChunk.text(details.branch).bold()) // NON-NLS
            ).wrapWith(html()).toString()
          )
        )
        is ReviewTitleChangedEvent -> EventMessagePanel(
          createSimpleMessageComponent(
            HtmlChunk.raw(
              SpaceBundle.message(
                "chat.review.title.changed",
                HtmlChunk.text(details.oldTitle).bold(), // NON-NLS
                HtmlChunk.text(details.newTitle).bold()  // NON-NLS
              )
            ).wrapWith(html()).toString()
          )
        )
        is MCMessage -> EventMessagePanel(createSimpleMessageComponent(item.text))
        else -> createUnsupportedMessageTypePanel(item.link)
      }
    return Item(
      item.author.asUser?.let { user -> avatarProvider.getIcon(user) } ?: resizeIcon(SpaceIcons.Main, avatarProvider.iconSize.get()),
      MessageTitleComponent(lifetime, item, server),
      createEditableContent(component, item)
    )
  }

  private fun ReviewRevisionsChangedEvent.createComponent(review: CodeReviewRecord): JComponent {
    val commitsCount = commits.size
    @Nls val text = when (changeType) {
      ReviewRevisionsChangedType.Created -> SpaceBundle.message("chat.code.review.created.message", commitsCount)
      ReviewRevisionsChangedType.Added -> SpaceBundle.message("chat.commits.added.message", commitsCount)
      ReviewRevisionsChangedType.Removed -> SpaceBundle.message("chat.commits.removed.message", commitsCount)
    }

    val message = createSimpleMessageComponent(text)

    val content = JPanel(VerticalLayout(JBUI.scale(5))).apply {
      isOpaque = false
      add(message)
      commits.resolveAll().forEach { commit ->
        val location = when (changeType) {
          ReviewRevisionsChangedType.Removed -> {
            Navigator.p.project(review.project).revision(commit.repositoryName, commit.revision)
          }
          else -> {
            Navigator.p.project(review.project)
              .reviewFiles(review.number, revisions = listOf(commit.revision))
          }
        }.absoluteHref(server)
        add(LinkLabel<Any?>(
          commit.message?.let { getCommitMessageSubject(it) } ?: SpaceBundle.message("chat.untitled.commit.label"), // NON-NLS
          AllIcons.Vcs.CommitNode,
          LinkListener { _, _ ->
            BrowserUtil.browse(location)
          }
        ))
      }
    }

    return EventMessagePanel(content)
  }

  private fun createUnsupportedMessageTypePanel(messageLink: String?): JComponent {
    val description = if (messageLink != null) {
      SpaceBundle.message("chat.unsupported.message.type.with.link", messageLink)
    }
    else {
      SpaceBundle.message("chat.unsupported.message.type")
    }
    return JBLabel(description, AllIcons.General.Warning, SwingConstants.LEFT).setCopyable(true)
  }

  private fun createEditableContent(content: JComponent, message: SpaceChatItem): JComponent {
    val submittableModel = object : SubmittableTextFieldModelBase("") {
      override fun submit() {
        val editingVm = message.editingVm.value
        val newText = document.text
        if (editingVm != null) {
          val id = editingVm.message.id
          launch(lifetime, Ui) {
            val chat = editingVm.channel.awaitLoaded(lifetime)
            if (newText.isBlank()) {
              chat?.deleteMessage(id)
            }
            else {
              chat?.alterMessage(id, newText)
            }
          }
        }
        message.stopEditing()
      }
    }

    val editingStateModel = SingleValueModelImpl(false)
    message.editingVm.forEach(lifetime) { editingVm ->
      if (editingVm == null) {
        editingStateModel.value = false
        return@forEach
      }
      val workspace = SpaceWorkspaceComponent.getInstance().workspace.value ?: return@forEach
      runWriteAction {
        submittableModel.document.setText(workspace.completion.editable(editingVm.message.text))
      }
      editingStateModel.value = true
    }
    return ToggleableContainer.create(editingStateModel, { content }, {
      SubmittableTextField(SpaceBundle.message("chat.message.edit.action.text"), submittableModel, onCancel = { message.stopEditing() })
    })
  }

  private fun createDiff(
    project: Project,
    discussion: CodeDiscussionRecord,
    resolved: Property<Boolean>,
    thread: M2ChannelVm
  ): JComponent? {
    val collapseButton = InlineIconButton(AllIcons.General.CollapseComponent, AllIcons.General.CollapseComponentHover)
    val expandButton = InlineIconButton(AllIcons.General.ExpandComponent, AllIcons.General.ExpandComponentHover)

    val fileNameComponent = JBUI.Panels.simplePanel(
      createFileNameComponent(discussion.anchor.filename!!, collapseButton, expandButton, resolved)
    ).apply {
      isOpaque = false
      border = JBUI.Borders.empty(8)
    }

    val diffEditorComponent = when (val snippet = discussion.snippet!!) {
      is CodeDiscussionSnippet.PlainSnippet -> {
        return null
      }
      is CodeDiscussionSnippet.InlineDiffSnippet -> createDiffComponent(project, discussion.anchor, snippet)
    }.apply {
      border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }

    val snapshotComponent = JPanel(VerticalLayout(0)).apply {
      isOpaque = true
      background = EditorColorsManager.getInstance().globalScheme.defaultBackground
      border = RoundedLineBorder(CONTRAST_BORDER_COLOR, IdeBorderFactory.BORDER_ROUNDNESS)
      add(fileNameComponent)
      add(diffEditorComponent, VerticalLayout.FILL_HORIZONTAL)
    }
    val reviewCommentComponent = createSimpleMessageComponent("")

    val panel = JPanel(VerticalLayout(UI.scale(4))).apply {
      isOpaque = false
      add(snapshotComponent, VerticalLayout.FILL_HORIZONTAL)
      add(reviewCommentComponent, VerticalLayout.FILL_HORIZONTAL)
    }

    thread.mvms.forEach(lifetime) {
      val comment = it.messages.first()
      reviewCommentComponent.setBody(processItemText(comment.message.text))
      reviewCommentComponent.repaint()
    }
    val threadComponent = createThreadComponent(project, lifetime, thread, withFirst = false)

    return JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      add(panel, VerticalLayout.FILL_HORIZONTAL)
      add(threadComponent, VerticalLayout.FILL_HORIZONTAL)
      addDiscussionExpandCollapseHandling(
        this,
        listOf(threadComponent, reviewCommentComponent, diffEditorComponent),
        collapseButton,
        expandButton,
        resolved
      )
    }
  }

  private fun createFileNameComponent(
    filePath: String,
    collapseButton: InlineIconButton,
    expandButton: InlineIconButton,
    resolved: Property<Boolean>
  ): JComponent {
    val name = PathUtil.getFileName(filePath)
    val parentPath = PathUtil.getParentPath(filePath)
    val nameLabel = JBLabel(name, null, SwingConstants.LEFT).setCopyable(true)

    val resolvedLabel = JBLabel(SpaceBundle.message("chat.message.resolved.text"), UIUtil.ComponentStyle.SMALL).apply {
      foreground = UIUtil.getContextHelpForeground()
      background = UIUtil.getPanelBackground()
      isOpaque = true
    }

    return NonOpaquePanel(HorizontalLayout(JBUI.scale(5))).apply {
      add(nameLabel)

      if (parentPath.isNotBlank()) {
        add(JBLabel(parentPath).apply {
          foreground = UIUtil.getContextHelpForeground()
          setCopyable(true)
        })
      }
      add(resolvedLabel)
      add(collapseButton)
      add(expandButton)

      resolved.forEach(lifetime) {
        resolvedLabel.isVisible = it
        revalidate()
        repaint()
      }
    }
  }

  private fun createSimpleMessageComponent(message: SpaceChatItem): HtmlEditorPane = createSimpleMessageComponent(message.text)

  private fun createSimpleMessageComponent(@Nls text: String): HtmlEditorPane = HtmlEditorPane().apply {
    setBody(processItemText(text))
  }

  @NlsSafe
  private fun processItemText(@NlsSafe text: String) = MentionConverter.html(text, server)

  // TODO: Extract it to code-review module
  private fun addDiscussionExpandCollapseHandling(
    parentComponent: JComponent,
    componentsToHide: Collection<JComponent>,
    collapseButton: InlineIconButton,
    expandButton: InlineIconButton,
    resolved: Property<Boolean>
  ) {
    val collapseModel = SingleValueModelImpl(true)
    collapseButton.actionListener = ActionListener { collapseModel.value = true }
    expandButton.actionListener = ActionListener { collapseModel.value = false }

    resolved.forEach(lifetime) {
      collapseModel.value = it
    }

    fun stateUpdated(collapsed: Boolean) {
      collapseButton.isVisible = resolved.value && !collapsed
      expandButton.isVisible = resolved.value && collapsed

      val areComponentsVisible = !resolved.value || !collapsed
      componentsToHide.forEach { it.isVisible = areComponentsVisible }
      parentComponent.revalidate()
      parentComponent.repaint()
    }

    collapseModel.addValueUpdatedListener {
      stateUpdated(it)
    }
    stateUpdated(true)
  }


  private class EventMessagePanel(content: JComponent) : BorderLayoutPanel() {
    private val lineColor = Color(22, 125, 255, 51)

    private val lineWidth
      get() = JBUI.scale(6)
    private val lineCenterX
      get() = lineWidth / 2
    private val yRoundOffset
      get() = lineWidth / 2

    init {
      addToCenter(content)
      isOpaque = false
      border = JBUI.Borders.empty(yRoundOffset, 15, yRoundOffset, 0)
    }

    override fun paint(g: Graphics?) {
      super.paint(g)

      with(g as Graphics2D) {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        color = lineColor
        stroke = BasicStroke(lineWidth.toFloat() / 2 + 1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL)

        drawLine(lineCenterX, yRoundOffset, lineCenterX, height - yRoundOffset)
      }
    }
  }

  internal class Item(avatar: Icon, private val title: MessageTitleComponent, content: JComponent) : HoverableJPanel() {
    companion object {
      val AVATAR_GAP: Int
        get() = UI.scale(8)
    }

    init {
      layout = BorderLayout()
      val avatarPanel = BorderLayoutPanel().apply {
        isOpaque = false
        border = JBUI.Borders.empty()
        addToTop(userAvatar(avatar))
      }
      val rightPart = JPanel(VerticalLayout(JBUI.scale(5))).apply {
        isOpaque = false
        border = JBUI.Borders.emptyLeft(AVATAR_GAP)
        add(title, VerticalLayout.FILL_HORIZONTAL)
        add(content, VerticalLayout.FILL_HORIZONTAL)
      }
      isOpaque = false
      border = JBUI.Borders.empty(10, 0)
      add(avatarPanel, BorderLayout.WEST)
      add(rightPart, BorderLayout.CENTER)
    }

    private fun userAvatar(avatar: Icon) = LinkLabel<Any>("", avatar)

    override fun hoverStateChanged(isHovered: Boolean) {
      title.actionsPanel.isVisible = isHovered
      title.revalidate()
      title.repaint()
    }
  }
}