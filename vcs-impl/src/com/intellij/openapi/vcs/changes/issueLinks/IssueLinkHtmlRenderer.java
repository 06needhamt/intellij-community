package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.IssueNavigationConfiguration;
import com.intellij.xml.util.XmlTagUtilBase;
import com.intellij.util.ui.UIUtil;

import java.util.List;

/**
 * @author yole
 */
public class IssueLinkHtmlRenderer {
  private IssueLinkHtmlRenderer() {
  }

  public static String formatTextIntoHtml(final Project project, final String c) {
    return "<html><head>" + UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) + "</head><body>" +
           formatTextWithLinks(project, c) + "</body></html>";
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String formatTextWithLinks(final Project project, final String c) {
    if (c == null) return "";
    String comment = XmlTagUtilBase.escapeString(c, false);

    StringBuilder commentBuilder = new StringBuilder();
    IssueNavigationConfiguration config = IssueNavigationConfiguration.getInstance(project);
    final List<IssueNavigationConfiguration.LinkMatch> list = config.findIssueLinks(comment);
    int pos = 0;
    for(IssueNavigationConfiguration.LinkMatch match: list) {
      TextRange range = match.getRange();
      commentBuilder.append(comment.substring(pos, range.getStartOffset())).append("<a href=\"").append(match.getTargetUrl()).append("\">");
      commentBuilder.append(range.substring(comment)).append("</a>");
      pos = range.getEndOffset();
    }
    commentBuilder.append(comment.substring(pos));
    comment = commentBuilder.toString();

    return comment.replace("\n", "<br>");
  }
}