/*
 * Copyright 2013-2018 consulo.io
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
package consulo.injecting.pico;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.pico.ConstructorInjectionComponentAdapter;
import consulo.injecting.PostInjectListener;
import consulo.injecting.key.InjectingKey;
import org.picocontainer.*;

import javax.annotation.Nonnull;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 2018-08-23
 */
public class BaseComponentAdapter<T> implements ComponentAdapter {
  private static final Logger LOGGER = Logger.getInstance(BaseComponentAdapter.class);

  private final InjectingKey<T> myInterfaceKey;

  private final Object myLock = new Object();

  private InjectingKey<? extends T> myImplementationKey;

  @Nonnull
  private PostInjectListener<T> myAfterInjectionListener = (time, object) -> {};

  private Function<Provider<T>, T> myRemap = Provider::get;

  private T myInstanceIfSingleton;

  private boolean myForceSingleton;

  private boolean myInitializeProgress;

  public BaseComponentAdapter(InjectingKey<T> interfaceKey) {
    myInterfaceKey = interfaceKey;
    myImplementationKey = interfaceKey;
  }

  public void setAfterInjectionListener(@Nonnull PostInjectListener<T> afterInjectionListener) {
    myAfterInjectionListener = afterInjectionListener;
  }

  public void setRemap(Function<Provider<T>, T> remap) {
    myRemap = remap;
  }

  public void setImplementationKey(InjectingKey<? extends T> implementationKey) {
    myImplementationKey = implementationKey;
  }

  public void setForceSingleton() {
    myForceSingleton = true;
  }

  @Override
  public String getComponentKey() {
    return myInterfaceKey.getTargetClassName();
  }

  @Override
  public Class getComponentImplementation() {
    return myImplementationKey.getTargetClass();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object getComponentInstance(@Nonnull PicoContainer container) throws PicoInitializationException, PicoIntrospectionException {
    T instance = myInstanceIfSingleton;
    if (instance != null) {
      return instance;
    }

    boolean isSingleton, isAnnotationSingleton;
    synchronized (myLock) {
      // double check
      instance = myInstanceIfSingleton;
      if (instance != null) {
        return instance;
      }

      isAnnotationSingleton = myImplementationKey.getTargetClass().isAnnotationPresent(Singleton.class);

      isSingleton = myForceSingleton || isAnnotationSingleton;

      if (myInitializeProgress) {
        throw new IllegalArgumentException("Cycle initialization");
      }

      if (isSingleton) {
        myInitializeProgress = true;
      }

      long l = System.nanoTime();

      ConstructorInjectionComponentAdapter delegate = new ConstructorInjectionComponentAdapter(getComponentKey(), getComponentImplementation());
      instance = myRemap.apply(() -> (T)delegate.getComponentInstance(container));

      myAfterInjectionListener.afterInject(l, instance);

      if (isSingleton) {
        myInitializeProgress = false;
        myInstanceIfSingleton = instance;
      }
    }

    if (isSingleton && !isAnnotationSingleton) {
      LOGGER.warn("Class " + myImplementationKey.getTargetClass().getName() + " is not annotated by @Singleton");
    }

    return instance;
  }

  @Override
  public void verify(final PicoContainer container) throws PicoIntrospectionException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(final PicoVisitor visitor) {
    visitor.visitComponentAdapter(this);
  }

  @Override
  public String toString() {
    return "BaseComponentAdapter[" + myInterfaceKey.getTargetClassName() + "]";
  }
}