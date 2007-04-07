/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * Brace matcher for Groovy language
 *
 * @author Ilya.Sergey
 */
public class GroovyBraceMatcher implements PairedBraceMatcher {

  private static final BracePair[] PAIRS = new BracePair[]{
          new BracePair('(', GroovyTokenTypes.mLPAREN, ')', GroovyTokenTypes.mRPAREN, false),
          new BracePair('[', GroovyTokenTypes.mLBRACK, ']', GroovyTokenTypes.mRBRACK, false),
          new BracePair('{', GroovyTokenTypes.mLCURLY, '}', GroovyTokenTypes.mRCURLY, true),

          new BracePair('"', GroovyTokenTypes.mGSTRING_SINGLE_BEGIN, '"', GroovyTokenTypes.mGSTRING_SINGLE_END, false),
          new BracePair('/', GroovyTokenTypes.mREGEX_BEGIN, '/', GroovyTokenTypes.mREGEX_END, false)
  };

  public BracePair[] getPairs() {
    return PAIRS;
  }
}