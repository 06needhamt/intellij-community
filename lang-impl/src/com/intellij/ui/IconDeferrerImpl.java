/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;

import javax.swing.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IconDeferrerImpl extends IconDeferrer {
  private final Object LOCK = new Object();
  private final Map<Object, Icon> myIconsCache = new HashMap<Object, Icon>();

  public IconDeferrerImpl(MessageBus bus) {
    final MessageBusConnection connection = bus.connect();
    connection.setDefaultHandler(new MessageHandler() {
      public void handle(final Method event, final Object... params) {
        invalidateAllIcons();
      }
    });

    connection.subscribe(ProjectTopics.MODIFICATION_TRACKER, new PsiModificationTracker.Listener() {
      public void modificationCountChanged() {
        invalidateAllIcons();
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      public void before(final List<? extends VFileEvent> events) {
      }

      public void after(final List<? extends VFileEvent> events) {
        synchronized (LOCK) {
          myIconsCache.clear();
        }
      }
    });
  }

  private void invalidateAllIcons() {
    synchronized (LOCK) {
      for (Icon icon : myIconsCache.values()) {
        if (icon instanceof DeferredIconImpl) {
          ((DeferredIconImpl)icon).invalidate();
        }
      }
    }
  }

  public <T> Icon defer(final Icon base, final T param, final Function<T, Icon> f) {
    synchronized (LOCK) {
      if (myEvaluationIsInProgress.get().booleanValue()) {
        return f.fun(param);
      }

      Icon result = myIconsCache.get(param);
      if (result == null) {
        result = new DeferredIconImpl<T>(base, param, f);
        myIconsCache.put(param, result);
      }

      return result;
    }
  }

  private static final ThreadLocal<Boolean> myEvaluationIsInProgress = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return Boolean.FALSE;
    }
  };

  public static void evaluateDeferredInReadAction(final Runnable runnable) {
    try {
      myEvaluationIsInProgress.set(Boolean.TRUE);
      ApplicationManager.getApplication().runReadAction(runnable);
    }
    finally {
      myEvaluationIsInProgress.set(Boolean.FALSE);
    }
  }

  public static void evaluateDeferred(final Runnable runnable) {
    try {
      myEvaluationIsInProgress.set(Boolean.TRUE);
      runnable.run();
    }
    finally {
      myEvaluationIsInProgress.set(Boolean.FALSE);
    }
  }
}