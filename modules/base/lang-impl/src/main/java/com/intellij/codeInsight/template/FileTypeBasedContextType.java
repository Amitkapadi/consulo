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

package com.intellij.codeInsight.template;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

/**
 * @author lesya
 */
public abstract class FileTypeBasedContextType extends TemplateContextType {
  private final LanguageFileType myFileType;

  protected FileTypeBasedContextType(@Nonnull @NonNls String id, @Nonnull String presentableName, @Nonnull LanguageFileType fileType) {
    super(id, presentableName);
    myFileType = fileType;
  }

  @Override
  public boolean isInContext(@Nonnull final PsiFile file, final int offset) {
    return myFileType == file.getFileType();
  }

  @Override
  public SyntaxHighlighter createHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(myFileType, null, null);
  }
}
