/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy;

import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.Commenter;
import com.intellij.lang.Language;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.GroovyFormattingModelBuilder;
import org.jetbrains.plugins.groovy.highlighter.GroovyBraceMatcher;
import org.jetbrains.plugins.groovy.highlighter.GroovyCommenter;
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.folding.GroovyFoldingBuilder;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition;
import org.jetbrains.plugins.groovy.annotator.GroovyAnnotator;
import org.jetbrains.plugins.groovy.findUsages.GroovyFindUsagesrovider;

/**
 * All main properties for Groovy language
 *
 * @author Ilya.Sergey
 */
public class GroovyLanguage extends Language {
  public GroovyLanguage() {
    super("Groovy");
  }

  public ParserDefinition getParserDefinition() {
    return new GroovyParserDefinition();
  }

  public FoldingBuilder getFoldingBuilder() {
    return new GroovyFoldingBuilder();
  }

  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, final VirtualFile virtualFile) {
    return new GroovySyntaxHighlighter();
  }

  @Nullable
  public Commenter getCommenter() {
    return new GroovyCommenter();
  }

  @Nullable
  public Annotator getAnnotator() {
    return GroovyAnnotator.INSTANCE;
  }

  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    return GroovyFindUsagesrovider.INSTANCE;
  }

  @Nullable
  public FormattingModelBuilder getFormattingModelBuilder() {
    return new GroovyFormattingModelBuilder();
  }

  @Nullable
  public PairedBraceMatcher getPairedBraceMatcher() {
    return new GroovyBraceMatcher();
  }

}
