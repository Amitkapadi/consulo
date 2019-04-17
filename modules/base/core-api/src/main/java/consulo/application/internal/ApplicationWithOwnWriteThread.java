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
package consulo.application.internal;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.ThrowableComputable;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2018-05-12
 */
public interface ApplicationWithOwnWriteThread extends Application {
  default boolean isWriteThreadEnabled() {
    return true;
  }

  @Nonnull
  AccessToken acquireWriteActionLockInternal(Class<?> callerClass);

  @Nonnull
  <T> AsyncResult<T> pushWriteAction(@Nonnull Class<?> caller, @Nonnull ThrowableComputable<T, Throwable> computable);
}
