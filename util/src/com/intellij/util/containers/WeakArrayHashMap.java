/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
package com.intellij.util.containers;

/**
 * @author peter
 */
public class WeakArrayHashMap<T,V> {
  private WeakValueHashMap<T,WeakArrayHashMap<T,V>> myContinuationMap;
  private WeakValueHashMap<T,V> myValuesMap;

  private V get(T[] array, int index) {
    if (index == array.length - 1) {
      return myValuesMap != null ? myValuesMap.get(array[index]) : null;
    }

    if (myContinuationMap != null) {
      final WeakArrayHashMap<T, V> map = myContinuationMap.get(array[index]);
      if (map != null) {
        return map.get(array, index + 1);
      }
    }

    return null;
  }

  public final V get(T[] key) {
    return get(key, 0);
  }

  private void put(T[] array, int index, V value) {
    final T key = array[index];
    if (index == array.length - 1) {
      if (myValuesMap == null) {
        myValuesMap = new WeakValueHashMap<T, V>();
      }
      myValuesMap.put(key, value);
    } else {
      if (myContinuationMap == null) {
        myContinuationMap = new WeakValueHashMap<T, WeakArrayHashMap<T, V>>();
      }
      WeakArrayHashMap<T, V> weakArrayHashMap = myContinuationMap.get(key);
      if (weakArrayHashMap == null) {
        myContinuationMap.put(key, weakArrayHashMap = new WeakArrayHashMap<T, V>());
      }
      weakArrayHashMap.put(array, index + 1, value);
    }
  }

  public final void put(T[] key, V value) {
    put(key, 0, value);
  }

}
