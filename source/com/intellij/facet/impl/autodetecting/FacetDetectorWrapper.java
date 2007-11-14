/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.*;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public abstract class FacetDetectorWrapper<S, C extends FacetConfiguration, F extends Facet<C>> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.autodetecting.FacetDetectorWrapper");
  private FileType myFileType;
  private final AutodetectionFilter myAutodetectionFilter;
  private VirtualFileFilter myVirtualFileFilter;
  private FacetDetector<S,C> myFacetDetector;
  private FacetType<F,C> myFacetType;

  protected FacetDetectorWrapper(final FileType fileType, FacetType<F, C> facetType, final AutodetectionFilter autodetectionFilter, final VirtualFileFilter virtualFileFilter,
                                 final FacetDetector<S, C> facetDetector) {
    myFileType = fileType;
    myFacetType = facetType;
    myAutodetectionFilter = autodetectionFilter;
    myVirtualFileFilter = virtualFileFilter;
    myFacetDetector = facetDetector;
  }

  public FileType getFileType() {
    return myFileType;
  }

  public VirtualFileFilter getVirtualFileFilter() {
    return myVirtualFileFilter;
  }

  public FacetType<?, C> getFacetType() {
    return myFacetType;
  }

  @Nullable
  protected Facet detectFacet(@NotNull final Module module, VirtualFile virtualFile, S source) {
    if (!myAutodetectionFilter.isAutodetectionEnabled(module, myFacetType, virtualFile.getUrl())) {
      LOG.debug("Autodetection disabled for " + myFacetType.getPresentableName() + " facets in module " + module.getName());
      return null;
    }

    Facet underlyingFacet = null;
    FacetTypeId underlyingFacetType = myFacetType.getUnderlyingFacetType();
    if (underlyingFacetType != null) {
      //todo[nik] check that underlying facet implements FacetRootsProvider
      //noinspection unchecked
      underlyingFacet = FacetFinder.getInstance(module.getProject()).findFacet(virtualFile, underlyingFacetType);
      if (underlyingFacet == null) {
        LOG.debug("Underlying " + underlyingFacetType + " facet not found for " + virtualFile.getUrl());
        return null;
      }
    }

    Collection<F> facets = FacetManager.getInstance(module).getFacetsByType(myFacetType.getId());
    Map<C, F> configurations = new HashMap<C, F>();
    for (F facet : facets) {
      configurations.put(facet.getConfiguration(), facet);
    }

    final C detectedConfiguration = myFacetDetector.detectFacet(source, Collections.unmodifiableCollection(configurations.keySet()));
    if (detectedConfiguration == null) {
      return null;
    }

    if (configurations.containsKey(detectedConfiguration)) {
      return configurations.get(detectedConfiguration);
    }

    final String name = generateName(module);

    final Facet underlyingFacet1 = underlyingFacet;
    return new WriteAction<Facet>() {
      protected void run(final Result<Facet> result) {
        final Facet facet = FacetManagerImpl.createFacet(myFacetType, module, name, detectedConfiguration, underlyingFacet1);
        facet.setImplicit(true);
        ModifiableFacetModel model = FacetManager.getInstance(facet.getModule()).createModifiableModel();
        ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        myFacetDetector.beforeFacetAdded(facet, model, rootModel);
        model.addFacet(facet);
        if (rootModel.isChanged()) {
          rootModel.commit();
        }
        else {
          rootModel.dispose();
        }

        model.commit();
        myFacetDetector.afterFacetAdded(facet);

        result.setResult(facet);
      }
    }.execute().getResultObject();
  }

  private String generateName(final Module module) {
    String baseName = myFacetType.getDefaultFacetName();
    FacetManager manager = FacetManager.getInstance(module);
    int i = 2;
    String name = baseName;
    while (manager.findFacet(myFacetType.getId(), name) != null) {
      name = baseName + i;
      i++;
    }
    return name;
  }

  @Nullable
  public abstract Facet detectFacet(final VirtualFile virtualFile, final PsiManager psiManager);
}
