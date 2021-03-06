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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import consulo.annotation.DeprecationInfo;
import consulo.fileEditor.impl.EditorWithProviderComposite;
import consulo.logging.Logger;

import javax.annotation.Nonnull;

/**
 * Author: msk
 */
@Deprecated
@DeprecationInfo("Desktop only")
public class DesktopEditorWithProviderComposite extends DesktopEditorComposite implements EditorWithProviderComposite {
  private static final Logger LOG = Logger.getInstance(DesktopEditorWithProviderComposite.class);
  private FileEditorProvider[] myProviders;

  public DesktopEditorWithProviderComposite(@Nonnull VirtualFile file, @Nonnull FileEditor[] editors, @Nonnull FileEditorProvider[] providers, @Nonnull FileEditorManagerEx fileEditorManager) {
    super(file, editors, fileEditorManager);
    myProviders = providers;
  }

  @Override
  @Nonnull
  public FileEditorProvider[] getProviders() {
    return myProviders;
  }

  @Override
  public boolean isModified() {
    final FileEditor[] editors = getEditors();
    for (FileEditor editor : editors) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Nonnull
  public FileEditorWithProvider getSelectedEditorWithProvider() {
    LOG.assertTrue(myEditors.length > 0, myEditors.length);
    if (myEditors.length == 1) {
      LOG.assertTrue(myTabbedPaneWrapper == null);
      return new FileEditorWithProvider(myEditors[0], myProviders[0]);
    }
    else { // we have to get myEditor from tabbed pane
      LOG.assertTrue(myTabbedPaneWrapper != null);
      int index = myTabbedPaneWrapper.getSelectedIndex();
      if (index == -1) {
        index = 0;
      }
      LOG.assertTrue(index >= 0, index);
      LOG.assertTrue(index < myEditors.length, index);
      return new FileEditorWithProvider(myEditors[index], myProviders[index]);
    }
  }

  @Override
  @Nonnull
  public HistoryEntry currentStateAsHistoryEntry() {
    final FileEditor[] editors = getEditors();
    final FileEditorState[] states = new FileEditorState[editors.length];
    for (int j = 0; j < states.length; j++) {
      states[j] = editors[j].getState(FileEditorStateLevel.FULL);
      LOG.assertTrue(states[j] != null);
    }
    final int selectedProviderIndex = ArrayUtil.find(editors, getSelectedEditor());
    LOG.assertTrue(selectedProviderIndex != -1);
    final FileEditorProvider[] providers = getProviders();
    return HistoryEntry.createLight(getFile(), providers, states, providers[selectedProviderIndex]);
  }

  @Override
  public void addEditor(@Nonnull FileEditor editor, FileEditorProvider provider) {
    addEditor(editor);
    myProviders = ArrayUtil.append(myProviders, provider);
  }
}
