package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;

/**
 * Iterator that limits processing of specified number of nodes
 */
public final class CountingNodeIterator extends NodeIterator {
  private int index;
  private int max;
  private NodeIterator delegate;

  public CountingNodeIterator(int _max, NodeIterator _iterator) {
    max = _max;
    delegate = _iterator;
  }

  public boolean hasNext() {
    return index < max && delegate.hasNext();
  }

  public PsiElement current() {
    if (index < max)
      return delegate.current();
    return null;
  }

  public void advance() {
    ++index;
    delegate.advance();
  }

  public void rewind() {
    if (index >0) {
      -- index;
      delegate.rewind();
    }
  }

  public void reset() {
    index = 0;
    delegate.reset();
  }
}
