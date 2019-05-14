// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.concurrent.Future;

public interface CancellablePromise<T> extends Promise<T>, Future<T> {
  void cancel();

  /**
   * Create a promise that is resolved with the given value.
   */
  @Nonnull
  static <T> CancellablePromise<T> resolve(@Nullable T result) {
    if (result == null) {
      //noinspection unchecked
      return (CancellablePromise<T>)InternalPromiseUtil.FULFILLED_PROMISE.getValue();
    }
    else {
      return new DonePromise<>(InternalPromiseUtil.PromiseValue.createFulfilled(result));
    }
  }
}