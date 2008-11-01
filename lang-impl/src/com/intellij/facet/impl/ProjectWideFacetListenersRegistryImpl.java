/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.ProjectTopics;
import com.intellij.facet.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class ProjectWideFacetListenersRegistryImpl extends ProjectWideFacetListenersRegistry {
  private Map<FacetTypeId, EventDispatcher<ProjectWideFacetListener>> myDispatchers = new HashMap<FacetTypeId, EventDispatcher<ProjectWideFacetListener>>();
  private Map<FacetTypeId, WeakHashMap<Facet, Boolean>> myFacetsByType = new HashMap<FacetTypeId, WeakHashMap<Facet, Boolean>>();
  private Map<Module, MessageBusConnection> myModule2Connection = new HashMap<Module, MessageBusConnection>();
  private FacetManagerAdapter myFacetListener;
  private EventDispatcher<ProjectWideFacetListener> myAllFacetsListener = EventDispatcher.create(ProjectWideFacetListener.class);

  public ProjectWideFacetListenersRegistryImpl(MessageBus messageBus) {
    myFacetListener = new MyFacetManagerAdapter();
    messageBus.connect().subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      public void moduleAdded(Project project, Module module) {
        onModuleAdded(module);
      }

      public void beforeModuleRemoved(final Project project, final Module module) {
        Facet[] allFacets = FacetManager.getInstance(module).getAllFacets();
        for (Facet facet : allFacets) {
          onFacetRemoved(facet, true);
        }
      }

      public void moduleRemoved(Project project, Module module) {
        onModuleRemoved(module);
      }
    });
  }

  private void onModuleRemoved(final Module module) {
    final MessageBusConnection connection = myModule2Connection.remove(module);
    if (connection != null) {
      connection.disconnect();
    }

    final FacetManager facetManager = FacetManager.getInstance(module);
    final Facet[] facets = facetManager.getAllFacets();
    for (Facet facet : facets) {
      onFacetRemoved(facet, false);
    }
  }

  private void onModuleAdded(final Module module) {
    final FacetManager facetManager = FacetManager.getInstance(module);
    final Facet[] facets = facetManager.getAllFacets();
    for (Facet facet : facets) {
      onFacetAdded(facet);
    }
    final MessageBusConnection connection = module.getMessageBus().connect();
    myModule2Connection.put(module, connection);
    connection.subscribe(FacetManager.FACETS_TOPIC, myFacetListener);
  }

  private void onFacetRemoved(final Facet facet, final boolean before) {
    final FacetTypeId typeId = facet.getTypeId();
    WeakHashMap<Facet, Boolean> facets = myFacetsByType.get(typeId);
    boolean lastFacet;
    if (facets != null) {
      facets.remove(facet);
      lastFacet = facets.isEmpty();
      if (lastFacet) {
        myFacetsByType.remove(typeId);
      }
    }
    else {
      lastFacet = true;
    }
    final EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(typeId);
    if (dispatcher != null) {
      if (before) {
        //noinspection unchecked
        dispatcher.getMulticaster().beforeFacetRemoved(facet);
      }
      else {
        //noinspection unchecked
        dispatcher.getMulticaster().facetRemoved(facet);
        if (lastFacet) {
          dispatcher.getMulticaster().allFacetsRemoved();
        }
      }
    }

    if (before) {
      getAllFacetsMulticaster().beforeFacetRemoved(facet);
    }
    else {
      getAllFacetsMulticaster().facetRemoved(facet);
      if (myFacetsByType.isEmpty()) {
        getAllFacetsMulticaster().allFacetsRemoved();
      }
    }
  }

  private ProjectWideFacetListener<Facet> getAllFacetsMulticaster() {
    //noinspection unchecked
    return myAllFacetsListener.getMulticaster();
  }

  private void onFacetAdded(final Facet facet) {
    boolean firstFacet = myFacetsByType.isEmpty();
    final FacetTypeId typeId = facet.getTypeId();
    WeakHashMap<Facet, Boolean> facets = myFacetsByType.get(typeId);
    if (facets == null) {
      facets = new WeakHashMap<Facet, Boolean>();
      myFacetsByType.put(typeId, facets);
    }
    boolean firstFacetOfType = facets.isEmpty();
    facets.put(facet, true);

    if (firstFacet) {
      getAllFacetsMulticaster().firstFacetAdded();
    }
    getAllFacetsMulticaster().facetAdded(facet);
    
    final EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(typeId);
    if (dispatcher != null) {
      if (firstFacetOfType) {
        dispatcher.getMulticaster().firstFacetAdded();
      }
      //noinspection unchecked
      dispatcher.getMulticaster().facetAdded(facet);
    }
  }

  private void onFacetChanged(final Facet facet) {
    final EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(facet.getTypeId());
    if (dispatcher != null) {
      //noinspection unchecked
      dispatcher.getMulticaster().facetConfigurationChanged(facet);
    }
    getAllFacetsMulticaster().facetConfigurationChanged(facet);
  }

  public <F extends Facet> void registerListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener) {
    EventDispatcher<ProjectWideFacetListener> dispatcher = myDispatchers.get(typeId);
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(ProjectWideFacetListener.class);
      myDispatchers.put(typeId, dispatcher);
    }
    dispatcher.addListener(listener);
  }

  public <F extends Facet> void unregisterListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener) {
    myDispatchers.get(typeId).removeListener(listener);
  }

  public <F extends Facet> void registerListener(@NotNull final FacetTypeId<F> typeId, @NotNull final ProjectWideFacetListener<? extends F> listener,
                                                 @NotNull final Disposable parentDisposable) {
    registerListener(typeId, listener);
    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        unregisterListener(typeId, listener);
      }
    });
  }

  public void registerListener(@NotNull final ProjectWideFacetListener<Facet> listener) {
    myAllFacetsListener.addListener(listener);
  }

  public void unregisterListener(@NotNull final ProjectWideFacetListener<Facet> listener) {
    myAllFacetsListener.removeListener(listener);
  }

  public void registerListener(@NotNull final ProjectWideFacetListener<Facet> listener, @NotNull final Disposable parentDisposable) {
    myAllFacetsListener.addListener(listener, parentDisposable);
  }

  private class MyFacetManagerAdapter extends FacetManagerAdapter {

    public void facetAdded(@NotNull Facet facet) {
      onFacetAdded(facet);
    }

    public void beforeFacetRemoved(@NotNull final Facet facet) {
      onFacetRemoved(facet, true);
    }

    public void facetRemoved(@NotNull Facet facet) {
      onFacetRemoved(facet, false);
    }

    public void facetConfigurationChanged(@NotNull final Facet facet) {
      onFacetChanged(facet);
    }

  }
}
