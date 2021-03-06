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

package com.intellij.codeInsight.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import javax.annotation.Nonnull;

/**
 * @author yole
 */
public interface ReferenceImporter {
  ExtensionPointName<ReferenceImporter> EP_NAME = ExtensionPointName.create("com.intellij.referenceImporter");

  boolean autoImportReferenceAtCursor(@Nonnull Editor editor, @Nonnull PsiFile file);

  /**
   * @deprecated implement {@link com.intellij.codeInspection.HintAction#fixSilently(Editor)} instead.
   */
  @Deprecated
  default boolean autoImportReferenceAt(@Nonnull Editor editor, @Nonnull PsiFile file, int offset) {
    return false;
  }
}
