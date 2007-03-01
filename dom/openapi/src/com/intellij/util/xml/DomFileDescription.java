/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.intellij.util.xml;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.*;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.InstanceMap;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 *
 * @see com.intellij.util.xml.MergingFileDescription
 */
public class DomFileDescription<T> {
  public static final ExtensionPointName<DomFileDescription> EP_NAME = ExtensionPointName.create("com.intellij.dom.fileDescription");

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.DomFileDescription");
  private final InstanceMap<ScopeProvider> myScopeProviders = new InstanceMap<ScopeProvider>();
  protected final Class<T> myRootElementClass;
  protected final String myRootTagName;
  private final Map<Class<? extends DomElement>,Class<? extends DomElement>> myImplementations = new HashMap<Class<? extends DomElement>, Class<? extends DomElement>>();
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();
  private final Map<String, NotNullFunction<XmlTag,List<String>>> myNamespacePolicies = new THashMap<String, NotNullFunction<XmlTag, List<String>>>();

  public DomFileDescription(final Class<T> rootElementClass, @NonNls final String rootTagName) {
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
  }

  /**
   * Register an implementation class to provide additional functionality for DOM elements.
   *
   * @param domElementClass interface class.
   * @param implementationClass abstract implementation class.
   *
   * @see #initializeFileDescription()
   */
  protected final <T extends DomElement> void registerImplementation(Class<T> domElementClass, Class<? extends T> implementationClass) {
    myImplementations.put(domElementClass, implementationClass);
  }

  /**
   * @param namespaceKey namespace identifier
   * @see @com.intellij.util.xml.Namespace()
   * @param policy function that takes XML file root tag and returns (maybe empty) list of possible namespace URLs or DTD public ids. This
   * function shouldn't use DOM since it may be not initialized for the file at the moment
   */
  protected final void registerNamespacePolicy(String namespaceKey, NotNullFunction<XmlTag,List<String>> policy) {
    myNamespacePolicies.put(namespaceKey, policy);
  }

  /**
   * @param namespaceKey namespace identifier
   * @see @com.intellij.util.xml.Namespace()
   * @param namespace XML namespace or DTD public id value for the given namespaceKey
   */
  protected final void registerNamespacePolicy(String namespaceKey, final String namespace) {
    registerNamespacePolicy(namespaceKey, new NotNullFunction<XmlTag, List<String>>() {
      @NotNull
      public List<String> fun(final XmlTag tag) {
        return Arrays.asList(namespace);
      }
    });
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  @NotNull
  public List<String> getAllowedNamespaces(@NotNull String namespaceKey, @NotNull XmlFile file) {
    final NotNullFunction<XmlTag, List<String>> function = myNamespacePolicies.get(namespaceKey);
    if (function != null) {
      final XmlDocument document = file.getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null) {
          return function.fun(tag);
        }
      }
    }
    return Collections.emptyList();
  }

  @Deprecated
  protected final void registerClassChooser(final Type aClass, final TypeChooser typeChooser, Disposable parentDisposable) {
    registerTypeChooser(aClass, typeChooser);
  }

  protected final void registerTypeChooser(final Type aClass, final TypeChooser typeChooser) {
    myTypeChooserManager.registerTypeChooser(aClass, typeChooser);
  }

  public final TypeChooserManager getTypeChooserManager() {
    return myTypeChooserManager;
  }

  public boolean isAutomaticHighlightingEnabled() {
    return true;
  }

  /**
   * The right place to call {@link #registerImplementation(Class, Class)},
   * {@link #registerNamespacePolicy(String, com.intellij.util.NotNullFunction)},
   * and {@link #registerTypeChooser(java.lang.reflect.Type, TypeChooser)}.
   */
  protected void initializeFileDescription() {}

  /**
   * Create custom DOM annotator that will be used when error-highlighting DOM. The results will be collected to
   * {@link com.intellij.util.xml.highlighting.DomElementsProblemsHolder}. The highlighting will be most probably done in an
   * {@link com.intellij.util.xml.highlighting.BasicDomElementsInspection} instance.
   * @return Annotator or null
   */
  @Nullable
  public DomElementsAnnotator createAnnotator() {
    return null;
  }

  public final Map<Class<? extends DomElement>,Class<? extends DomElement>> getImplementations() {
    initializeFileDescription();
    return myImplementations;
  }

  @NotNull
  public final Class<T> getRootElementClass() {
    return myRootElementClass;
  }

  public final String getRootTagName() {
    return myRootTagName;
  }

  public boolean isMyFile(@NotNull XmlFile file, @Nullable final Module module) {
    final Namespace namespace = DomReflectionUtil.findAnnotationDFS(myRootElementClass, Namespace.class);
    if (namespace != null) {
      final String key = namespace.value();
      final NotNullFunction<XmlTag, List<String>> function = myNamespacePolicies.get(key);
      LOG.assertTrue(function != null, "No namespace policy for namespace " + key + " in " + this);
      final XmlDocument document = file.getDocument();
      if (document != null) {
        final XmlTag tag = document.getRootTag();
        if (tag != null) {
          final List<String> list = function.fun(tag);
          if (list.contains(tag.getNamespace())) return true;

          final XmlProlog prolog = document.getProlog();
          if (prolog != null) {
            final XmlDoctype doctype = prolog.getDoctype();
            if (doctype != null) {
              final String publicId = doctype.getPublicId();
              if (publicId != null && list.contains(publicId)) return true;
            }
          }
        }
      }
      return false;
    }

    return true;
  }

  public boolean acceptsOtherRootTagNames() {
    return false;
  }

  /**
   * Get dependency items (the same, as in {@link com.intellij.psi.util.CachedValue}) for file. On any dependency item change, the
   * {@link #isMyFile(com.intellij.psi.xml.XmlFile, Module)} method will be invoked once more to ensure that the file description still
   * accepts this file 
   * @param file XML file to get dependencies of
   * @return dependency item set 
   */
  @NotNull
  public Set<? extends Object> getDependencyItems(XmlFile file) {
    return Collections.emptySet();
  }

  /**
   * @deprecated not used
   */
  @NotNull
  public Set<Class<? extends DomElement>> getDomModelDependencyItems() {
    return Collections.emptySet();
  }

  /**
   * @deprecated not used
   */
  @NotNull
  public Set<XmlFile> getDomModelDependentFiles(@NotNull DomFileElement changedRoot) {
    return Collections.emptySet();
  }

  protected static Set<Class<? extends DomElement>> convertToSet(Class<? extends DomElement> classes) {
    return new THashSet<Class<? extends DomElement>>(Arrays.asList(classes));
  }

  /**
   * @param reference DOM reference
   * @return element, whose all children will be searched for declaration
   */
  @NotNull
  public DomElement getResolveScope(GenericDomValue<?> reference) {
    final DomElement annotation = getScopeFromAnnotation(reference);
    if (annotation != null) return annotation;

    return reference.getRoot();
  }

  /**
   * @param element DOM element
   * @return element, whose direct children names will be compared by name. Basically it's parameter element's parent (see {@link ParentScopeProvider}).
   */
  @NotNull
  public DomElement getIdentityScope(DomElement element) {
    final DomElement annotation = getScopeFromAnnotation(element);
    if (annotation != null) return annotation;

    return element.getParent();
  }

  @Nullable
  protected final DomElement getScopeFromAnnotation(final DomElement element) {
    final Scope scope = element.getAnnotation(Scope.class);
    if (scope != null) {
      return myScopeProviders.get(scope.value()).getScope(element);
    }
    return null;
  }

}
