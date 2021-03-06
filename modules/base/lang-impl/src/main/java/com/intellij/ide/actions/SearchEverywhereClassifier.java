// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Philipp Smorygo
 */
public interface SearchEverywhereClassifier {
  class EP_Manager {
    private EP_Manager() {
    }

    public static boolean isClass(@Nullable Object o) {
      for (SearchEverywhereClassifier classifier : SearchEverywhereClassifier.EP_NAME.getExtensionList()) {
        if (classifier.isClass(o)) return true;
      }
      return false;
    }

    public static boolean isSymbol(@Nullable Object o) {
      for (SearchEverywhereClassifier classifier : SearchEverywhereClassifier.EP_NAME.getExtensionList()) {
        if (classifier.isSymbol(o)) return true;
      }
      return false;
    }

    @Nullable
    public static VirtualFile getVirtualFile(@Nonnull Object o) {
      for (SearchEverywhereClassifier classifier : SearchEverywhereClassifier.EP_NAME.getExtensionList()) {
        VirtualFile virtualFile = classifier.getVirtualFile(o);
        if (virtualFile != null) return virtualFile;
      }
      return null;
    }

    @Nullable
    public static Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      for (SearchEverywhereClassifier classifier : SearchEverywhereClassifier.EP_NAME.getExtensionList()) {
        Component component = classifier.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (component != null) return component;
      }
      return null;
    }

    @Nullable
    public static GlobalSearchScope getProjectScope(@Nonnull Project project) {
      for (SearchEverywhereClassifier classifier : SearchEverywhereClassifier.EP_NAME.getExtensionList()) {
        GlobalSearchScope scope = classifier.getProjectScope(project);
        if (scope != null) return scope;
      }
      return null;
    }
  }

  ExtensionPointName<SearchEverywhereClassifier> EP_NAME = ExtensionPointName.create("com.intellij.searchEverywhereClassifier");

  boolean isClass(@Nullable Object o);

  boolean isSymbol(@Nullable Object o);

  @Nullable
  VirtualFile getVirtualFile(@Nonnull Object o);

  @Nullable
  Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus);

  @Nullable
  default GlobalSearchScope getProjectScope(@Nonnull Project project) {
    return null;
  }
}
