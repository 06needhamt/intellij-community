/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import java.util.List;
import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class MultiMap<K, V> extends FactoryMap<K, List<V>> {
  
  protected List<V> create(final K key) {
    return new ArrayList<V>();
  }

  public void putValue(K key, V value) {
    final List<V> list = get(key);
    list.add(value);
  }

  public boolean isEmpty() {
    for(List<V> valueList: myMap.values()) {
      if (!valueList.isEmpty()) {
        return false;
      }
    }
    return true;    
  }

  public List<V> remove(final K k) {
    return myMap.remove(k);
  }
}
