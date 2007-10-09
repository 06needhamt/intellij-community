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
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MultiValuesMap<Key, Value>{
  private final Map<Key, Collection<Value>> myBaseMap;

  public MultiValuesMap() {
    this(false);
  }

  public MultiValuesMap(boolean ordered) {
    myBaseMap = ordered ? new LinkedHashMap<Key, Collection<Value>>() : new HashMap<Key, Collection<Value>>();
  }

  public void putAll(Key key, List<Value> values) {
    for (Value value : values) {
      put(key, value);
    }
  }

  public void putAll(Key key, Value... values) {
    for (Value value : values) {
      put(key, value);
    }
  }

  public void put(Key key, Value value) {
    if (!myBaseMap.containsKey(key)) {
      myBaseMap.put(key, new HashSet<Value>());
    }

    myBaseMap.get(key).add(value);
  }

  @Nullable
  public Collection<Value> get(Key key){
    return myBaseMap.get(key);
  }

  public Set<Key> keySet() {
    return myBaseMap.keySet();
  }

  public Collection<Value> values() {
    Set<Value> result = new HashSet<Value>();
    for (final Collection<Value> values : myBaseMap.values()) {
      result.addAll(values);
    }

    return result;
  }

  public void remove(Key key, Value value) {
    if (!myBaseMap.containsKey(key)) return;
    final Collection<Value> values = myBaseMap.get(key);
    values.remove(value);
    if (values.isEmpty()) {
      myBaseMap.remove(key);
    }
  }

  public void clear() {
    myBaseMap.clear();
  }

  public void removeAll(final Key key) {
    myBaseMap.remove(key);
  }

  public Set<Map.Entry<Key, Collection<Value>>> entrySet() {
    return myBaseMap.entrySet();
  }

  public boolean isEmpty() {
    return myBaseMap.isEmpty();
  }

  public boolean containsKey(final Key key) {
    return myBaseMap.containsKey(key);
  }
}
