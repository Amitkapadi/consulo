/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.mock.MockApplicationEx;
import com.intellij.mock.MockProjectEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
@Deprecated
public abstract class PlatformLiteFixture extends UsefulTestCase {
  protected MockProjectEx myProject;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
  }

  public static MockApplicationEx getApplication() {
    return (MockApplicationEx)ApplicationManager.getApplication();
  }

  public void initApplication() {
    //if (ApplicationManager.getApplication() instanceof MockApplicationEx) return;
    final MockApplicationEx instance = new MockApplicationEx(getTestRootDisposable());
    ApplicationManager.setApplication(instance, getTestRootDisposable());
    getApplication().registerService(EncodingManager.class, EncodingManagerImpl.class);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    clearFields(this);
    myProject = null;
  }

  protected <T> void registerExtension(final ExtensionPointName<T> extensionPointName, @Nonnull final T t) {
  }

  protected <T> void registerExtension(final ExtensionPointName<T> extensionPointName, @Nonnull final T t, @Nonnull LoadingOrder loadingOrder) {
  }

  protected <T> void registerExtensionPoint(final ExtensionPointName<T> extensionPointName, final Class<T> aClass) {
  }
}
