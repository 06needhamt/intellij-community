package com.intellij.openapi.components.impl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.components.ex.ComponentManagerEx;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusFactory;
import com.intellij.util.pico.IdeaPicoContainer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.*;
import org.picocontainer.defaults.CachingComponentAdapter;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author mike
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManagerEx, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.components.ComponentManager");

  private Map<Class, Object> myInitializedComponents = new HashMap<Class, Object>();

  private boolean myComponentsCreated = false;

  private MutablePicoContainer myPicoContainer;
  private volatile boolean myDisposed = false;
  private volatile boolean myDisposeCompleted = false;

  private MessageBus myMessageBus;

  private final ComponentManagerConfigurator myConfigurator = new ComponentManagerConfigurator(this);
  private ComponentManager myParentComponentManager;
  private IComponentStore myComponentStore;
  private Boolean myHeadless;
  private final ComponentsRegistry myComponentsRegistry = new ComponentsRegistry();

  protected ComponentManagerImpl(ComponentManager parentComponentManager) {
    myParentComponentManager = parentComponentManager;
    boostrapPicoContainer();
  }

  @NotNull
  public synchronized IComponentStore getStateStore() {
    if (myComponentStore == null) {
      assert myPicoContainer != null;
      myComponentStore = (IComponentStore)myPicoContainer.getComponentInstanceOfType(IComponentStore.class);
    }
    return myComponentStore;
  }

  public MessageBus getMessageBus() {
    assert myMessageBus != null : "Not initialized yet";
    return myMessageBus;
  }

  public boolean isComponentsCreated() {
    return myComponentsCreated;
  }

  private void createComponents() {
    try {
      myComponentsRegistry.loadClasses();

      final Class[] componentInterfaces = myComponentsRegistry.getComponentInterfaces();
      for (Class componentInterface : componentInterfaces) {
        try {
          createComponent(componentInterface);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    finally {
      myComponentsCreated = true;
    }
  }

  private synchronized Object createComponent(Class componentInterface) {
    final Object component = getPicoContainer().getComponentInstance(componentInterface.getName());
    assert component != null : "Can't instantiate component for: " + componentInterface;
    return component;
  }

  protected void disposeComponents() {
    final BaseComponent[] components = getComponents(BaseComponent.class);
    myDisposed = true;

    for (BaseComponent component : components) {
      try {
        component.disposeComponent();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }

    myComponentsCreated = false;
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  private synchronized <T> T getComponentFromContainer(Class<T> interfaceClass) {
    final T initializedComponent = (T)myInitializedComponents.get(interfaceClass);
    if (initializedComponent != null) return initializedComponent;

    //if (!myComponentsCreated) {
    //  LOG.error("Component requests are not allowed before they are created");
    //}

    if (!myComponentsRegistry.containsInterface(interfaceClass)) {
      return null;
    }

    Object lock = myComponentsRegistry.getComponentLock(interfaceClass);

    synchronized (lock) {
      T component = (T)getPicoContainer().getComponentInstance(interfaceClass.getName());
      if (component == null) {
        component = (T)createComponent(interfaceClass);
      }
      if (component == null) {
        LOG.error("Cant create " + interfaceClass);
        return null;
      }

      myInitializedComponents.put(interfaceClass, component);

      return component;
    }
  }

  public <T> T getComponent(Class<T> interfaceClass) {
    assert !myDisposeCompleted : "Already disposed";
    return getComponent(interfaceClass, null);
  }

  public <T> T getComponent(Class<T> interfaceClass, T defaultImplementation) {
    final T fromContainer = getComponentFromContainer(interfaceClass);
    if (fromContainer != null) return fromContainer;
    if (defaultImplementation != null) return defaultImplementation;
    return null;
  }

  private void initComponent(Object component) {
    try {
      getStateStore().initComponent(component);
      if (component instanceof BaseComponent) {
        ((BaseComponent)component).initComponent();
      }
    }
    catch (Throwable ex) {
      handleInitComponentError(ex, false, component.getClass().getName());
    }
  }

  protected void handleInitComponentError(final Throwable ex, final boolean fatal, final String componentClassName) {
    LOG.error(ex);
  }

  public void registerComponent(Class interfaceClass, Class implementationClass) {
    registerComponent(interfaceClass, implementationClass, null);

  }

  @SuppressWarnings({"unchecked"})
  public void registerComponent(Class interfaceClass, Class implementationClass, Map options) {
    LOG.warn("Deprecated method usage: registerComponent", new Throwable());

    final ComponentConfig config = new ComponentConfig();
    config.implementationClass = implementationClass.getName();
    config.interfaceClass = interfaceClass.getName();
    config.options = options;
    registerComponent(config);
  }

  @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"})
  public synchronized void registerComponent(final ComponentConfig config, final IdeaPluginDescriptor pluginDescriptor) {
    if (isHeadless()) {
      String headlessImplClass = config.headlessImplementationClass;
      if (headlessImplClass != null) {
        if (headlessImplClass.trim().length() == 0) return;
        config.implementationClass = headlessImplClass;
      }
    }

    config.implementationClass = config.implementationClass.trim();

    if (config.interfaceClass == null) config.interfaceClass = config.implementationClass;
    config.interfaceClass = config.interfaceClass.trim();

    config.pluginDescriptor =  pluginDescriptor;
    myComponentsRegistry.registerComponent(config);
  }

  @NotNull
  public synchronized Class[] getComponentInterfaces() {
    LOG.warn("Deprecated method usage: getComponentInterfaces", new Throwable());
    return myComponentsRegistry.getComponentInterfaces();
  }

  public synchronized boolean hasComponent(@NotNull Class interfaceClass) {
    return myComponentsRegistry.containsInterface(interfaceClass);
  }

  protected synchronized Object[] getComponents() {
    Class[] componentClasses = myComponentsRegistry.getComponentInterfaces();
    ArrayList<Object> components = new ArrayList<Object>(componentClasses.length);
    for (Class<?> interfaceClass : componentClasses) {
      Object component = getComponent(interfaceClass);
      if (component != null) components.add(component);
    }
    return components.toArray(new Object[components.size()]);
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public synchronized <T> T[] getComponents(Class<T> baseClass) {
    return myComponentsRegistry.getComponentsByType(baseClass);
  }

  @NotNull
  public synchronized MutablePicoContainer getPicoContainer() {
    return myPicoContainer;
  }

  protected MutablePicoContainer createPicoContainer() {
    MutablePicoContainer result;

    if (myParentComponentManager != null) {
      result = new IdeaPicoContainer(myParentComponentManager.getPicoContainer());
    }
    else {
      result = new IdeaPicoContainer();
    }
    
    return result;
  }

  public synchronized BaseComponent getComponent(String name) {
    return myComponentsRegistry.getComponentByName(name);
  }

  protected boolean isComponentSuitable(Map<String, String> options) {
    return !isTrue(options, "internal") || ApplicationManagerEx.getApplicationEx().isInternal();
  }

  private static boolean isTrue(Map<String, String> options, @NonNls final String option) {
    return options != null && options.containsKey(option) && Boolean.valueOf(options.get(option)).booleanValue();
  }

  public void saveSettingsSavingComponents() {
    Object[] components = getComponents(SettingsSavingComponent.class);
    for (Object component : components) {
      if (component instanceof SettingsSavingComponent) {
        ((SettingsSavingComponent)component).save();
      }
    }
  }

  public synchronized void dispose() {
    final IComponentStore store = getStateStore();

    myDisposeCompleted = true;

    if (myMessageBus != null) {
      myMessageBus.dispose();
      myMessageBus = null;
    }

    myInitializedComponents = null;
    myPicoContainer = null;

    store.dispose();
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  //todo[mike] there are several init* methods. Make it just 1
  public void init() {
    getStateStore().initStore();
    initComponents();
  }

  public void initComponents() {
    createComponents();
    getComponents();
  }

  protected void loadComponentsConfiguration(ComponentConfig[] components, @Nullable final IdeaPluginDescriptor descriptor, final boolean loadDummies) {
    myConfigurator.loadComponentsConfiguration(components, descriptor, loadDummies);
  }

  public void loadComponentsConfiguration(final String layer, final boolean loadDummies) {
    myConfigurator.loadComponentsConfiguration(layer, loadDummies);
  }

  protected void boostrapPicoContainer() {
    myPicoContainer = createPicoContainer();

    myMessageBus = MessageBusFactory.newMessageBus(this, myParentComponentManager != null ? myParentComponentManager.getMessageBus() : null);
    final MutablePicoContainer picoContainer = getPicoContainer();
    picoContainer.registerComponentInstance(MessageBus.class, myMessageBus);
    picoContainer.registerComponentInstance(this);
  }


  public ComponentManager getParentComponentManager() {
    return myParentComponentManager;
  }

  private boolean isHeadless() {
    if (myHeadless == null) {
      myHeadless = ApplicationManager.getApplication().isHeadlessEnvironment();
    }

    return myHeadless.booleanValue();
  }


  public void registerComponent(final ComponentConfig config) {
    registerComponent(config, null);
  }

  @NotNull
  public ComponentConfig[] getComponentConfigurations() {
    return myComponentsRegistry.getComponentConfigurations();
  }

  @Nullable
  public Object getComponent(final ComponentConfig componentConfig) {
    return getPicoContainer().getComponentInstance(componentConfig.interfaceClass);
  }

  private class ComponentsRegistry {
    private Map<Class, Object> myInterfaceToLockMap = new HashMap<Class, Object>();
    private Map<Class, Class> myInterfaceToClassMap = new HashMap<Class, Class>();
    private ArrayList<Class> myComponentInterfaces = new ArrayList<Class>(); // keeps order of component's registration
    private Map<String, BaseComponent> myNameToComponent = new HashMap<String, BaseComponent>();
    private List<ComponentConfig> myComponentConfigs = new ArrayList<ComponentConfig>();
    private List<Object> myImplementations = new ArrayList<Object>();
    private boolean myClassesLoaded = false;

    private void loadClasses() {
      assert !myClassesLoaded;

      for (ComponentConfig config : myComponentConfigs) {
        loadClasses(config);
      }

      myClassesLoaded = true;
    }

    private void loadClasses(final ComponentConfig config) {
      ClassLoader loader = config.getClassLoader();

      try {
        final Class<?> interfaceClass = Class.forName(config.interfaceClass, true, loader);
        final Class<?> implementationClass = Class.forName(config.implementationClass, true, loader);

        if (myInterfaceToClassMap.get(interfaceClass) != null) {
          throw new Error("ComponentSetup for component " + interfaceClass.getName() + " already registered");
        }

        getPicoContainer().registerComponent(new ComponentConfigComponentAdapter(config));
        myInterfaceToClassMap.put(interfaceClass, implementationClass);
        myComponentInterfaces.add(interfaceClass);
      }
      catch (Exception e) {
        @NonNls final String message = "Error while registering component: " + config;

        if (config.pluginDescriptor != null) {
          LOG.error(message, new PluginException(e, config.pluginDescriptor.getPluginId()));
        }
        else {
          LOG.error(message, e);
        }
      }
      catch (Error e) {
        if (config.pluginDescriptor != null) {
          LOG.error(new PluginException(e, config.pluginDescriptor.getPluginId()));
        }
        else {
          throw e;
        }
      }
    }

    private Object getComponentLock(final Class componentClass) {
      Object lock = myInterfaceToLockMap.get(componentClass);
      if (lock == null) {
        myInterfaceToLockMap.put(componentClass, lock = new Object());
      }
      return lock;
    }

    private Class[] getComponentInterfaces() {
      assert myClassesLoaded;
      return myComponentInterfaces.toArray(new Class[myComponentInterfaces.size()]);
    }

    private boolean containsInterface(final Class interfaceClass) {
      if (!myClassesLoaded) loadClasses();
      return myInterfaceToClassMap.containsKey(interfaceClass);
    }

    private void registerComponentInstance(final Object component) {
      myImplementations.add(component);

      if (component instanceof BaseComponent) {
        BaseComponent baseComponent = (BaseComponent)component;
        final String componentName = baseComponent.getComponentName();

        if (myNameToComponent.containsKey(componentName)) {
          BaseComponent loadedComponent = myNameToComponent.get(componentName);
          // component may have been already loaded by PicoContainer, so fire error only if components are really different
          if (!component.equals(loadedComponent)) {
            LOG.error("Component name collision: " + componentName + " " + loadedComponent.getClass() + " and " + component.getClass());
          }
        }
        else {
          myNameToComponent.put(componentName, baseComponent);
        }
      }
    }

    private void registerComponent(ComponentConfig config) {
      myComponentConfigs.add(config);

      if (myClassesLoaded) {
        loadClasses(config);
      }
    }

    private BaseComponent getComponentByName(final String name) {
      return myNameToComponent.get(name);
    }

    @SuppressWarnings({"unchecked"})
    public <T> T[] getComponentsByType(final Class<T> baseClass) {
      ArrayList<T> array = new ArrayList<T>();

      for (Class interfaceClass : myComponentInterfaces) {
        final Class implClass = myInterfaceToClassMap.get(interfaceClass);
        if (baseClass.isAssignableFrom(implClass)) {
          array.add((T)getComponent(interfaceClass));
        }
      }

      return array.toArray((T[])Array.newInstance(baseClass, array.size()));
    }

    public ComponentConfig[] getComponentConfigurations() {
        return myComponentConfigs.toArray(new ComponentConfig[myComponentConfigs.size()]);
    }
  }

  private class ComponentConfigComponentAdapter implements ComponentAdapter {
    private final ComponentConfig myConfig;
    private ComponentAdapter myDelegate;
    private boolean myInitialized = false;

    public ComponentConfigComponentAdapter(final ComponentConfig config) {
      myConfig = config;
    }

    public Object getComponentKey() {
      return myConfig.interfaceClass;
    }

    public Class getComponentImplementation() {
      return getDelegate().getComponentImplementation();
    }

    public Object getComponentInstance(final PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
      return getDelegate().getComponentInstance(container);
    }

    public void verify(final PicoContainer container) throws PicoIntrospectionException {
      getDelegate().verify(container);
    }

    public void accept(final PicoVisitor visitor) {
      visitor.visitComponentAdapter(this);
      getDelegate().accept(visitor);
    }

    private ComponentAdapter getDelegate() {
      if (myDelegate == null) {
        final Object componentKey = getComponentKey();

        ClassLoader loader = myConfig.getClassLoader();

        Class<?> implementationClass = null;

        try {
          implementationClass = Class.forName(myConfig.implementationClass, true, loader);
        }
        catch (Exception e) {
          @NonNls final String message = "Error while registering component: " + myConfig;

          if (myConfig.pluginDescriptor != null) {
            LOG.error(message, new PluginException(e, myConfig.pluginDescriptor.getPluginId()));
          }
          else {
            LOG.error(message, e);
          }
        }
        catch (Error e) {
          if (myConfig.pluginDescriptor != null) {
            LOG.error(new PluginException(e, myConfig.pluginDescriptor.getPluginId()));
          }
          else {
            throw e;
          }
        }

        assert implementationClass != null;

        myDelegate = new CachingComponentAdapter(new ConstructorInjectionComponentAdapter(componentKey, implementationClass, null, true)) {
            public Object getComponentInstance(PicoContainer picoContainer) throws PicoInitializationException, PicoIntrospectionException {
              Object componentInstance = null;
              try {
                componentInstance = super.getComponentInstance(picoContainer);

                if (!myInitialized) {
                  myComponentsRegistry.registerComponentInstance(componentInstance);
                  initComponent(componentInstance);
                  myInitialized = true;
                }
              }
              catch (Throwable t) {
                handleInitComponentError(t, componentInstance == null, componentKey.toString());
                if (componentInstance == null) {
                  System.exit(1);
                }

              }
              return componentInstance;
            }
          };
      }

      return myDelegate;
    }
  }
}
