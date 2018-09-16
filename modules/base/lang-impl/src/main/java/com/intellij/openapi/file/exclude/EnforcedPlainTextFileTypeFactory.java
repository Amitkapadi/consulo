/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.file.exclude;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;

import javax.annotation.Nonnull;

/**
 * Registers text file type for particular virtual files rather than using .txt extension.
 *
 * @author Rustam Vishnyakov
 */
public class EnforcedPlainTextFileTypeFactory extends FileTypeFactory {

  public static final Image ENFORCED_PLAIN_TEXT_ICON;

  static {
    ENFORCED_PLAIN_TEXT_ICON = ImageEffects.layered(AllIcons.FileTypes.Text, AllIcons.Nodes.ExcludedFromCompile);
  }

  private final FileTypeIdentifiableByVirtualFile myFileType;

  public EnforcedPlainTextFileTypeFactory() {
    myFileType = new FileTypeIdentifiableByVirtualFile() {

      @Override
      public boolean isMyFileType(VirtualFile file) {
        if (isMarkedAsPlainText(file)) {
          return true;
        }
        return false;
      }

      @Nonnull
      @Override
      public String getId() {
        return "Enforced Plain Text";
      }

      @Nonnull
      @Override
      public String getDescription() {
        return "Enforced Plain Text";
      }

      @Nonnull
      @Override
      public String getDefaultExtension() {
        return "fakeTxt";
      }

      @Override
      public Image getIcon() {
        return ENFORCED_PLAIN_TEXT_ICON;
      }
    };
  }

  @Override
  public void createFileTypes(final @Nonnull FileTypeConsumer consumer) {
    consumer.consume(myFileType, "");
  }

  private static boolean isMarkedAsPlainText(VirtualFile file) {
    EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    if (typeManager == null) return false;
    return typeManager.isMarkedAsPlainText(file);
  }
}
