/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.facet.FacetInfo;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.impl.ui.FacetEditor;
import com.intellij.facet.impl.ui.FacetTreeModel;
import com.intellij.facet.impl.ui.ProjectConfigurableContext;
import com.intellij.facet.impl.ui.ConfigureFacetsStep;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class ProjectFacetsConfigurator implements FacetsProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ProjectFacetsConfigurator");
  private Map<Module, ModifiableFacetModel> myModels = new HashMap<Module, ModifiableFacetModel>();
  private Map<Facet, FacetEditor> myEditors = new HashMap<Facet, FacetEditor>();
  private Map<Module, FacetTreeModel> myTreeModels = new HashMap<Module, FacetTreeModel>();
  private Map<FacetInfo, Facet> myInfo2Facet = new HashMap<FacetInfo, Facet>();
  private Map<Facet, FacetInfo> myFacet2Info = new HashMap<Facet, FacetInfo>();
  private Map<Module, UserDataHolder> mySharedModuleData = new HashMap<Module, UserDataHolder>();
  private Set<Facet> myChangedFacets = new HashSet<Facet>();
  private final NotNullFunction<Module, ModuleConfigurationState> myModuleStateProvider;

  public ProjectFacetsConfigurator(NotNullFunction<Module, ModuleConfigurationState> moduleStateProvider) {
    myModuleStateProvider = moduleStateProvider;
  }

  public void removeFacet(Facet facet) {
    getTreeModel(facet.getModule()).removeFacetInfo(myFacet2Info.get(facet));
    getOrCreateModifiableModel(facet.getModule()).removeFacet(facet);
  }

  public Facet createAndAddFacet(Module module, FacetType<?, ?> type, String name, final @Nullable FacetInfo underlyingFacet) {
    final Facet facet = createFacet(type, module, name, myInfo2Facet.get(underlyingFacet));
    getOrCreateModifiableModel(module).addFacet(facet);
    addFacetInfo(facet);
    return facet;
  }

  private void addFacetInfo(final Facet facet) {
    LOG.assertTrue(!myFacet2Info.containsKey(facet));
    FacetInfo info = new FacetInfo(facet.getType(), facet.getName(), facet.getConfiguration(), myFacet2Info.get(facet.getUnderlyingFacet()));
    myFacet2Info.put(facet, info);
    myInfo2Facet.put(info, facet);
    getTreeModel(facet.getModule()).addFacetInfo(info);
  }

  public void addFacetInfos(final Module module) {
    final Facet[] facets = getFacetModel(module).getSortedFacets();
    for (Facet facet : facets) {
      //todo[nik] remove later. This 'if' is used only to hide javaee facets in Project Settings
      if (FacetTypeRegistry.getInstance().findFacetType(facet.getTypeId()) != null) {
        addFacetInfo(facet);
      }
    }
  }

  private static <C extends FacetConfiguration> Facet createFacet(final FacetType<?, C> type, final Module module, String name, final @Nullable Facet underlyingFacet) {
    return type.createFacet(module, name, type.createDefaultConfiguration(), underlyingFacet);
  }

  private boolean isNewFacet(Facet facet) {
    final ModifiableFacetModel model = myModels.get(facet.getModule());
    return model != null && model.isNewFacet(facet);
  }

  @NotNull
  public ModifiableFacetModel getOrCreateModifiableModel(Module module) {
    ModifiableFacetModel model = myModels.get(module);
    if (model == null) {
      model = FacetManager.getInstance(module).createModifiableModel();
      myModels.put(module, model);
    }
    return model;
  }

  @NotNull
  public FacetEditor getOrCreateEditor(Facet facet) {
    FacetEditor editor = myEditors.get(facet);
    if (editor == null) {
      final Facet underlyingFacet = facet.getUnderlyingFacet();
      final FacetEditorContext parentContext = underlyingFacet != null ? getOrCreateEditor(underlyingFacet).getContext() : null;
      final ModuleConfigurationState state = myModuleStateProvider.fun(facet.getModule());
      final ProjectConfigurableContext context = new ProjectConfigurableContext(facet, isNewFacet(facet), parentContext, state,
                                                                                getSharedModuleData(facet.getModule()));
      editor = new FacetEditor(context, facet.getConfiguration());
      editor.getComponent();
      editor.reset();
      myEditors.put(facet, editor);
    }
    return editor;
  }

  private UserDataHolder getSharedModuleData(final Module module) {
    UserDataHolder dataHolder = mySharedModuleData.get(module);
    if (dataHolder == null) {
      dataHolder = new UserDataHolderBase();
      mySharedModuleData.put(module, dataHolder);
    }
    return dataHolder;
  }

  @NotNull
  public FacetModel getFacetModel(Module module) {
    final ModifiableFacetModel model = myModels.get(module);
    if (model != null) {
      return model;
    }
    return FacetManager.getInstance(module);
  }

  public void commitFacets() {
    for (ModifiableFacetModel model : myModels.values()) {
      model.commit();
    }

    for (Map.Entry<Facet, FacetEditor> entry : myEditors.entrySet()) {
      entry.getValue().onFacetAdded(entry.getKey());
    }

    myModels.clear();
    for (Facet facet : myChangedFacets) {
      facet.getModule().getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(facet);
    }
    myChangedFacets.clear();
  }

  public void resetEditors() {
    for (FacetEditor editor : myEditors.values()) {
      editor.reset();
    }
  }

  public void applyEditors() throws ConfigurationException {
    for (Map.Entry<Facet,FacetEditor> entry : myEditors.entrySet()) {
      final FacetEditor editor = entry.getValue();
      if (editor.isModified()) {
        myChangedFacets.add(entry.getKey());
      }
      editor.apply();
    }
  }

  public boolean isModified() {
    for (ModifiableFacetModel model : myModels.values()) {
      if (model.isModified()) {
        return true;
      }
    }
    for (FacetEditor editor : myEditors.values()) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  public FacetTreeModel getTreeModel(Module module) {
    FacetTreeModel treeModel = myTreeModels.get(module);
    if (treeModel == null) {
      treeModel = new FacetTreeModel();
      myTreeModels.put(module, treeModel);
    }
    return treeModel;
  }

  public FacetInfo getFacetInfo(final Facet facet) {
    return myFacet2Info.get(facet);
  }

  public Facet getFacet(final FacetInfo facetInfo) {
    return myInfo2Facet.get(facetInfo);
  }

  public void registerEditors(final Module module, ConfigureFacetsStep facetsStep) {
    final Map<FacetInfo, FacetEditor> info2EditorMap = facetsStep.getInfo2EditorMap();
    final Facet[] allFacets = FacetManager.getInstance(module).getAllFacets();
    for (Facet facet : allFacets) {
      for (Map.Entry<FacetInfo, FacetEditor> entry : info2EditorMap.entrySet()) {
        if (entry.getKey().getConfiguration() == facet.getConfiguration()) {
          myEditors.put(facet, entry.getValue());
        }
      }
    }
  }

  public void disposeEditors() {
    for (FacetEditor editor : myEditors.values()) {
      editor.disposeUIResources();
    }
  }

  @NotNull
  public Facet[] getAllFacets(final Module module) {
    return getFacetModel(module).getAllFacets();
  }

  @NotNull
  public <F extends Facet> Collection<F> getFacetsByType(final Module module, final FacetTypeId<F> type) {
    return getFacetModel(module).getFacetsByType(type);
  }

  @Nullable
  public <F extends Facet> F findFacet(final Module module, final FacetTypeId<F> type, final String name) {
    return getFacetModel(module).findFacet(type, name);
  }
}
