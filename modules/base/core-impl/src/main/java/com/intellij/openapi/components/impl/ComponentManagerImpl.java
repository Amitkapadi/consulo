/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
 */
package com.intellij.openapi.components.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.impl.ExtensionAreaId;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.impl.MessageBusFactory;
import consulo.application.ApplicationProperties;
import consulo.injecting.InjectingContainer;
import consulo.injecting.InjectingContainerBuilder;
import consulo.injecting.InjectingPoint;
import consulo.injecting.key.InjectingKey;
import consulo.ui.RequiredUIAccess;
import gnu.trove.THashMap;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * @author mike
 */
public abstract class ComponentManagerImpl extends UserDataHolderBase implements ComponentManager, Disposable {
  protected class ComponentsRegistry {
    private final List<ComponentConfig> myComponentConfigs = new ArrayList<>();
    private final Map<Class, ComponentConfig> myComponentClassToConfig = new THashMap<>();

    private void loadClasses(List<Class> notLazyServices, InjectingContainerBuilder builder) {
      for (ComponentConfig config : myComponentConfigs) {
        loadClasses(config, notLazyServices, builder);
      }
    }

    @SuppressWarnings("unchecked")
    private void loadClasses(@Nonnull ComponentConfig config, List<Class> notLazyServices, InjectingContainerBuilder builder) {
      ClassLoader loader = config.getClassLoader();

      try {
        final Class interfaceClass = Class.forName(config.getInterfaceClass(), false, loader);
        final Class implementationClass = Comparing.equal(config.getInterfaceClass(), config.getImplementationClass()) ? interfaceClass : Class.forName(config.getImplementationClass(), false, loader);

        InjectingPoint<Object> point = builder.bind(interfaceClass);

        // force singleton
        point.forceSingleton();
        // to impl class
        point.to(implementationClass);
        // post processor
        point.injectListener((startTime, componentInstance) -> {
          if (myChecker.containsKey(interfaceClass)) {
            throw new IllegalArgumentException("Duplicate init of " + interfaceClass);
          }
          myChecker.put(interfaceClass, componentInstance);

          if (componentInstance instanceof Disposable) {
            Disposer.register(ComponentManagerImpl.this, (Disposable)componentInstance);
          }

          boolean isStorableComponent = initializeIfStorableComponent(componentInstance, false, false);

          if (componentInstance instanceof BaseComponent) {
            try {
              ((BaseComponent)componentInstance).initComponent();

              if (!isStorableComponent) {
                LOG.warn("Not storable component implement initComponent() method, which can moved to constructor, component: " + componentInstance.getClass().getName());
              }
            }
            catch (BaseComponent.DefaultImplException ignored) {
              // skip default impl
            }
          }

          long ms = (System.nanoTime() - startTime) / 1000000;
          if (ms > 10 && logSlowComponents()) {
            LOG.info(componentInstance.getClass().getName() + " initialized in " + ms + " ms");
          }
        });

        myComponentClassToConfig.put(implementationClass, config);

        notLazyServices.add(interfaceClass);
      }
      catch (Throwable t) {
        handleInitComponentError(t, null, config);
      }
    }

    private void addConfig(ComponentConfig config) {
      myComponentConfigs.add(config);
    }

    public ComponentConfig getConfig(final Class componentImplementation) {
      return myComponentClassToConfig.get(componentImplementation);
    }
  }

  private static final Logger LOG = Logger.getInstance(ComponentManagerImpl.class);

  private InjectingContainer myInjectingContainer;

  private volatile boolean myDisposed = false;

  protected volatile boolean temporarilyDisposed = false;

  private MessageBus myMessageBus;

  private final ComponentManager myParent;

  private ComponentsRegistry myComponentsRegistry = new ComponentsRegistry();
  private final Condition myDisposedCondition = o -> isDisposed();

  private boolean myComponentsCreated = false;

  private ExtensionsAreaImpl myExtensionsArea;
  @Nonnull
  private final String myName;
  @Nullable
  private final ExtensionAreaId myExtensionAreaId;

  private List<Class> myNotLazyServices = new ArrayList<>();

  private Map<Class<?>, Object> myChecker = new ConcurrentHashMap<>();

  protected ComponentManagerImpl(@Nullable ComponentManager parent, @Nonnull String name, @Nullable ExtensionAreaId extensionAreaId, boolean buildInjectionContainer) {
    myParent = parent;
    myName = name;
    myExtensionAreaId = extensionAreaId;

    if (buildInjectionContainer) {
      buildInjectingContainer();
    }
  }

  protected void buildInjectingContainer() {
    myMessageBus = MessageBusFactory.newMessageBus(myName, myParent == null ? null : myParent.getMessageBus());

    myExtensionsArea = new ExtensionsAreaImpl(this);

    registerExtensionPointsAndExtensions(myExtensionsArea);

    myExtensionsArea.setLocked();

    InjectingContainerBuilder builder = myParent == null ? InjectingContainer.root().childBuilder() : myParent.getInjectingContainer().childBuilder();

    registerServices(builder);

    loadServices(myNotLazyServices, builder);

    bootstrapInjectingContainer(builder);

    myInjectingContainer = builder.build();
  }

  protected void registerExtensionPointsAndExtensions(ExtensionsAreaImpl area) {
    PluginManagerCore.registerExtensionPointsAndExtensions(myExtensionAreaId, area);
  }

  protected void registerServices(InjectingContainerBuilder builder) {
    IdeaPluginDescriptor[] plugins = PluginManagerCore.getPlugins();
    for (IdeaPluginDescriptor plugin : plugins) {
      if (!PluginManagerCore.shouldSkipPlugin(plugin)) {
        ComponentConfig[] componentConfigs = getComponentConfigs(plugin);

        for (ComponentConfig componentConfig : componentConfigs) {
          registerComponent(componentConfig, plugin);
        }
      }
    }

    myComponentsRegistry.loadClasses(myNotLazyServices, builder);
  }

  protected void bootstrapInjectingContainer(@Nonnull InjectingContainerBuilder builder) {
  }

  @Nonnull
  protected ComponentConfig[] getComponentConfigs(IdeaPluginDescriptor ideaPluginDescriptor) {
    return ComponentConfig.EMPTY_ARRAY;
  }

  private void loadServices(List<Class> notLazyServices, InjectingContainerBuilder builder) {
    ExtensionPointName<ServiceDescriptor> ep = getServiceExtensionPointName();
    if (ep != null) {
      ExtensionPointImpl<ServiceDescriptor> extensionPoint = (ExtensionPointImpl<ServiceDescriptor>)myExtensionsArea.getExtensionPoint(ep);
      // there no injector at that level - build it via hardcode
      List<ServiceDescriptor> descriptorList = extensionPoint.buildUnsafe(aClass -> new ServiceDescriptor());
      // and cache it
      extensionPoint.setExtensionCache(descriptorList);

      for (ServiceDescriptor descriptor : extensionPoint.getExtensionList()) {
        InjectingKey<Object> key = InjectingKey.of(descriptor.getInterface(), getTargetClassLoader(descriptor.getPluginDescriptor()));
        InjectingKey<Object> implKey = InjectingKey.of(descriptor.getImplementation(), getTargetClassLoader(descriptor.getPluginDescriptor()));

        InjectingPoint<Object> point = builder.bind(key);
        // bind to impl class
        point.to(implKey);
        // require singleton
        point.forceSingleton();
        // remap object initialization
        point.factory(objectProvider -> runServiceInitialize(descriptor, objectProvider::get));

        point.injectListener((time, instance) -> {

          if (myChecker.containsKey(key.getTargetClass())) {
            throw new IllegalArgumentException("Duplicate init of " + key.getTargetClass());
          }
          myChecker.put(key.getTargetClass(), instance);

          if (instance instanceof Disposable) {
            Disposer.register(this, (Disposable)instance);
          }

          initializeIfStorableComponent(instance, true, descriptor.isLazy());
        });

        if (!descriptor.isLazy()) {
          // if service is not lazy - add it for init at start
          notLazyServices.add(key.getTargetClass());
        }
      }
    }
  }

  private static ClassLoader getTargetClassLoader(IdeaPluginDescriptor pluginDescriptor) {
    return pluginDescriptor != null ? pluginDescriptor.getPluginClassLoader() : ComponentManagerImpl.class.getClassLoader();
  }

  protected <T> T runServiceInitialize(@Nonnull ServiceDescriptor descriptor, @Nonnull Supplier<T> runnable) {
    return runnable.get();
  }

  @Nullable
  protected ExtensionPointName<ServiceDescriptor> getServiceExtensionPointName() {
    return null;
  }

  public boolean initializeIfStorableComponent(@Nonnull Object component, boolean service, boolean lazy) {
    return false;
  }

  protected void handleInitComponentError(@Nonnull Throwable ex, @Nullable String componentClassName, @Nullable ComponentConfig config) {
    LOG.error(ex);
  }

  private void registerComponent(ComponentConfig config, IdeaPluginDescriptor pluginDescriptor) {
    config.prepareClasses();

    config.pluginDescriptor = pluginDescriptor;
    myComponentsRegistry.addConfig(config);
  }

  @Override
  public void initNotLazyServices() {
    try {
      if (myComponentsCreated) {
        throw new IllegalArgumentException("Injector already build");
      }

      for (Class<?> componentInterface : myNotLazyServices) {
        ProgressIndicator indicator = ProgressManager.getGlobalProgressIndicator();
        if (indicator != null) {
          indicator.checkCanceled();
        }

        Object component = getComponent(componentInterface);
        assert component != null;
      }
    }
    finally {
      myComponentsCreated = true;
    }
  }

  @Override
  public int getNotLazyServicesCount() {
    return myNotLazyServices.size();
  }

  @Nonnull
  @Override
  public MessageBus getMessageBus() {
    if (myDisposed) {
      ProgressManager.checkCanceled();
      throw new AssertionError("Already disposed");
    }
    assert myMessageBus != null : "Not initialized yet";
    return myMessageBus;
  }

  public boolean isComponentsCreated() {
    return myComponentsCreated;
  }

  @Override
  public <T> T getComponent(@Nonnull Class<T> clazz) {
    if (myDisposed) {
      ProgressManager.checkCanceled();
      throw new AssertionError("Already disposed: " + this);
    }
    return getInjectingContainer().getInstance(clazz);
  }

  @Nonnull
  @Override
  public InjectingContainer getInjectingContainer() {
    InjectingContainer container = myInjectingContainer;
    if (container == null || myDisposed) {
      ProgressManager.checkCanceled();
      throw new AssertionError("Already disposed: " + toString());
    }
    return container;
  }

  @Nonnull
  @Override
  public ExtensionsArea getExtensionsArea() {
    return myExtensionsArea;
  }

  @Override
  public boolean isDisposed() {
    return myDisposed || temporarilyDisposed;
  }

  @TestOnly
  public void setTemporarilyDisposed(boolean disposed) {
    temporarilyDisposed = disposed;
  }

  public ComponentConfig getConfig(Class componentImplementation) {
    return myComponentsRegistry.getConfig(componentImplementation);
  }

  @Override
  @Nonnull
  public Condition getDisposed() {
    return myDisposedCondition;
  }

  @Nonnull
  public static String getComponentName(@Nonnull final Object component) {
    if (component instanceof NamedComponent) {
      return ((NamedComponent)component).getComponentName();
    }
    else {
      return component.getClass().getName();
    }
  }

  protected boolean logSlowComponents() {
    return LOG.isDebugEnabled() || ApplicationProperties.isInSandbox();
  }

  @Override
  @RequiredUIAccess
  public void dispose() {
    Application.get().assertIsDispatchThread();

    if (myMessageBus != null) {
      myMessageBus.dispose();
      myMessageBus = null;
    }

    myExtensionsArea = null;
    myInjectingContainer.dispose();
    myInjectingContainer = null;

    myComponentsRegistry = null;
    myComponentsCreated = false;
    myNotLazyServices.clear();
    myDisposed = true;
  }
}
