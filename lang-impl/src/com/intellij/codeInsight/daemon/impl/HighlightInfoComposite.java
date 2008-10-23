/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class HighlightInfoComposite extends HighlightInfo {
  @NonNls private static final String HTML_HEADER = "<html>";
  @NonNls private static final String BODY_HEADER = "<body>";
  @NonNls private static final String HTML_FOOTER = "</html>";
  @NonNls private static final String BODY_FOOTER = "</body>";
  @NonNls private static final String LINE_BREAK = "\n<hr size=1 noshade>";

  public HighlightInfoComposite(@NotNull List<HighlightInfo> infos) {
    super(infos.get(0).type, infos.get(0).startOffset, infos.get(0).endOffset, createCompositeDescription(infos),
          createCompositeTooltip(infos));
    text = infos.get(0).text;
    highlighter = infos.get(0).highlighter;
    group = infos.get(0).group;
    quickFixActionMarkers = new ArrayList<Pair<IntentionActionDescriptor, RangeMarker>>();
    quickFixActionRanges = new ArrayList<Pair<IntentionActionDescriptor, TextRange>>();
    for (HighlightInfo info : infos) {
      if (info.quickFixActionMarkers != null) {
        quickFixActionMarkers.addAll(info.quickFixActionMarkers);
      }
      if (info.quickFixActionRanges != null) {
        quickFixActionRanges.addAll(info.quickFixActionRanges);
      }
    }
  }

  @Nullable
  private static String createCompositeDescription(List<HighlightInfo> infos) {
    StringBuilder description = new StringBuilder();
    boolean isNull = true;
    for (HighlightInfo info : infos) {
      String itemDescription = info.description;
      if (itemDescription != null) {
        itemDescription = itemDescription.trim();
        description.append(itemDescription);
        if (!itemDescription.endsWith(".")) {
          description.append('.');
        }
        description.append(' ');

        isNull = false;
      }
    }
    return isNull ? null : description.toString();
  }

  @Nullable
  private static String createCompositeTooltip(List<HighlightInfo> infos) {
    StringBuilder result = new StringBuilder();
    for (HighlightInfo info : infos) {
      String toolTip = info.toolTip;
      if (toolTip != null) {
        if (result.length() != 0) {
          result.append(LINE_BREAK);
        }
        toolTip = StringUtil.trimStart(toolTip, HTML_HEADER);
        toolTip = StringUtil.trimStart(toolTip, BODY_HEADER);
        toolTip = StringUtil.trimEnd(toolTip, HTML_FOOTER);
        toolTip = StringUtil.trimEnd(toolTip, BODY_FOOTER);
        result.append(toolTip);
      }
    }
    if (result.length() == 0) {
      return null;
    }
    result.insert(0, HTML_HEADER);
    result.append(HTML_FOOTER);
    return result.toString();
  }
}