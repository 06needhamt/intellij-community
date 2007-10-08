/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.facet.FacetManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class FacetModelImpl extends FacetModelBase implements ModifiableFacetModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.FacetModelImpl");
  private List<Facet> myFacets = new ArrayList<Facet>();
  private Map<Facet, String> myFacet2NewName = new HashMap<Facet, String>();
  private FacetManagerImpl myManager;
  private Set<Listener> myListeners = new HashSet<Listener>();

  public FacetModelImpl(final FacetManagerImpl manager) {
    myManager = manager;
    for (Facet facet : myManager.getAllFacets()) {
      addFacet(facet);
    }
  }

  public void addFacet(Facet facet) {
    if (myFacets.contains(facet)) {
      LOG.error("Facet " + facet + " [" + facet.getTypeId() + "] is already added");
    }

    myFacets.add(facet);
    facetsChanged();
  }

  public void removeFacet(Facet facet) {
    if (myFacets == null || !myFacets.remove(facet)) {
      LOG.error("Facet " + facet + " [" + facet.getTypeId() + "] not found");
    }
    myFacet2NewName.remove(facet);
    facetsChanged();
  }

  public void rename(final Facet facet, final String newName) {
    if (!newName.equals(facet.getName())) {
      myFacet2NewName.put(facet, newName);
    } else {
      myFacet2NewName.remove(facet);
    }
    facetsChanged();
  }

  @Nullable
  public String getNewName(final Facet facet) {
    return myFacet2NewName.get(facet);
  }

  public void commit() {
    myManager.commit(this);
  }

  public boolean isModified() {
    return !new HashSet<Facet>(myFacets).equals(new HashSet<Facet>(Arrays.asList(myManager.getAllFacets()))) || !myFacet2NewName.isEmpty();
  }

  public boolean isNewFacet(final Facet facet) {
    return myFacets.contains(facet) && ArrayUtil.find(myManager.getAllFacets(), facet) == -1;
  }

  @NotNull
  public Facet[] getAllFacets() {
    return myFacets.toArray(new Facet[myFacets.size()]);
  }

  public String getFacetName(final Facet facet) {
    return myFacet2NewName.containsKey(facet) ? myFacet2NewName.get(facet) : facet.getName();
  }

  public void addListener(@NotNull final Listener listener, @Nullable Disposable parentDisposable) {
    myListeners.add(listener);
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, new Disposable() {
        public void dispose() {
          myListeners.remove(listener);
        }
      });
    }
  }

  protected void facetsChanged() {
    super.facetsChanged();
    final Listener[] all = myListeners.toArray(new Listener[myListeners.size()]);
    for (Listener each : all) {
      each.onChanged();
    }
  }
}
