/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.awt.TargetAWT;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.io.IOException;

/**
 * @author gregsh
 *
 * Created on 1/19/15
 */
public abstract class RootType {

  public static final ExtensionPointName<RootType> ROOT_EP = ExtensionPointName.create("com.intellij.scratch.rootType");

  @Nonnull
  public static RootType[] getAllRootIds() {
    return Extensions.getExtensions(ROOT_EP);
  }

  @Nonnull
  public static RootType findById(@Nonnull String id) {
    for (RootType type : getAllRootIds()) {
      if (id.equals(type.getId())) return type;
    }
    throw new AssertionError(id);
  }

  @Nonnull
  public static <T extends RootType> T findByClass(Class<T> aClass) {
    return Extensions.findExtension(ROOT_EP, aClass);
  }

  private final String myId;
  private final String myDisplayName;

  protected RootType(@Nonnull String id, @Nullable String displayName) {
    myId = id;
    myDisplayName = displayName;
  }

  @Nonnull
  public final String getId() {
    return myId;
  }

  @Nullable
  public final String getDisplayName() {
    return myDisplayName;
  }

  public boolean isHidden() {
    return StringUtil.isEmpty(myDisplayName);
  }

  public boolean containsFile(@Nullable VirtualFile file) {
    if (file == null) return false;
    ScratchFileService service = ScratchFileService.getInstance();
    return service != null && service.getRootType(file) == this;
  }

  @Nullable
  public Language substituteLanguage(@Nonnull Project project, @Nonnull VirtualFile file) {
    return null;
  }

  @Nullable
  public Icon substituteIcon(@Nonnull Project project, @Nonnull VirtualFile file) {
    Language language = substituteLanguage(project, file);
    FileType fileType = LanguageUtil.getLanguageFileType(language);
    if (fileType == null) fileType = ScratchUtil.getFileTypeFromName(file);
    return fileType != null ? TargetAWT.to(fileType.getIcon()) : null;
  }

  @Nullable
  public String substituteName(@Nonnull Project project, @Nonnull VirtualFile file) {
    return null;
  }

  public VirtualFile findFile(@Nullable Project project, @Nonnull String pathName, ScratchFileService.Option option) throws IOException {
    return ScratchFileService.getInstance().findFile(this, pathName, option);
  }

  public void fileOpened(@Nonnull VirtualFile file, @Nonnull FileEditorManager source) {
  }

  public void fileClosed(@Nonnull VirtualFile file, @Nonnull FileEditorManager source) {
  }

  public boolean isIgnored(@Nonnull Project project, @Nonnull VirtualFile element) {
    return false;
  }

  public void registerTreeUpdater(@Nonnull Project project, @Nonnull AbstractTreeBuilder builder) {
  }

}
