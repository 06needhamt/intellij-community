// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.chat.ui.header

import circlet.code.api.CodeReviewState
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.space.chat.model.impl.SpaceChatReviewHeaderDetails
import com.intellij.space.chat.ui.SpaceChatItemComponentFactory
import com.intellij.space.chat.ui.SpaceChatPanel
import com.intellij.space.ui.resizeIcon
import com.intellij.space.vcs.review.HtmlEditorPane
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.VcsCodeReviewIcons
import libraries.coroutines.extra.Lifetime
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class SpaceChatReviewHeaderComponent(
  lifetime: Lifetime,
  details: SpaceChatReviewHeaderDetails
) : JPanel() {
  init {
    isOpaque = false
    layout = MigLayout(LC().gridGap("0", "0")
                         .insets("0", "0", "0", "0")
                         .fill()).apply {
      columnConstraints = "[][]"
    }

    val reviewStateIconPanel = BorderLayoutPanel().apply {
      isOpaque = false
      border = JBUI.Borders.emptyRight(SpaceChatItemComponentFactory.Item.AVATAR_GAP)
    }
    val headerContent = HtmlEditorPane().apply {
      font = font.deriveFont((font.size * 1.5).toFloat())
    }

    add(reviewStateIconPanel, CC().pushY())
    add(headerContent, CC().pushX().alignY("center"))

    details.title.forEach(lifetime) { newTitle ->
      headerContent.setBody(getHeaderHtml(newTitle, details.reviewKey))
    }
    details.state.forEach(lifetime) { newState ->
      if (reviewStateIconPanel.components.isNotEmpty()) {
        reviewStateIconPanel.remove(0)
      }
      reviewStateIconPanel.add(getReviewStateIcon(newState), 0)
      reviewStateIconPanel.revalidate()
      reviewStateIconPanel.repaint()
    }
  }

  private fun getReviewStateIcon(state: CodeReviewState): JComponent {
    val icon = when (state) {
      CodeReviewState.Opened -> VcsCodeReviewIcons.PullRequestOpen
      CodeReviewState.Closed, CodeReviewState.Deleted -> VcsCodeReviewIcons.PullRequestClosed
    }
    return JLabel(resizeIcon(icon, SpaceChatPanel.getChatAvatarSize().get()))
  }

  @Nls
  private fun getHeaderHtml(@Nls title: String, @NlsSafe reviewKey: String?): String {
    val builder = HtmlBuilder().append(title)
    if (reviewKey != null) {
      builder
        .nbsp()
        .append(HtmlChunk.span("color: ${ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())}").addRaw(reviewKey))
    }
    return builder.toString()
  }
}