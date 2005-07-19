/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.Comparing;

import javax.swing.*;

public final class KeyboardShortcut extends Shortcut{
  private final KeyStroke myFirstKeyStroke;
  private final KeyStroke mySecondKeyStroke;

  /**
   * @throws IllegalArgumentException if <code>firstKeyStroke</code> is <code>null</code>
   */
  public KeyboardShortcut(KeyStroke firstKeyStroke, KeyStroke secondKeyStroke){
    if (firstKeyStroke == null) {
      throw new IllegalArgumentException("firstKeystroke cannot be null");
    }
    myFirstKeyStroke = firstKeyStroke;
    mySecondKeyStroke = secondKeyStroke;
  }

  public KeyStroke getFirstKeyStroke(){
    return myFirstKeyStroke;
  }

  public KeyStroke getSecondKeyStroke(){
    return mySecondKeyStroke;
  }

  public int hashCode(){
    int hashCode=myFirstKeyStroke.hashCode();
    if(mySecondKeyStroke!=null){
      hashCode+=mySecondKeyStroke.hashCode();
    }
    return hashCode;
  }

  public boolean equals(Object obj){
    if (!(obj instanceof KeyboardShortcut)){
      return false;
    }
    KeyboardShortcut second = (KeyboardShortcut)obj;
    if (!Comparing.equal(myFirstKeyStroke, second.myFirstKeyStroke)){
      return false;
    }
    if (!Comparing.equal(mySecondKeyStroke, second.mySecondKeyStroke)){
      return false;
    }
    return true;
  }
}
