package com.intellij.openapi.util;


import com.intellij.util.containers.LockPoolSynchronizedMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class UserDataHolderBase implements UserDataHolderEx, Cloneable {
  private volatile Map<Key, Object> myUserMap = null;
  private static final Object WRITE_LOCK = new Object();

  private static final Key<Map<Key, Object>> COPYABLE_USER_MAP_KEY = Key.create("COPYABLE_USER_MAP_KEY");

  protected Object clone() {
    try {
      UserDataHolderBase clone = (UserDataHolderBase)super.clone();
      clone.myUserMap = null;
      copyCopyableDataTo(clone);
      return clone;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);

    }
  }

  public String getUserDataString() {
    final Map<Key, Object> userMap = myUserMap;
    if (userMap == null) {
      return "";
    }
    final Map copyableMap = (Map)userMap.get(COPYABLE_USER_MAP_KEY);
    if (copyableMap == null) {
      return userMap.toString();
    }
    else {
      return userMap.toString() + copyableMap.toString();
    }
  }

  public void copyUserDataTo(UserDataHolderBase other) {
    if (myUserMap == null) {
      other.myUserMap = null;
    }
    else {
      LockPoolSynchronizedMap<Key, Object> fresh = createMap();
      fresh.putAll(myUserMap);
      other.myUserMap = fresh;
    }
  }

  public <T> T getUserData(Key<T> key) {
    final Map<Key, Object> map = myUserMap;
    return map == null ? null : (T)map.get(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    synchronized (WRITE_LOCK) {
      Map<Key, Object> map = myUserMap;
      if (map == null) {
        if (value == null) return;
        myUserMap = map = createMap();
      }
      if (value == null) {
        map.remove(key);
      }
      else {
        map.put(key, value);
      }
    }
  }

  private static LockPoolSynchronizedMap<Key, Object> createMap() {
    return new LockPoolSynchronizedMap<Key, Object>(2, 0.9f);
  }

  public <T> T getCopyableUserData(Key<T> key) {
    return getCopyableUserDataImpl(key);
  }

  protected <T> T getCopyableUserDataImpl(Key<T> key) {
    Map map = getUserData(COPYABLE_USER_MAP_KEY);
    return map == null ? null : (T)map.get(key);
  }

  public <T> void putCopyableUserData(Key<T> key, T value) {
    putCopyableUserDataImpl(key, value);
  }

  protected <T> void putCopyableUserDataImpl(Key<T> key, T value) {
    synchronized (WRITE_LOCK) {
      Map<Key, Object> map = getUserData(COPYABLE_USER_MAP_KEY);
      if (map == null) {
        if (value == null) return;
        map = new LockPoolSynchronizedMap<Key, Object>(1, 0.9f);
        putUserData(COPYABLE_USER_MAP_KEY, map);
      }

      if (value != null) {
        map.put(key, value);
      }
      else {
        map.remove(key);
        if (map.isEmpty()) {
          putUserData(COPYABLE_USER_MAP_KEY, null);
        }
      }
    }
  }

  @NotNull
  public <T> T putUserDataIfAbsent(@NotNull final Key<T> key, @NotNull final T value) {
    synchronized (WRITE_LOCK) {
      Map<Key, Object> map = myUserMap;
      if (map == null) {
        myUserMap = map = createMap();
        map.put(key, value);
        return value;
      }
      T prev = (T)map.get(key);
      if (prev == null) {
        map.put(key, value);
        return value;
      }
      else {
        return prev;
      }
    }
  }

  public <T> boolean replace(@NotNull Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    synchronized (WRITE_LOCK) {
      Map<Key, Object> map = myUserMap;
      if (map == null) {
        if (newValue == null) return oldValue == null;
        myUserMap = map = createMap();
        map.put(key, newValue);
        return true;
      }
      T prev = (T)map.get(key);
      if (!Comparing.equal(oldValue, prev)) {
        return false;
      }
      if (newValue == null) {
        map.remove(key);
      }
      else {
        map.put(key, newValue);
      }
      return true;
    }
  }

  public void copyCopyableDataTo(UserDataHolderBase clone) {
    Map<Key, Object> copyableMap = getUserData(COPYABLE_USER_MAP_KEY);
    if (copyableMap != null) {
      copyableMap = ((LockPoolSynchronizedMap)copyableMap).clone();
    }
    clone.putUserData(COPYABLE_USER_MAP_KEY, copyableMap);
  }

  protected void clearUserData() {
    synchronized (WRITE_LOCK) {
      myUserMap = null;
    }
  }
}
