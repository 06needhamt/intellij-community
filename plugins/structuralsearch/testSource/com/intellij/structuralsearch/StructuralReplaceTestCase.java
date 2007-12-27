package com.intellij.structuralsearch;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.Replacer;
import com.intellij.testFramework.IdeaTestCase;

/**
 * Created by IntelliJ IDEA.
 * User: maxim.mossienko
 * Date: Oct 11, 2005
 * Time: 10:10:48 PM
 * To change this template use File | Settings | File Templates.
 */
abstract class StructuralReplaceTestCase extends IdeaTestCase {
  protected Replacer replacer;
  protected ReplaceOptions options;
  protected String actualResult;

  protected void setUp() throws Exception {
    super.setUp();

    LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(LanguageLevel.JDK_1_4);

    options = new ReplaceOptions();
    options.setMatchOptions(new MatchOptions());
    replacer = new Replacer(myProject, null);
  }

}
