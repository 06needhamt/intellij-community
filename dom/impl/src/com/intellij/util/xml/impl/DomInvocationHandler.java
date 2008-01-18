/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NullableFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.*;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.*;
import com.intellij.util.xml.events.CollectionElementAddedEvent;
import com.intellij.util.xml.events.ElementDefinedEvent;
import com.intellij.util.xml.events.ElementUndefinedEvent;
import com.intellij.util.xml.events.TagValueChangeEvent;
import com.intellij.util.xml.reflect.AbstractDomChildrenDescription;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import net.sf.cglib.proxy.InvocationHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

/**
 * @author peter
 */
public abstract class DomInvocationHandler<T extends AbstractDomChildDescriptionImpl> extends UserDataHolderBase implements InvocationHandler, DomElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomInvocationHandler");
  public static final Method ACCEPT_METHOD = ReflectionUtil.getMethod(DomElement.class, "accept", DomElementVisitor.class);
  public static final Method ACCEPT_CHILDREN_METHOD = ReflectionUtil.getMethod(DomElement.class, "acceptChildren", DomElementVisitor.class);

  private final Type myAbstractType;
  private final Type myType;
  private final DomInvocationHandler myParent;
  private final DomManagerImpl myManager;
  private final EvaluatedXmlName myTagName;
  private final T myChildDescription;
  private final Converter myGenericConverter;
  private XmlTag myXmlTag;

  private XmlFile myFile;
  private DomFileElementImpl myRoot;
  private final DomElement myProxy;
  private Set<AbstractDomChildDescriptionImpl> myInitializedChildren;
  private Map<Pair<FixedChildDescriptionImpl, Integer>, IndexedElementInvocationHandler> myFixedChildren;
  private Map<AttributeChildDescriptionImpl, AttributeChildInvocationHandler> myAttributeChildren;
  private DomGenericInfoEx myGenericInfo;
  private Map<FixedChildDescriptionImpl, Class> myFixedChildrenClasses;
  private Throwable myInvalidated;
  private final InvocationCache myInvocationCache;
  private final FactoryMap<JavaMethod, Converter> myScalarConverters = new FactoryMap<JavaMethod, Converter>() {
    protected Converter create(final JavaMethod method) {
      final Type returnType = method.getGenericReturnType();
      final Type type = returnType == void.class ? method.getGenericParameterTypes()[0] : returnType;
      final Class parameter = ReflectionUtil.substituteGenericType(type, myType);
      LOG.assertTrue(parameter != null, type + " " + myType);
      final Converter converter = getConverter(method, parameter, type instanceof TypeVariable ? myGenericConverter : null);
      LOG.assertTrue(converter != null, "No converter specified: String<->" + parameter.getName());
      return converter;
    }
  };

  private static final JBReentrantReadWriteLock rwl = LockFactory.createReadWriteLock();
  public static final JBLock r = rwl.readLock();
  public static final JBLock w = rwl.writeLock();

  protected DomInvocationHandler(final Type type,
                                 final XmlTag tag,
                                 final DomInvocationHandler parent,
                                 final EvaluatedXmlName tagName,
                                 final T childDescription,
                                 final DomManagerImpl manager) {
    myManager = manager;
    myParent = parent;
    myTagName = tagName;
    myChildDescription = childDescription;
    myAbstractType = type;

    final Type concreteInterface = manager.getTypeChooserManager().getTypeChooser(type).chooseType(tag);
    final StaticGenericInfo staticInfo = manager.getStaticGenericInfo(concreteInterface);
    myGenericInfo = ((DomInvocationHandler)this) instanceof AttributeChildInvocationHandler ? staticInfo : new DynamicGenericInfo(this, staticInfo, myManager.getProject());
    myType = concreteInterface;

    final Converter converter = getConverter(this, DomUtil.getGenericValueParameter(concreteInterface), null);
    myGenericConverter = converter;
    myInvocationCache =
      manager.getInvocationCache(new Pair<Type, Type>(concreteInterface, converter == null ? null : converter.getClass()));
    final Class<?> rawType = getRawType();
    Class<? extends DomElement> implementation = manager.getImplementation(rawType);
    final boolean isInterface = ReflectionCache.isInterface(rawType);
    if (implementation == null && !isInterface) {
      implementation = (Class<? extends DomElement>)rawType;
    }
    myProxy = AdvancedProxy.createProxy(this, implementation, isInterface ? new Class[]{rawType} : ArrayUtil.EMPTY_CLASS_ARRAY);
    attach(tag);
  }

  @NotNull
  public <T extends DomElement> DomFileElementImpl<T> getRoot() {
    checkIsValid();

    if (myRoot == null) {
      myRoot = myParent.getRoot();
    }
    return myRoot;
  }

  @Nullable
  public DomElement getParent() {
    checkIsValid();

    return myParent.getProxy();
  }

  protected final void checkIsValid() {
    if (!isValid()) {
      throw new RuntimeException("element " + myType.toString() + " is not valid", myInvalidated);
    }
  }

  @Nullable
  final DomInvocationHandler getParentHandler() {
    return myParent;
  }

  @NotNull
  public final Type getDomElementType() {
    return myType;
  }

  final Type getAbstractType() {
    return myAbstractType;
  }

  @Nullable
  protected String getValue() {
    final XmlTag tag = getXmlTag();
    return tag == null ? null : getTagValue(tag);
  }

  protected void setValue(@Nullable final String value) {
    final XmlTag tag = ensureTagExists();
    myManager.runChange(new Runnable() {
      public void run() {
        setTagValue(tag, value);
      }
    });
    myManager.fireEvent(new TagValueChangeEvent(getProxy(), value));
  }

  public final void copyFrom(DomElement other) {
    if (other == getProxy()) return;
    assert other.getDomElementType().equals(myType);

    if (other.getXmlElement() == null) {
      undefine();
      return;
    }

    ensureXmlElementExists();
    final DomGenericInfoEx genericInfo = getGenericInfo();
    for (final AttributeChildDescriptionImpl description : genericInfo.getAttributeChildrenDescriptions()) {
      description.getDomAttributeValue(this).setStringValue(description.getDomAttributeValue(other).getStringValue());
    }
    for (final DomFixedChildDescription description : genericInfo.getFixedChildrenDescriptions()) {
      final List<? extends DomElement> list = description.getValues(getProxy());
      final List<? extends DomElement> otherValues = description.getValues(other);
      for (int i = 0; i < list.size(); i++) {
        final DomElement otherValue = otherValues.get(i);
        final DomElement value = list.get(i);
        if (otherValue.getXmlElement() == null) {
          value.undefine();
        } else {
          value.copyFrom(otherValue);
        }
      }
    }
    for (final DomCollectionChildDescription description : genericInfo.getCollectionChildrenDescriptions()) {
      for (final DomElement value : description.getValues(getProxy())) {
        value.undefine();
      }
      for (final DomElement otherValue : description.getValues(other)) {
        description.addValue(getProxy(), otherValue.getDomElementType()).copyFrom(otherValue);
      }
    }

    final String stringValue = DomManagerImpl.getDomInvocationHandler(other).getValue();
    if (StringUtil.isNotEmpty(stringValue)) {
      setValue(stringValue);
    }
  }

  public <T extends DomElement> T createStableCopy() {
    final XmlTag tag;
    r.lock();
    try {
      tag = myXmlTag;
    } finally {
      r.unlock();
    }
    if (tag != null && tag.isPhysical()) {
      assert myManager.getDomElement(tag) == getProxy() : myManager.getDomElement(tag) + "\n\n" + tag.getParent().getText();
      final SmartPsiElementPointer<XmlTag> pointer = SmartPointerManager.getInstance(myManager.getProject()).createLazyPointer(tag);
      return myManager.createStableValue(new StableCopyFactory<T>(pointer, myType, getClass()));
    }
    return (T)createPathStableCopy();
  }

  protected DomElement createPathStableCopy() {
    throw new UnsupportedOperationException();
  }

  public final <T extends DomElement> T createMockCopy(final boolean physical) {
    final T copy = myManager.createMockElement((Class<? extends T>)getRawType(), getProxy().getModule(), physical);
    copy.copyFrom(getProxy());
    return copy;
  }

  @NotNull
  public String getXmlElementNamespace() {
    final XmlElement element = getParentHandler().getXmlElement();
    assert element != null;
    return getXmlName().getNamespace(element);
  }

  @Nullable
  public String getXmlElementNamespaceKey() {
    return getXmlName().getXmlName().getNamespaceKey();
  }

  public final Module getModule() {
    final Module module = ModuleUtil.findModuleForPsiElement(getFile());
    return module != null ? module : getRoot().getUserData(DomManagerImpl.MOCK_ELEMENT_MODULE);
  }

  public XmlTag ensureTagExists() {
    checkIsValid();

    if (myXmlTag != null) return myXmlTag;

    attach(setEmptyXmlTag());

    myManager.fireEvent(new ElementDefinedEvent(getProxy()));
    addRequiredChildren();
    return myXmlTag;
  }

  public XmlElement getXmlElement() {
    return getXmlTag();
  }

  public XmlElement ensureXmlElementExists() {
    return ensureTagExists();
  }

  protected final XmlTag createChildTag(final EvaluatedXmlName tagName) {
    final String localName = tagName.getXmlName().getLocalName();
    if (localName.contains(":")) {
      try {
        return JavaPsiFacade.getInstance(myXmlTag.getProject()).getElementFactory().createTagFromText("<" + localName + "/>");
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    final XmlElement element = getXmlElement();
    assert element != null;
    return myXmlTag.createChildTag(localName, tagName.getNamespace(element), "", false);
  }

  public boolean isValid() {
    ProgressManager.getInstance().checkCanceled();
    r.lock();
    try {
      return _isValid();
    }
    finally {
      r.unlock();
    }
  }

  private boolean _isValid() {
    return myInvalidated == null;
  }

  @NotNull
  public final DomGenericInfoEx getGenericInfo() {
    myGenericInfo.checkInitialized();
    return myGenericInfo;
  }

  protected abstract void undefineInternal();

  public final void undefine() {
    undefineInternal();
  }

  protected final void detachChildren() {
    if (myAttributeChildren != null) {
      for (final AttributeChildInvocationHandler handler : myAttributeChildren.values()) {
        handler.detach(false);
      }
    }
    if (myFixedChildren != null) {
      for (final IndexedElementInvocationHandler handler : myFixedChildren.values()) {
        handler.detach(false);
      }
    }
  }

  protected final void deleteTag(final XmlTag tag) {
    final boolean changing = myManager.setChanging(true);
    try {
      tag.delete();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally {
      myManager.setChanging(changing);
    }
    w.lock();
    try {
      setXmlTag(null);
    }
    finally {
      w.unlock();
    }
  }

  protected final void fireUndefinedEvent() {
    myManager.fireEvent(new ElementUndefinedEvent(getProxy()));
  }

  protected abstract XmlTag setEmptyXmlTag();

  protected void addRequiredChildren() {
    for (final AbstractDomChildrenDescription description : getGenericInfo().getChildrenDescriptions()) {
      if (description instanceof DomAttributeChildDescription) {
        final Required required = description.getAnnotation(Required.class);

        if (required != null && required.value()) {
          description.getValues(getProxy()).get(0).ensureXmlElementExists();
        }
      }
      else if (description instanceof DomFixedChildDescription) {
        final DomFixedChildDescription childDescription = (DomFixedChildDescription)description;
        List<? extends DomElement> values = null;
        final int count = childDescription.getCount();
        for (int i = 0; i < count; i++) {
          final Required required = childDescription.getAnnotation(i, Required.class);
          if (required != null && required.value()) {
            if (values == null) {
              values = description.getValues(getProxy());
            }
            values.get(i).ensureTagExists();
          }
        }
      }
    }
  }

  @NotNull
  public final String getXmlElementName() {
    return myTagName.getXmlName().getLocalName();
  }

  @NotNull
  public final EvaluatedXmlName getXmlName() {
    return myTagName;
  }

  public void accept(final DomElementVisitor visitor) {
    ProgressManager.getInstance().checkCanceled();
    myManager.getVisitorDescription(visitor.getClass()).acceptElement(visitor, getProxy());
  }

  public void acceptChildren(DomElementVisitor visitor) {
    ProgressManager.getInstance().checkCanceled();
    final DomElement element = getProxy();
    for (final AbstractDomChildrenDescription description : getGenericInfo().getChildrenDescriptions()) {
      for (final DomElement value : description.getValues(element)) {
        value.accept(visitor);
      }
    }
  }

  final List<CollectionElementInvocationHandler> getAllCollectionChildren() {
    final List<CollectionElementInvocationHandler> collectionChildren = new ArrayList<CollectionElementInvocationHandler>();
    final XmlTag tag = getXmlTag();
    if (tag != null) {
      for (XmlTag xmlTag : tag.getSubTags()) {
        final DomInvocationHandler cachedElement = DomManagerImpl.getCachedElement(xmlTag);
        if (cachedElement instanceof CollectionElementInvocationHandler) {
          collectionChildren.add((CollectionElementInvocationHandler)cachedElement);
        }
      }
    }
    return collectionChildren;
  }

  @NotNull
  protected final synchronized Converter getScalarConverter(final JavaMethod method) {
    return myScalarConverters.get(method);
  }

  public final T getChildDescription() {
    return myChildDescription;
  }

  @Nullable
  public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
    final AnnotatedElement childDescription = getChildDescription();
    if (childDescription != null) {
      final T annotation = childDescription.getAnnotation(annotationClass);
      if (annotation != null) return annotation;
    }

    return getRawType().getAnnotation(annotationClass);
  }

  @Nullable
  private Converter getConverter(final AnnotatedElement annotationProvider,
                                 Class parameter,
                                 @Nullable Converter defaultConverter) {
    final Resolve resolveAnnotation = annotationProvider.getAnnotation(Resolve.class);
    if (resolveAnnotation != null) {
      final Class<? extends DomElement> aClass = resolveAnnotation.value();
      if (!DomElement.class.equals(aClass)) {
        return DomResolveConverter.createConverter(aClass);
      } else {
        LOG.assertTrue(parameter != null, "You should specify @Resolve#value() parameter");
        return DomResolveConverter.createConverter(parameter);
      }
    }

    final Convert convertAnnotation = annotationProvider.getAnnotation(Convert.class);
    final ConverterManager converterManager = myManager.getConverterManager();
    if (convertAnnotation != null) {
      if (convertAnnotation instanceof ConvertAnnotationImpl) {
        return ((ConvertAnnotationImpl)convertAnnotation).getConverter();
      }
      return converterManager.getConverterInstance(convertAnnotation.value());
    }

    if (defaultConverter != null) {
      return defaultConverter;
    }

    return parameter == null ? null : converterManager.getConverterByClass(parameter);
  }

  @NotNull
  public final DomElement getProxy() {
    return myProxy;
  }

  @NotNull
  protected final XmlFile getFile() {
    if (myFile == null) {
      myFile = getRoot().getFile();
    }
    return myFile;
  }

  @NotNull
  public final DomNameStrategy getNameStrategy() {
    final Class<?> rawType = getRawType();
    final DomNameStrategy strategy = DomImplUtil.getDomNameStrategy(rawType, isAttribute());
    if (strategy != null) {
      return strategy;
    }
    final DomInvocationHandler parent = getParentHandler();
    return parent != null ? parent.getNameStrategy() : DomNameStrategy.HYPHEN_STRATEGY;
  }

  protected boolean isAttribute() {
    return false;
  }

  @NotNull
  public ElementPresentation getPresentation() {
    return new ElementPresentation() {
      public String getElementName() {
        return ElementPresentationManager.getElementName(getProxy());
      }

      public String getTypeName() {
        return ElementPresentationManager.getTypeNameForObject(getProxy());
      }

      public Icon getIcon() {
        return ElementPresentationManager.getIcon(getProxy());
      }
    };
  }

  public final GlobalSearchScope getResolveScope() {
    return getRoot().getResolveScope();
  }

  private static <T extends DomElement> T _getParentOfType(Class<T> requiredClass, DomElement element) {
    while (element != null && !(requiredClass.isInstance(element))) {
      element = element.getParent();
    }
    return (T)element;
  }

  public final <T extends DomElement> T getParentOfType(Class<T> requiredClass, boolean strict) {
    return _getParentOfType(requiredClass, strict ? getParent() : getProxy());
  }

  private Invocation createInvocation(final JavaMethod method) {
    if (DomImplUtil.isTagValueGetter(method)) {
      return new GetInvocation(getScalarConverter(method));
    }

    if (DomImplUtil.isTagValueSetter(method)) {
      return new SetInvocation(getScalarConverter(method));
    }

    return myGenericInfo.createInvocation(method);
  }

  @NotNull
  final IndexedElementInvocationHandler getFixedChild(final Pair<FixedChildDescriptionImpl, Integer> info) {
    r.lock();
    try {
      LOG.assertTrue(myFixedChildren != null);
      final IndexedElementInvocationHandler handler = myFixedChildren.get(info);
      if (handler == null) {
        LOG.assertTrue(false, this + " " + info.toString());
      }
      return handler;
    }
    finally {
      r.unlock();
    }
  }

  final Collection<IndexedElementInvocationHandler> getFixedChildren() {
    return myFixedChildren == null ? Collections.<IndexedElementInvocationHandler>emptyList() : myFixedChildren.values();
  }

  @NotNull
  final AttributeChildInvocationHandler getAttributeChild(final AttributeChildDescriptionImpl description) {
    checkIsValid();
    r.lock();
    try {
      _checkInitialized(description);
      assert myAttributeChildren != null;
      final AttributeChildInvocationHandler domElement = myAttributeChildren.get(description);
      assert domElement != null : description;
      return domElement;
    }
    finally {
      r.unlock();
    }
  }

  @Nullable
  public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      return doInvoke(JavaMethodSignature.getSignature(method), args);
    }
    catch (InvocationTargetException ex) {
      throw ex.getTargetException();
    }
  }

  @Nullable
  private Object doInvoke(final JavaMethodSignature signature, final Object... args) throws Throwable {
    Invocation invocation = myInvocationCache.getInvocation(signature);
    if (invocation == null) {
      invocation = createInvocation(JavaMethod.getMethod(getRawType(), signature));
      myInvocationCache.putInvocation(signature, invocation);
    }
    return invocation.invoke(this, args);
  }

  private static void setTagValue(final XmlTag tag, final String value) {
    tag.getValue().setText(value);
  }

  static String getTagValue(final XmlTag tag) {
    return tag.getValue().getTrimmedText();
  }

  public final String toString() {
    if (ReflectionCache.isAssignable(GenericValue.class, getRawType())) {
      return ((GenericValue)getProxy()).getStringValue();
    }
    return myType.toString() + " @" + hashCode();
  }

  final void _checkInitialized(final AbstractDomChildDescriptionImpl description) {
    if (myInitializedChildren != null && myInitializedChildren.contains(description)) return;
    r.unlock();

    final List<XmlTag> subTags = getSubTags(description);

    w.lock();
    try {
      if (myInitializedChildren != null && myInitializedChildren.contains(description)) return;

      if (description instanceof AttributeChildDescriptionImpl) {
        final AttributeChildDescriptionImpl attributeChildDescription = (AttributeChildDescriptionImpl)description;
        final EvaluatedXmlName evaluatedXmlName = createEvaluatedXmlName(attributeChildDescription.getXmlName());
        if (myAttributeChildren == null) myAttributeChildren = new THashMap<AttributeChildDescriptionImpl, AttributeChildInvocationHandler>();
        myAttributeChildren.put(attributeChildDescription, new AttributeChildInvocationHandler(description.getType(), myXmlTag, this,
                                                                                               evaluatedXmlName, attributeChildDescription, myManager));
      }
      else if (description instanceof FixedChildDescriptionImpl) {
        final FixedChildDescriptionImpl fixedChildDescription = (FixedChildDescriptionImpl)description;
        final EvaluatedXmlName evaluatedXmlName = createEvaluatedXmlName(fixedChildDescription.getXmlName());
        final int count = fixedChildDescription.getCount();
        for (int i = 0; i < count; i++) {
          getOrCreateIndexedChild(findSubTag(subTags, i), evaluatedXmlName, Pair.create(fixedChildDescription, i));
        }
      }
      else if (myXmlTag != null && description instanceof AbstractCollectionChildDescription) {
        final AbstractCollectionChildDescription childDescription = (AbstractCollectionChildDescription)description;
        for (XmlTag subTag : subTags) {
          new CollectionElementInvocationHandler(description.getType(), subTag, childDescription, this);
        }
      }
      if (myInitializedChildren == null) myInitializedChildren = new THashSet<AbstractDomChildDescriptionImpl>();
      myInitializedChildren.add(description);
    }
    finally {
      r.lock();
      w.unlock();
    }
  }

  private List<XmlTag> getSubTags(final AbstractDomChildDescriptionImpl description) {
    final XmlTag tag = getXmlTag();
    final XmlFile file = getFile();
    if (tag != null) {
      final XmlTag[] xmlTags = tag.getSubTags();
      r.lock();
      try {
        if (description instanceof FixedChildDescriptionImpl) {
          return DomImplUtil.findSubTags(xmlTags, createEvaluatedXmlName(((DomChildDescriptionImpl)description).getXmlName()), file);
        }
        if (description instanceof AbstractCollectionChildDescription) {
          return ((AbstractCollectionChildDescription) description).getSubTags(this, xmlTags, file);
        }
      }
      finally {
        r.unlock();
      }
    }
    return Collections.emptyList();
  }

  private void getOrCreateIndexedChild(final XmlTag subTag, EvaluatedXmlName name, final Pair<FixedChildDescriptionImpl, Integer> pair) {
    if (myFixedChildren == null) myFixedChildren = new THashMap<Pair<FixedChildDescriptionImpl, Integer>, IndexedElementInvocationHandler>();
    IndexedElementInvocationHandler handler = myFixedChildren.get(pair);
    if (handler == null) {
      handler = createIndexedChild(subTag, name, pair);
      myFixedChildren.put(pair, handler);
    }
    else {
      handler.attach(subTag);
    }
  }

  private IndexedElementInvocationHandler createIndexedChild(final XmlTag subTag, EvaluatedXmlName qname, final Pair<FixedChildDescriptionImpl, Integer> pair) {
    final FixedChildDescriptionImpl description = pair.first;
    Type type = getFixedChildrenClass(description);
    if (type == null) {
      type = description.getType();
    }
    return new IndexedElementInvocationHandler(type, subTag, this, qname, description, pair.getSecond());
  }

  protected final Class<?> getRawType() {
    return ReflectionUtil.getRawType(myType);
  }

  @Nullable
  protected final Class getFixedChildrenClass(final FixedChildDescriptionImpl tagName) {
    return myFixedChildrenClasses == null ? null : myFixedChildrenClasses.get(tagName);
  }

  @Nullable
  private static XmlTag findSubTag(@NotNull List<XmlTag> subTags, final int index) {
    return subTags.size() <= index ? null : subTags.get(index);
  }

  @Nullable
  public XmlTag getXmlTag() {
    ProgressManager.getInstance().checkCanceled();
    r.lock();
    try {
      return myXmlTag;
    }
    finally {
      r.unlock();
    }
  }

  protected final void detach(boolean invalidate) {
    w.lock();
    try {
      if (invalidate && _isValid()) {
        myInvalidated = new Throwable();
      }
      if (myInitializedChildren != null && !myInitializedChildren.isEmpty()) {
        if (myFixedChildren != null) {
          for (DomInvocationHandler handler : myFixedChildren.values()) {
            handler.detach(invalidate);
          }
        }
        if (myXmlTag != null && myXmlTag.isValid()) {
          for (CollectionElementInvocationHandler handler : getAllCollectionChildren()) {
            handler.detach(true);
          }
        }
      }

      myInitializedChildren = null;
      removeFromCache();
      setXmlTag(null);
    } finally {
      w.unlock();
    }
  }

  protected void removeFromCache() {
    if (myXmlTag != null && DomManagerImpl.getCachedElement(myXmlTag) == this) {
      DomManagerImpl.setCachedElement(myXmlTag, null);
    }
  }

  protected final void attach(final XmlTag tag) {
    w.lock();
    try {
      setXmlTag(tag);
      cacheInTag(tag);
    } finally{
      r.lock();
      w.unlock();
    }
    try {
      if (myFixedChildren != null) {
        for (final Pair<FixedChildDescriptionImpl, Integer> pair : myFixedChildren.keySet()) {
          _checkInitialized(pair.first);
        }
      }
    } finally {
      r.unlock();
    }
  }

  protected final void setXmlTag(final XmlTag tag) {
    final StaticGenericInfo staticInfo = myManager.getStaticGenericInfo(myType);
    myGenericInfo = tag == null || ((DomInvocationHandler)this) instanceof AttributeChildInvocationHandler ? staticInfo : new DynamicGenericInfo(this, staticInfo, myManager.getProject());
    myXmlTag = tag;
  }

  protected void cacheInTag(final XmlTag tag) {
    DomManagerImpl.setCachedElement(tag, this);
  }

  @NotNull
  public final DomManagerImpl getManager() {
    return myManager;
  }

  public final DomElement addCollectionChild(final CollectionChildDescriptionImpl description, final Type type, int index) throws IncorrectOperationException {
    checkInitialized(description);
    final EvaluatedXmlName name = createEvaluatedXmlName(description.getXmlName());
    final XmlTag tag = addEmptyTag(name, index);
    final CollectionElementInvocationHandler handler = new CollectionElementInvocationHandler(type, tag, description, this);
    final DomElement element = handler.getProxy();
    myManager.fireEvent(new CollectionElementAddedEvent(element, tag.getName()));
    handler.addRequiredChildren();
    return element;
  }

  final void checkInitialized(final DomChildDescriptionImpl description) {
    checkIsValid();
    r.lock();
    try {
      _checkInitialized(description);
    }
    finally {
      r.unlock();
    }
  }

  protected final void createFixedChildrenTags(EvaluatedXmlName tagName, FixedChildDescriptionImpl description, int count) {
    checkInitialized(description);
    final XmlTag tag = ensureTagExists();
    final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, tagName, getFile());
    if (subTags.size() < count) {
      getFixedChild(Pair.create(description, count - 1)).ensureTagExists();
    }
  }

  private XmlTag addEmptyTag(final EvaluatedXmlName tagName, int index) throws IncorrectOperationException {
    final XmlTag tag = ensureTagExists();
    final List<XmlTag> subTags = DomImplUtil.findSubTags(tag, tagName, getFile());
    if (subTags.size() < index) {
      index = subTags.size();
    }
    final boolean changing = myManager.setChanging(true);
    try {
      XmlTag newTag = createChildTag(tagName);
      if (index == 0) {
        if (subTags.isEmpty()) {
          return (XmlTag)tag.add(newTag);
        }

        return (XmlTag)tag.addBefore(newTag, subTags.get(0));
      }

      return (XmlTag)tag.addAfter(newTag, subTags.get(index - 1));
    }
    finally {
      myManager.setChanging(changing);
    }
  }

  public final boolean isInitialized(final AbstractDomChildDescriptionImpl qname) {
    r.lock();
    try {
      return myInitializedChildren != null && myInitializedChildren.contains(qname);
    } finally{
      r.unlock();
    }
  }

  public final boolean isAnythingInitialized() {
    r.lock();
    try {
      return myInitializedChildren != null && !myInitializedChildren.isEmpty();
    } finally{
      r.unlock();
    }
  }

  public void setFixedChildClass(final FixedChildDescriptionImpl tagName, final Class<? extends DomElement> aClass) {
    assert !isInitialized(tagName);
    if (myFixedChildrenClasses == null) myFixedChildrenClasses = new THashMap<FixedChildDescriptionImpl, Class>();
    myFixedChildrenClasses.put(tagName, aClass);
  }

  @NotNull
  public final EvaluatedXmlName createEvaluatedXmlName(final XmlName xmlName) {
    return getXmlName().evaluateChildName(xmlName);
  }

  public List<? extends DomElement> getCollectionChildren(final AbstractDomChildDescriptionImpl description, final NotNullFunction<DomInvocationHandler, List<XmlTag>> tagsGetter) {
    XmlTag tag = getXmlTag();
    if (tag == null) return Collections.emptyList();

    checkIsValid();
    r.lock();
    try {
      _checkInitialized(description);

      final List<XmlTag> subTags = tagsGetter.fun(this);
      if (subTags.isEmpty()) return Collections.emptyList();

      List<DomElement> elements = new ArrayList<DomElement>(subTags.size());
      for (XmlTag subTag : subTags) {
        final DomInvocationHandler element = DomManagerImpl.getCachedElement(subTag);
        if (element != null) {
          elements.add(element.getProxy());
        }
      }
      return Collections.unmodifiableList(elements);
    }
    finally {
      r.unlock();
    }
  }

  private static class StableCopyFactory<T extends DomElement> implements NullableFactory<T> {
    private final SmartPsiElementPointer<XmlTag> myPointer;
    private final Type myType;
    private final Class<? extends DomInvocationHandler> myHandlerClass;

    public StableCopyFactory(final SmartPsiElementPointer<XmlTag> pointer,
                             final Type type, final Class<? extends DomInvocationHandler> aClass) {
      myPointer = pointer;
      myType = type;
      myHandlerClass = aClass;
    }

    public T create() {
      final XmlTag tag = myPointer.getElement();
      if (tag == null) return null;

      final DomElement element = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
      if (element == null || !element.getDomElementType().equals(myType)) return null;

      final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(element);
      if (handler == null || !handler.getClass().equals(myHandlerClass)) return null;

      return (T)element;
    }
  }
}

