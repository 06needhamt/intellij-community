/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: anna
 * Date: 19-Apr-2006
 */
public class TextEditorHighlightingPassRegistrarImpl extends TextEditorHighlightingPassRegistrar {

  private Map<TextEditorHighlightingPassFactory, Pair<Anchor, Integer>> myRegisteredPasses = null;

  public void registerTextEditorHighlightingPass(TextEditorHighlightingPassFactory factory, Anchor anchor, int anchorPass) {
    if (myRegisteredPasses == null){
      myRegisteredPasses = new HashMap<TextEditorHighlightingPassFactory, Pair<Anchor, Integer>>();
    }
    myRegisteredPasses.put(factory, Pair.create(anchor, anchorPass));
  }

  public TextEditorHighlightingPass[] modifyHighlightingPasses(final List<TextEditorHighlightingPass> passes,
                                                               final PsiFile psiFile,
                                                               final Editor editor) {
    if (myRegisteredPasses == null){
      return passes.toArray(new TextEditorHighlightingPass[passes.size()]);
    }
    List<TextEditorHighlightingPass> result = new ArrayList<TextEditorHighlightingPass>(passes);
    for (TextEditorHighlightingPassFactory factory : myRegisteredPasses.keySet()) {
      final TextEditorHighlightingPass editorHighlightingPass = factory.createHighlightingPass(psiFile, editor);
      if (editorHighlightingPass == null) continue;
      final Pair<Anchor, Integer> location = myRegisteredPasses.get(factory);
      final Anchor anchor = location.first;
      if (anchor == Anchor.FIRST){
        result.add(0, editorHighlightingPass);
      } else if (anchor == Anchor.LAST){
        result.add(editorHighlightingPass);
      } else {
        final int passId = location.second.intValue();
        int anchorPassIdx = -1;
        for (int idx = 0; idx < result.size(); idx++) {
          final TextEditorHighlightingPass highlightingPass = result.get(idx);
          if (highlightingPass.getPassId() == passId){
            anchorPassIdx = idx;
            break;
          }
        }
        if (anchorPassIdx != -1){
          if (anchor == Anchor.BEFORE){
            result.add(Math.max(0, anchorPassIdx - 1), editorHighlightingPass);
          } else {
            result.add(anchorPassIdx +1, editorHighlightingPass);
          }
        } else {
          result.add(editorHighlightingPass);
        }
      }
    }
    return result.toArray(new TextEditorHighlightingPass[result.size()]);
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "TextEditorHighlightingPassRegistrarImpl";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void projectOpened() {

  }

  public void projectClosed() {
  }
}
