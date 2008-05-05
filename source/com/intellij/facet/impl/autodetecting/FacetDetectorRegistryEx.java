/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.facet.autodetecting.DetectedFacetPresentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.patterns.VirtualFilePattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.*;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

/**
 * @author nik
 */
public class FacetDetectorRegistryEx<C extends FacetConfiguration> implements FacetDetectorRegistry<C> {
  private final @Nullable FacetDetectorForWizardRegistry<C> myForWizardDelegate;
  private final @Nullable FacetOnTheFlyDetectorRegistry<C> myOnTheFlyDelegate;
  private DetectedFacetPresentation myPresentation;

  public FacetDetectorRegistryEx(final @Nullable FacetDetectorForWizardRegistry<C> forWizardDelegate, final @Nullable FacetOnTheFlyDetectorRegistry<C> onTheFlyDelegate) {
    myForWizardDelegate = forWizardDelegate;
    myOnTheFlyDelegate = onTheFlyDelegate;
  }

  public void customizeDetectedFacetPresentation(@NotNull final DetectedFacetPresentation presentation) {
    myPresentation = presentation;
  }

  public <U extends FacetConfiguration> void registerUniversalDetectorByFileNameAndRootTag(@NotNull @NonNls String fileName,
                                                            @NotNull @NonNls String rootTag,
                                                            @NotNull final FacetDetector<VirtualFile, C> detector,
                                                            @Nullable UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector) {
    VirtualFilePattern fileNamePattern = PlatformPatterns.virtualFile().withName(StandardPatterns.string().equalTo(fileName));
    VirtualFilePattern wizardPattern = fileNamePattern.xmlWithRootTag(StandardPatterns.string().equalTo(rootTag));

    if (underlyingFacetSelector != null) {
      registerSubFacetDetectorForWizard(StdFileTypes.XML, wizardPattern, detector, underlyingFacetSelector);
    }
    else {
      registerDetectorForWizard(StdFileTypes.XML, wizardPattern, detector);
    }

    registerOnTheFlyDetector(StdFileTypes.XML, fileNamePattern, XmlPatterns.xmlFile().withRootTag(XmlPatterns.xmlTag().withName(rootTag)),
                             convertDetector(detector), underlyingFacetSelector);
  }

  public void registerUniversalDetector(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    registerUniversalDetector(fileType, new MyPatternFilter(virtualFilePattern), facetDetector);
  }

  public void registerDetectorForWizard(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    registerDetectorForWizard(fileType, new MyPatternFilter(virtualFilePattern), facetDetector);
  }

  public <U extends FacetConfiguration> void registerSubFacetDetectorForWizard(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final FacetDetector<VirtualFile, C> facetDetector,
                       @NotNull final UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector) {
    if (myForWizardDelegate != null) {
      myForWizardDelegate.register(fileType, new MyPatternFilter(virtualFilePattern), facetDetector, underlyingFacetSelector);
    }
  }

  public void registerOnTheFlyDetector(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final ElementPattern psiFilePattern,
                       @NotNull final FacetDetector<PsiFile, C> facetDetector) {
    registerOnTheFlyDetector(fileType, virtualFilePattern, psiFilePattern, facetDetector, null);
  }

  public <U extends FacetConfiguration> void registerOnTheFlyDetector(@NotNull final FileType fileType,
                                       @NotNull final VirtualFilePattern virtualFilePattern,
                                       @NotNull final ElementPattern psiFilePattern,
                                       @NotNull final FacetDetector<PsiFile, C> facetDetector,
                                       final UnderlyingFacetSelector<VirtualFile, U> selector) {
    registerOnTheFlyDetector(fileType, new MyPatternFilter(virtualFilePattern), new Condition<PsiFile>() {
      public boolean value(final PsiFile psiFile) {
        return psiFilePattern.accepts(psiFile);
      }
    }, facetDetector, selector);
  }


  public void registerUniversalDetector(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    if (myForWizardDelegate != null) {
      myForWizardDelegate.register(fileType, virtualFileFilter, facetDetector);
    }
    if (myOnTheFlyDelegate != null) {
      myOnTheFlyDelegate.register(fileType, virtualFileFilter, facetDetector);
    }
  }

  public void registerDetectorForWizard(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    if (myForWizardDelegate != null) {
      myForWizardDelegate.register(fileType, virtualFileFilter, facetDetector);
    }
  }

  public <U extends FacetConfiguration> void registerOnTheFlyDetector(@NotNull final FileType fileType,
                                                                      @NotNull final VirtualFileFilter virtualFileFilter,
                                                                      @NotNull final Condition<PsiFile> psiFileFilter,
                                                                      @NotNull final FacetDetector<PsiFile, C> facetDetector,
                                                                      @Nullable UnderlyingFacetSelector<VirtualFile, U> selector) {
    if (myOnTheFlyDelegate != null) {
      myOnTheFlyDelegate.register(fileType, virtualFileFilter, psiFileFilter, facetDetector, selector);
    }
  }

  public void registerOnTheFlyDetector(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, @NotNull final Condition<PsiFile> psiFileFilter,
                                       @NotNull final FacetDetector<PsiFile, C> facetDetector) {
    registerOnTheFlyDetector(fileType, virtualFileFilter, psiFileFilter, facetDetector, null);
  }

  private static class MyPatternFilter implements VirtualFileFilter {
    private final VirtualFilePattern myVirtualFilePattern;

    public MyPatternFilter(final VirtualFilePattern virtualFilePattern) {
      myVirtualFilePattern = virtualFilePattern;
    }

    public boolean accept(final VirtualFile file) {
      return myVirtualFilePattern.accepts(file);
    }
  }

  public static <C extends FacetConfiguration> FacetDetector<PsiFile, C> convertDetector(final FacetDetector<VirtualFile, C> detector) {
    return new FacetDetector<PsiFile, C>() {
      public C detectFacet(final PsiFile source, final Collection<C> existentFacetConfigurations) {
        VirtualFile virtualFile = source.getVirtualFile();
        return virtualFile != null ? detector.detectFacet(virtualFile, existentFacetConfigurations) : null;
      }

      public void beforeFacetAdded(@NotNull final Facet facet, final FacetModel facetModel, @NotNull final ModifiableRootModel modifiableRootModel) {
        detector.beforeFacetAdded(facet, facetModel, modifiableRootModel);
      }

      public void afterFacetAdded(@NotNull final Facet facet) {
        detector.afterFacetAdded(facet);
      }
    };
  }

  @NotNull
  public static <C extends FacetConfiguration> DetectedFacetPresentation getDetectedFacetPresentation(@NotNull FacetType<?,C> facetType) {
    FacetDetectorRegistryEx<C> registry = new FacetDetectorRegistryEx<C>(null, null);
    facetType.registerDetectors(registry);
    DetectedFacetPresentation presentation = registry.myPresentation;
    return presentation != null ? presentation : DefaultDetectedFacetPresentation.INSTANCE;
  }
}
