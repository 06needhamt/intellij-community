package com.intellij.injected.editor;

import gnu.trove.THashMap;

import java.util.Collection;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;

/**
 * @author cdr
*/
class ListenerWrapperMap<T extends EventListener> {
  Map<T,T> myListener2WrapperMap = new THashMap<T, T>();

  void registerWrapper(T listener, T wrapper) {
    myListener2WrapperMap.put(listener, wrapper);
  }
  T removeWrapper(T listener) {
    return myListener2WrapperMap.remove(listener);
  }

  public Collection<T> wrappers() {
    return myListener2WrapperMap.values();
  }

  public String toString() {
    return new HashMap<T,T>(myListener2WrapperMap).toString();
  }

  public void clear() {
    myListener2WrapperMap.clear();
  }
}
