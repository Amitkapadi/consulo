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
package com.intellij.injected.editor;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class InjectedCaret implements Caret {
  private final EditorWindow myEditorWindow;
  final Caret myDelegate;

  InjectedCaret(EditorWindow window, Caret delegate) {
    myEditorWindow = window;
    myDelegate = delegate;
  }

  @Nonnull
  @Override
  public Editor getEditor() {
    return myEditorWindow;
  }

  @Nonnull
  @Override
  public CaretModel getCaretModel() {
    return myEditorWindow.getCaretModel();
  }

  public Caret getDelegate() {
    return myDelegate;
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }

  @Override
  public void moveCaretRelatively(int columnShift, int lineShift, boolean withSelection, boolean scrollToCaret) {
    myDelegate.moveCaretRelatively(columnShift, lineShift, withSelection, scrollToCaret);
  }

  @Override
  public void moveToLogicalPosition(@Nonnull LogicalPosition pos) {
    LogicalPosition hostPos = myEditorWindow.injectedToHost(pos);
    myDelegate.moveToLogicalPosition(hostPos);
  }

  @Override
  public void moveToVisualPosition(@Nonnull VisualPosition pos) {
    LogicalPosition hostPos = myEditorWindow.injectedToHost(myEditorWindow.visualToLogicalPosition(pos));
    myDelegate.moveToLogicalPosition(hostPos);
  }

  @Override
  public void moveToOffset(int offset) {
    moveToOffset(offset, false);
  }

  @Override
  public void moveToOffset(int offset, boolean locateBeforeSoftWrap) {
    int hostOffset = myEditorWindow.getDocument().injectedToHost(offset);
    myDelegate.moveToOffset(hostOffset, locateBeforeSoftWrap);
  }

  @Override
  public boolean isUpToDate() {
    return myDelegate.isUpToDate();
  }

  @Nonnull
  @Override
  public LogicalPosition getLogicalPosition() {
    LogicalPosition hostPos = myDelegate.getLogicalPosition();
    return myEditorWindow.hostToInjected(hostPos);
  }

  @Nonnull
  @Override
  public VisualPosition getVisualPosition() {
    LogicalPosition logicalPosition = getLogicalPosition();
    return myEditorWindow.logicalToVisualPosition(logicalPosition);
  }

  @Override
  public int getOffset() {
    return myEditorWindow.getDocument().hostToInjected(myDelegate.getOffset());
  }

  @Override
  public int getVisualLineStart() {
    return myEditorWindow.getDocument().hostToInjected(myDelegate.getVisualLineStart());
  }

  @Override
  public int getVisualLineEnd() {
    return myEditorWindow.getDocument().hostToInjected(myDelegate.getVisualLineEnd());
  }

  @Override
  public int getSelectionStart() {
    return myEditorWindow.getDocument().hostToInjected(myDelegate.getSelectionStart());
  }

  @Nonnull
  @Override
  public VisualPosition getSelectionStartPosition() {
    return myDelegate.getSelectionStartPosition();
  }

  @Override
  public int getSelectionEnd() {
    return myEditorWindow.getDocument().hostToInjected(myDelegate.getSelectionEnd());
  }

  @Nonnull
  @Override
  public VisualPosition getSelectionEndPosition() {
    return myDelegate.getSelectionEndPosition();
  }

  @Nullable
  @Override
  public String getSelectedText() {
    return myDelegate.getSelectedText();
  }

  @Override
  public int getLeadSelectionOffset() {
    return myEditorWindow.getDocument().hostToInjected(myDelegate.getLeadSelectionOffset());
  }

  @Nonnull
  @Override
  public VisualPosition getLeadSelectionPosition() {
    return myDelegate.getLeadSelectionPosition();
  }

  @Override
  public boolean hasSelection() {
    return myDelegate.hasSelection();
  }

  @Override
  public void setSelection(int startOffset, int endOffset) {
    TextRange hostRange = myEditorWindow.getDocument().injectedToHost(new ProperTextRange(startOffset, endOffset));
    myDelegate.setSelection(hostRange.getStartOffset(), hostRange.getEndOffset());
  }

  @Override
  public void setSelection(int startOffset, int endOffset, boolean updateSystemSelection) {
    TextRange hostRange = myEditorWindow.getDocument().injectedToHost(new ProperTextRange(startOffset, endOffset));
    myDelegate.setSelection(hostRange.getStartOffset(), hostRange.getEndOffset(), updateSystemSelection);
  }

  @Override
  public void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    TextRange hostRange = myEditorWindow.getDocument().injectedToHost(new ProperTextRange(startOffset, endOffset));
    myDelegate.setSelection(hostRange.getStartOffset(), endPosition, hostRange.getEndOffset());
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
    TextRange hostRange = myEditorWindow.getDocument().injectedToHost(new ProperTextRange(startOffset, endOffset));
    myDelegate.setSelection(startPosition, hostRange.getStartOffset(), endPosition, hostRange.getEndOffset());
  }

  @Override
  public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset, boolean updateSystemSelection) {
    TextRange hostRange = myEditorWindow.getDocument().injectedToHost(new ProperTextRange(startOffset, endOffset));
    myDelegate.setSelection(startPosition, hostRange.getStartOffset(), endPosition, hostRange.getEndOffset(), updateSystemSelection);
  }

  @Override
  public void removeSelection() {
    myDelegate.removeSelection();
  }

  @Override
  public void selectLineAtCaret() {
    myDelegate.selectLineAtCaret();
  }

  @Override
  public void selectWordAtCaret(boolean honorCamelWordsSettings) {
    myDelegate.selectWordAtCaret(honorCamelWordsSettings);
  }

  @Nullable
  @Override
  public Caret clone(boolean above) {
    Caret clone = myDelegate.clone(above);
    return clone == null ? null : new InjectedCaret(myEditorWindow, clone);
  }

  @Override
  public void dispose() {
    //noinspection SSBasedInspection
    myDelegate.dispose();
  }

  @Nonnull
  @Override
  public <T> T putUserDataIfAbsent(@Nonnull Key<T> key, @Nonnull T value) {
    return myDelegate.putUserDataIfAbsent(key, value);
  }

  @Override
  public <T> boolean replace(@Nonnull Key<T> key, @Nullable T oldValue, @Nullable T newValue) {
    return myDelegate.replace(key, oldValue, newValue);
  }

  @Nullable
  @Override
  public <T> T getUserData(@Nonnull Key<T> key) {
    return myDelegate.getUserData(key);
  }

  @Override
  public <T> void putUserData(@Nonnull Key<T> key, @Nullable T value) {
    myDelegate.putUserData(key, value);
  }

  @Override
  public boolean isAtRtlLocation() {
    return myDelegate.isAtRtlLocation();
  }

  @Override
  public boolean isAtBidiRunBoundary() {
    return myDelegate.isAtBidiRunBoundary();
  }

  @Nonnull
  @Override
  public CaretVisualAttributes getVisualAttributes() {
    return myDelegate.getVisualAttributes();
  }

  @Override
  public void setVisualAttributes(@Nonnull CaretVisualAttributes attributes) {
    myDelegate.setVisualAttributes(attributes);
  }
}
