package com.intellij.openapi.components;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface StateStorage {
  @Nullable
  <T> T getState(final Object component, final String componentName, Class<T> stateClass, @Nullable T mergeInto) throws StateStorageException;
  void setState(Object component, final String componentName, Object state) throws StateStorageException;
  boolean hasState(final Object component, final String componentName, final Class<?> aClass) throws StateStorageException;


  List<VirtualFile> getAllStorageFiles();

  boolean needsSave() throws StateStorageException;
  void save() throws StateStorageException;



  class StateStorageException extends Exception {
    public StateStorageException() {
    }

    public StateStorageException(final String message) {
      super(message);
    }

    public StateStorageException(final String message, final Throwable cause) {
      super(message, cause);
    }

    public StateStorageException(final Throwable cause) {
      super(cause);
    }
  }
}
