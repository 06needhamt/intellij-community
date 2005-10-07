/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.util.objectTree.ObjectTree;
import com.intellij.openapi.util.objectTree.ObjectTreeAction;
import com.intellij.openapi.Disposable;

public class Disposer {

  private static final ObjectTree ourTree = new ObjectTree();

  private static final ObjectTreeAction ourDisposeAction = new ObjectTreeAction() {
    public void execute(final Object each) {
      ((Disposable)each).dispose();
    }
  };

  private Disposer() {
  }

  public static void register(Disposable parent, Disposable child) {
    assert parent != null : "null parent disposable for " + child;
    assert child != null : "null child disposable for " + parent;
    assert parent != child : " Cannot register to intself";

    ourTree.register(parent, child);
  }

  public static void dispose(Disposable disposable) {
    ourTree.executeAll(disposable, true, ourDisposeAction);
  }

  public static void disposeChildAndReplace(Disposable toDipose, Disposable toReplace) {
    ourTree.executeChildAndReplace(toDipose, toReplace, true, ourDisposeAction);
  }

  public static boolean isRegistered(Disposable aDisposable) {
    return ourTree.isRegistered(aDisposable);
  }

  public static boolean isRoot(Disposable disposable) {
    return ourTree.isRoot(disposable);
  }

  static ObjectTree getTree() {
    return ourTree;
  }
}
