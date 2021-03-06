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
package consulo.consulo.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.IndentHelper;
import consulo.annotation.access.RequiredReadAction;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2018-09-26
 */
public interface IndentHelperExtension {
  ExtensionPointName<IndentHelperExtension> EP_NAME = ExtensionPointName.create("com.intellij.codeStyleIndentHelperExtension");

  int TOO_BIG_WALK_THRESHOLD = 450;

  boolean isAvaliable(@Nonnull PsiFile file);

  @RequiredReadAction
  int getIndentInner(@Nonnull IndentHelper indentHelper, @Nonnull PsiFile file, @Nonnull final ASTNode element, boolean includeNonSpace, int recursionLevel);
}
