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
package com.intellij.openapi.diff;

import consulo.disposer.Disposable;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.awt.*;


public interface DiffTool {
  /**
   * @see DiffManager#getIdeaDiffTool()
   */
  @NonNls
  Object HINT_SHOW_MODAL_DIALOG = "showModalDialog";

  /**
   * @see DiffManager#getIdeaDiffTool()
   */
  @NonNls
  Object HINT_SHOW_FRAME = "showNotModalWindow";

  /**
   * @see DiffManager#getIdeaDiffTool()
   */
  @NonNls
  Object HINT_SHOW_NOT_MODAL_DIALOG = "showNotModalDialog";

  @NonNls
  Object HINT_DIFF_IS_APPROXIMATE = "warnThatDiffIsApproximate";

  /**
   * @see DiffManager#getIdeaDiffTool()
   */
  @NonNls
  Object HINT_DO_NOT_IGNORE_WHITESPACES = "doNotIgnoreWhitespaces";

  @NonNls
  Object HINT_ALLOW_NO_DIFFERENCES = "allowNoDifferences";

  @NonNls
  Key SCROLL_TO_LINE = Key.create("scrollToLine");

  /**
   * Opens window to compare contents. Clients should call {@link #canShow(com.intellij.openapi.diff.DiffRequest)} first.
   */
  void show(DiffRequest request);

  /**
   * @return true if this tool can comare given contents
   */
  boolean canShow(DiffRequest request);

  @Nullable
  DiffViewer createComponent(final String title, final DiffRequest request, Window window, @Nonnull Disposable parentDisposable);
}
