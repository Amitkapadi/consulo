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
package com.intellij.openapi.module.impl;

import com.intellij.openapi.module.LoadedModuleDescription;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * from kotlin
 */
public class LoadedModuleDescriptionImpl implements LoadedModuleDescription {
  private final Module myModule;

  public LoadedModuleDescriptionImpl(Module module) {
    myModule = module;
  }

  @NotNull
  @Override
  public Module getModule() {
    return myModule;
  }

  @NotNull
  @Override
  public String getName() {
    return myModule.getName();
  }

  @NotNull
  @Override
  public List<String> getDependencyModuleNames() {
    return Arrays.asList(ModuleRootManager.getInstance(myModule).getDependencyModuleNames());
  }
}
