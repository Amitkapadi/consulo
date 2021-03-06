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
package consulo.web.fileEditor.impl;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.PsiAwareFileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiManager;
import com.intellij.ui.docking.DockManager;
import consulo.fileEditor.impl.EditorWithProviderComposite;
import consulo.ui.Component;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * @author VISTALL
 * @since 2018-05-09
 */
@Singleton
public class WebPsiAwareFileEditorManagerImpl extends PsiAwareFileEditorManagerImpl {
  @Inject
  public WebPsiAwareFileEditorManagerImpl(Project project, PsiManager psiManager, Provider<WolfTheProblemSolver> problemSolver, DockManager dockManager) {
    super(project, psiManager, problemSolver, dockManager);
  }

  @Override
  protected void initUI() {
    if (mySplitters == null) {
      mySplitters = new WebEditorsSplitters(this);
    }
  }

  @Nonnull
  @Override
  public Component getUIComponent() {
    initUI();
    return mySplitters.getUIComponent();
  }

  @Nonnull
  @Override
  protected EditorWithProviderComposite createEditorWithProviderComposite(@Nonnull VirtualFile file,
                                                                          @Nonnull FileEditor[] editors,
                                                                          @Nonnull FileEditorProvider[] providers,
                                                                          @Nonnull FileEditorManagerEx fileEditorManager) {
    return new WebEditorWithProviderComposite(file, editors, providers, fileEditorManager);
  }
}
