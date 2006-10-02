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
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.InstanceMap;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.*;

/**
 * @author peter
 */
public abstract class DomFileDescription<T> {
  private final InstanceMap<ScopeProvider> myScopeProviders = new InstanceMap<ScopeProvider>();
  protected final Class<T> myRootElementClass;
  protected final String myRootTagName;
  private final Map<Class<? extends DomElement>,Class<? extends DomElement>> myImplementations = new HashMap<Class<? extends DomElement>, Class<? extends DomElement>>();
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();

  protected DomFileDescription(final Class<T> rootElementClass, @NonNls final String rootTagName) {
    myRootElementClass = rootElementClass;
    myRootTagName = rootTagName;
  }

  protected final <T extends DomElement> void registerImplementation(Class<T> domElementClass, Class<? extends T> implementationClass) {
    myImplementations.put(domElementClass, implementationClass);
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

  protected abstract void initializeFileDescription();

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

  public final Class<T> getRootElementClass() {
    return myRootElementClass;
  }

  public final String getRootTagName() {
    return myRootTagName;
  }

  public boolean isMyFile(XmlFile file, @Nullable final Module module) {
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
   * @return set of DOM root interfaces, whose changes may cause file of this file description change.
   */
  @NotNull
  public Set<Class<? extends DomElement>> getDomModelDependencyItems() {
    return Collections.emptySet();
  }

  /**
   * @param changedRoot Changed DOM file element, that is registered with {@link com.intellij.util.xml.DomFileDescription}, that
   * mentioned this description in {@link #getDomModelDependencyItems()}. 
   * @return set of files, whose DOM may change on changedRoot changes
   */
  @NotNull
  public Set<XmlFile> getDomModelDependentFiles(@NotNull DomFileElement changedRoot) {
    return Collections.emptySet();
  }

  protected static Set<Class<? extends DomElement>> convertToSet(Class<? extends DomElement> classes) {
    return new THashSet<Class<? extends DomElement>>(Arrays.asList(classes));
  }

  @NotNull
  public DomElement getResolveScope(GenericDomValue<?> reference) {
    final DomElement annotation = getScopeFromAnnotation(reference);
    if (annotation != null) return annotation;

    return reference.getRoot();
  }

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
