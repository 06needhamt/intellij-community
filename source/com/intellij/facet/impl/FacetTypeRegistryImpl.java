/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl;

import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

/**
 * @author nik
 */
public class FacetTypeRegistryImpl extends FacetTypeRegistry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.FacetTypeRegistryImpl");
  private Map<String, FacetTypeId> myTypeIds = new HashMap<String, FacetTypeId>();
  private Map<FacetTypeId, FacetType> myFacetTypes = new HashMap<FacetTypeId, FacetType>();

  public void registerFacetType(FacetType facetType) {
    final FacetTypeId typeId = facetType.getId();
    String id = facetType.getStringId();
    LOG.assertTrue(!myFacetTypes.containsKey(typeId), "Facet type '" + id + "' is already registered");
    myFacetTypes.put(typeId, facetType);

    LOG.assertTrue(!myTypeIds.containsKey(id), "Facet type id '" + id + "' is already registered");
    myTypeIds.put(id, typeId);
  }

  public void unregisterFacetType(FacetType facetType) {
    final FacetTypeId id = facetType.getId();
    final String stringId = facetType.getStringId();
    LOG.assertTrue(myFacetTypes.remove(id) != null, "Facet type '" + stringId + "' is not registered");
    myFacetTypes.remove(id);
    myTypeIds.remove(stringId);
  }

  public FacetType[] getFacetTypes() {
    final Collection<FacetType> types = myFacetTypes.values();
    return types.toArray(new FacetType[types.size()]);
  }

  @Nullable
  public FacetType findFacetType(String id) {
    final FacetTypeId typeId = myTypeIds.get(id);
    return typeId == null ? null : myFacetTypes.get(typeId);
  }

  @Nullable
  public FacetType findFacetType(FacetTypeId typeId) {
    return myFacetTypes.get(typeId);
  }


}
