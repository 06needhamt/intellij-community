package com.intellij.util.containers;

import com.intellij.openapi.util.Comparing;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public final class ConcurrentWeakValueHashMap<K,V> implements ConcurrentMap<K,V> {
  private final ConcurrentHashMap<K,MyReference<K,V>> myMap = new ConcurrentHashMap<K, MyReference<K,V>>();
  private ReferenceQueue<V> myQueue = new ReferenceQueue<V>();

  public ConcurrentWeakValueHashMap(final Map<K, V> map) {
    this();
    putAll(map);
  }

  public ConcurrentWeakValueHashMap() {
  }

  private static class MyReference<K,T> extends WeakReference<T> {
    private final K key;
    public MyReference(K key, T referent, ReferenceQueue<? super T> q) {
      super(referent, q);
      this.key = key;
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final MyReference that = (MyReference)o;

      if (!key.equals(that.key)) return false;
      if (!Comparing.equal(get(), that.get())) return false;

      return true;
    }

    public int hashCode() {
      return key.hashCode();
    }
  }

  private void processQueue() {
    while(true){
      MyReference<K,V> ref = (MyReference<K,V>)myQueue.poll();
      if (ref == null) {
        return;
      }
      if (myMap.get(ref.key) == ref){
        myMap.remove(ref.key);
      }
    }
  }

  public V get(Object key) {
    MyReference<K,V> ref = myMap.get(key);
    if (ref == null) return null;
    return ref.get();
  }

  public V put(K key, V value) {
    processQueue();
    MyReference<K,V> oldRef = myMap.put(key, new MyReference<K,V>(key, value, myQueue));
    return oldRef != null ? oldRef.get() : null;
  }

  public V putIfAbsent(K key, V value) {
    while (true) {
      processQueue();
      MyReference<K, V> newRef = new MyReference<K, V>(key, value, myQueue);
      MyReference<K,V> oldRef = myMap.putIfAbsent(key, newRef);
      if (oldRef == null) return null;
      final V oldVal = oldRef.get();
      if (oldVal == null) {
        if (myMap.replace(key, oldRef, newRef)) return null;
      }
      else {
        return oldVal;
      }
    }
  }

  public boolean remove(final Object key, final Object value) {
    processQueue();
    return myMap.remove(key, new MyReference<K,V>((K)key, (V)value, myQueue));
  }

  public boolean replace(final K key, final V oldValue, final V newValue) {
    processQueue();
    return myMap.replace(key, new MyReference<K,V>(key, oldValue, myQueue), new MyReference<K,V>(key, newValue, myQueue));
  }

  public V replace(final K key, final V value) {
    processQueue();
    MyReference<K, V> ref = myMap.replace(key, new MyReference<K, V>(key, value, myQueue));
    return ref == null ? null : ref.get();
  }

  public V remove(Object key) {
    processQueue();
    MyReference<K,V> ref = myMap.remove(key);
    return ref != null ? ref.get() : null;
  }

  public void putAll(Map<? extends K, ? extends V> t) {
    for (K k : t.keySet()) {
      V v = t.get(k);
      if (v != null) {
        put(k, v);
      }
    }
  }

  public void clear() {
    myMap.clear();
  }

  public int size() {
    return myMap.size(); //?
  }

  public boolean isEmpty() {
    return myMap.isEmpty(); //?
  }

  public boolean containsKey(Object key) {
    return get(key) != null;
  }

  public boolean containsValue(Object value) {
    throw new RuntimeException("method not implemented");
  }

  public Set<K> keySet() {
    return myMap.keySet();
  }

  public Collection<V> values() {
    List<V> result = new ArrayList<V>();
    final Collection<MyReference<K, V>> refs = myMap.values();
    for (MyReference<K, V> ref : refs) {
      final V value = ref.get();
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  public Set<Entry<K, V>> entrySet() {
    throw new RuntimeException("method not implemented");
  }
}
