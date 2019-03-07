/*
 * Copyright 2013-2019 consulo.io
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
package consulo.disposer.internal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.TraceableDisposable;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @author VISTALL
 * @since 2019-03-07
 */
public abstract class DisposerInternal {
  public static final DisposerInternal ourInstance = loadSingleOrError(DisposerInternal.class);

  public abstract void register(@Nonnull Disposable parent, @Nonnull Disposable child, @NonNls @Nullable final String key);

  public abstract boolean isDisposed(@Nonnull Disposable disposable);

  public abstract void dispose(@Nonnull Disposable disposable, boolean processUnregistered);

  public abstract void disposeChildAndReplace(@Nonnull Disposable toDispose, @Nonnull Disposable toReplace);

  public abstract TraceableDisposable newTraceDisposable(boolean debug);

  public abstract Disposable get(@Nonnull String key);

  public abstract boolean isDebugMode();

  public abstract void assertIsEmpty();

  public abstract boolean setDebugMode(boolean debugMode);

  // TODO [VISTALL] use consulo.util.ServiceLoaderUtil
  @Nonnull
  private static <T> T loadSingleOrError(@Nonnull Class<T> clazz) {
    ServiceLoader<T> serviceLoader = ServiceLoader.load(clazz, clazz.getClassLoader());
    Iterator<T> iterator = serviceLoader.iterator();
    if (iterator.hasNext()) {
      return iterator.next();
    }
    throw new Error("Unable to find '" + clazz.getName() + "' implementation");
  }
}


