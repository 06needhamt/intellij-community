/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiLock;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author peter
*/
class FileDescriptionCachedValueProvider<T extends DomElement> implements CachedValueProvider<DomFileElementImpl<T>>, ModificationTracker {
  private final XmlFile myXmlFile;
  private Runnable myPostRunnable;
  private boolean myFireEvents;
  private boolean myInModel;
  private Result<DomFileElementImpl<T>> myOldResult;
  private long myModCount;
  private final Condition<DomFileDescription> myCondition = new Condition<DomFileDescription>() {
    public boolean value(final DomFileDescription description) {
      return description.isMyFile(myXmlFile);
    }
  };

  private DomFileDescription<T> myFileDescription;
  private DomManagerImpl myDomManager;

  public FileDescriptionCachedValueProvider(final DomManagerImpl domManager, final XmlFile xmlFile) {
    myDomManager = domManager;
    myXmlFile = xmlFile;
  }

  public final DomFileDescription getFileDescription() {
    return myFileDescription;
  }

  final void fireEvents() {
    if (myPostRunnable != null) {
      myPostRunnable.run();
      myPostRunnable = null;
    }
  }

  final void setFireEvents() {
    myFireEvents = true;
  }

  public Result<DomFileElementImpl<T>> compute() {
    synchronized (PsiLock.LOCK) {
      if (myDomManager.getProject().isDisposed()) return new Result<DomFileElementImpl<T>>(null);
      final boolean fireEvents = myFireEvents;
      myFireEvents = false;

      if (myOldResult != null && myFileDescription != null && myFileDescription.isMyFile(myXmlFile)) {
        return myOldResult;
      }

      final XmlFile originalFile = (XmlFile)myXmlFile.getOriginalFile();
      if (originalFile != null) {
        return saveResult(myDomManager.getOrCreateCachedValueProvider(originalFile).getFileDescription(), fireEvents);
      }

      return saveResult(ContainerUtil.find(myDomManager.getFileDescriptions().keySet(), myCondition), fireEvents);
    }
  }

  final boolean isInModel() {
    return myInModel;
  }

  final void setInModel(final boolean inModel) {
    myInModel = inModel;
  }

  private Result<DomFileElementImpl<T>> saveResult(final DomFileDescription<T> description, final boolean fireEvents) {
    myInModel = false;
    final DomFileElementImpl oldValue = getOldValue();
    final DomFileDescription oldFileDescription = myFileDescription;
    final Runnable undefinedRunnable = new Runnable() {
      public void run() {
        if (oldValue != null) {
          assert oldFileDescription != null;
          myDomManager.getFileDescriptions().get(oldFileDescription).remove(oldValue);
          if (fireEvents) {
            myDomManager.fireEvent(new ElementUndefinedEvent(oldValue), false);
          }
        }
      }
    };

    myFileDescription = description;
    if (description == null) {
      myPostRunnable = undefinedRunnable;
      myOldResult = null;
      return new Result<DomFileElementImpl<T>>(null, getAllDependencyItems());
    }

    final DomFileElementImpl<T> fileElement = myDomManager.createFileElement(myXmlFile, description);

    myPostRunnable = new Runnable() {
      public void run() {
        undefinedRunnable.run();
        myDomManager.getFileDescriptions().get(myFileDescription).add(fileElement);
        if (fireEvents) {
          myDomManager.fireEvent(new ElementDefinedEvent(fileElement), false);
        }
      }
    };

    final Set<Object> deps = new HashSet<Object>(description.getDependencyItems(myXmlFile));
    deps.add(myXmlFile);
    deps.add(this);
    return myOldResult = new Result<DomFileElementImpl<T>>(fileElement, deps.toArray());
  }

  private Object[] getAllDependencyItems() {
    final Set<Object> deps = new HashSet<Object>();
    deps.add(myXmlFile);
    deps.add(this);
    for (final DomFileDescription<?> fileDescription : myDomManager.getFileDescriptions().keySet()) {
      deps.addAll(fileDescription.getDependencyItems(myXmlFile));
    }
    return deps.toArray();
  }

  @Nullable
  final DomFileElementImpl getOldValue() {
    return myOldResult != null ? myOldResult.getValue() : null;
  }

  public final void changed() {
    myModCount++;
    setFireEvents();
  }

  public long getModificationCount() {
    return myModCount;
  }
}
