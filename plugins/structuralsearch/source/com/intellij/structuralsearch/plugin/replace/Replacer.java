package com.intellij.structuralsearch.plugin.replace;

import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.plugin.replace.impl.ReplacerImpl;
import com.intellij.structuralsearch.MatchResult;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 4, 2004
 * Time: 9:19:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class Replacer extends ReplacerImpl {
  public Replacer(Project _project, ReplaceOptions _options) {
    super(_project,_options);
  }

  public String testReplace(String in, String what, String by, ReplaceOptions _options) {
    return testReplace(in, what, by, _options,false);
  }

  public String testReplace(String in, String what, String by, ReplaceOptions _options, boolean filePattern) {
    return super.testReplace(in, what, by, _options, filePattern);
  }

  public void replaceAll(final List<ReplacementInfo> resultPtrList) {
    super.replaceAll(resultPtrList);
  }

  public void replace(final ReplacementInfo info) {
    super.replace(info);
  }

  public ReplacementInfo buildReplacement(MatchResult result) {
    return super.buildReplacement(result);
  }
}
