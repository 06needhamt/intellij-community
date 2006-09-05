/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.AbstractConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DomElementsProblemsHolderImpl extends SmartList<DomElementProblemDescriptor> implements DomElementsProblemsHolder {
  private HighlightSeverity myDefaultHighlightSeverity = HighlightSeverity.ERROR;
  private final FactoryMap<DomElement, List<DomElementProblemDescriptor>> myCachedErrors =
    new FactoryMap<DomElement, List<DomElementProblemDescriptor>>() {
      protected List<DomElementProblemDescriptor> create(final DomElement domElement) {
        List<DomElementProblemDescriptor> problems = new SmartList<DomElementProblemDescriptor>();
        for (DomElementProblemDescriptor problemDescriptor : DomElementsProblemsHolderImpl.this) {
          if (problemDescriptor.getDomElement().equals(domElement)) {
            problems.add(problemDescriptor);
          }
        }
        return problems;
      }
    };
  private final FactoryMap<DomElement, List<DomElementProblemDescriptor>> myCachedXmlErrors =
    new FactoryMap<DomElement, List<DomElementProblemDescriptor>>() {
      protected List<DomElementProblemDescriptor> create(final DomElement domElement) {
        SmartList<DomElementProblemDescriptor> problems = new SmartList<DomElementProblemDescriptor>();
        if (domElement instanceof GenericDomValue) {
          addResolveProblems((GenericDomValue)domElement, problems);
        }
        return problems;
      }
    };
  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedChildrenErrors =
    new HashMap<DomElement, List<DomElementProblemDescriptor>>();
  private final Map<DomElement, List<DomElementProblemDescriptor>> myCachedChildrenXmlErrors =
    new HashMap<DomElement, List<DomElementProblemDescriptor>>();
  private final Function<DomElement, Collection<DomElementProblemDescriptor>> myDomProblemsGetter =
    new Function<DomElement, Collection<DomElementProblemDescriptor>>() {
      public Collection<DomElementProblemDescriptor> fun(final DomElement s) {
        return myCachedErrors.get(s);
      }
    };
  private final Function<DomElement, Collection<DomElementProblemDescriptor>> myXmlProblemsGetter =
    new Function<DomElement, Collection<DomElementProblemDescriptor>>() {
      public Collection<DomElementProblemDescriptor> fun(final DomElement s) {
        return myCachedXmlErrors.get(s);
      }
    };

  public void createProblem(DomElement domElement, @Nullable String message) {
    createProblem(domElement, getDefaultHighlightSeverity(), message);
  }

  public void createProblem(DomElement domElement, DomCollectionChildDescription childDescription, @Nullable String message) {
    addProblem(new DomCollectionProblemDescriptorImpl(domElement, message, getDefaultHighlightSeverity(), childDescription));
  }

  @NotNull
  public synchronized List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
    if (domElement == null || !domElement.isValid()) return Collections.emptyList();
    return new SmartList<DomElementProblemDescriptor>(myCachedErrors.get(domElement));
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement, boolean includeXmlProblems) {
    List<DomElementProblemDescriptor> problems = getProblems(domElement);
    if (includeXmlProblems) {
      problems.addAll(myCachedXmlErrors.get(domElement));
    }
    return problems;
  }

  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren) {

    final List<DomElementProblemDescriptor> list = getProblems(domElement);
    if (!withChildren || domElement == null || !domElement.isValid()) {
      return list;
    }

    final List<DomElementProblemDescriptor> collection = getProblems(domElement, myCachedChildrenErrors, myDomProblemsGetter);
    collection.addAll(getProblems(domElement, myCachedChildrenXmlErrors, myXmlProblemsGetter));
    return collection;
  }

  public List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren,
                                                       HighlightSeverity minSeverity) {
    List<DomElementProblemDescriptor> severityProblem = new ArrayList<DomElementProblemDescriptor>();
    for (DomElementProblemDescriptor problemDescriptor : getProblems(domElement, includeXmlProblems, withChildren)) {
      if (problemDescriptor.getHighlightSeverity().equals(minSeverity)) {
        severityProblem.add(problemDescriptor);
      }
    }

    return severityProblem;
  }

  public final void createProblem(DomElement domElement, HighlightSeverity highlightType, String message) {
    addProblem(new DomElementProblemDescriptorImpl(domElement, message, highlightType));
  }

  public void addProblem(final DomElementProblemDescriptor problemDescriptor) {
    add(problemDescriptor);
    myCachedChildrenErrors.clear();
    myCachedChildrenXmlErrors.clear();
    myCachedErrors.clear();
    myCachedXmlErrors.clear();
  }

  private static List<DomElementProblemDescriptor> getProblems(final DomElement domElement,
                                                               final Map<DomElement, List<DomElementProblemDescriptor>> map,
                                                               final Function<DomElement, Collection<DomElementProblemDescriptor>> function) {
    final Collection<DomElementProblemDescriptor> list = map.get(domElement);
    if (list != null) {
      return new ArrayList<DomElementProblemDescriptor>(list);
    }

    final List<DomElementProblemDescriptor> problems = new ArrayList<DomElementProblemDescriptor>(function.fun(domElement));
    domElement.acceptChildren(new DomElementVisitor() {
      public void visitDomElement(DomElement element) {
        problems.addAll(getProblems(element, map, function));
      }
    });
    map.put(domElement, problems);
    return new ArrayList<DomElementProblemDescriptor>(problems);
  }


  private static void addResolveProblems(final GenericDomValue value, SmartList<DomElementProblemDescriptor> problems) {
    if (value.getXmlElement() != null && value.getValue() == null) {
      final String description = value.getConverter().getErrorMessage(value.getStringValue(), new AbstractConvertContext() {
        @NotNull
        public DomElement getInvocationElement() {
          return value;
        }

        public PsiManager getPsiManager() {
          return PsiManager.getInstance(value.getManager().getProject());
        }
      });
      problems.add(new DomElementProblemDescriptorImpl(value, description, HighlightSeverity.ERROR));
    }
  }

  public List<DomElementProblemDescriptor> getAllProblems() {
    return this;
  }

  public HighlightSeverity getDefaultHighlightSeverity() {
    return myDefaultHighlightSeverity;
  }

  public void setDefaultHighlightSeverity(final HighlightSeverity defaultHighlightSeverity) {
    myDefaultHighlightSeverity = defaultHighlightSeverity;
  }
}
