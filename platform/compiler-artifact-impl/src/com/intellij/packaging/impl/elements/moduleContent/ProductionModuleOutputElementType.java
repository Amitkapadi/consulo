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
package com.intellij.packaging.impl.elements.moduleContent;

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentFolderType;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElementBase;
import com.intellij.packaging.impl.elements.moduleContent.elementImpl.ProductionModuleOutputPackagingElement;
import org.consulo.util.pointers.NamedPointer;
import org.jetbrains.annotations.NotNull;

/**
* @author nik
*/
public class ProductionModuleOutputElementType extends ModuleOutputElementTypeBase<ProductionModuleOutputPackagingElement> {
  public static final ProductionModuleOutputElementType ELEMENT_TYPE = new ProductionModuleOutputElementType();

  ProductionModuleOutputElementType() {
    super("module-output", CompilerBundle.message("element.type.name.module.output"));
  }

  @Override
  @NotNull
  public ProductionModuleOutputPackagingElement createEmpty(@NotNull Project project) {
    return new ProductionModuleOutputPackagingElement(project);
  }

  @NotNull
  @Override
  protected ContentFolderType getContentFolderType() {
    return ContentFolderType.PRODUCTION;
  }

  @Override
  protected ModuleOutputPackagingElementBase createElement(@NotNull Project project, @NotNull NamedPointer<Module> pointer) {
    return new ProductionModuleOutputPackagingElement(project, pointer);
  }
}
