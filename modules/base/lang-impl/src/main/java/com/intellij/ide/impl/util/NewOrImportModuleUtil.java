/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.impl.util;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.annotations.RequiredReadAction;
import consulo.annotations.RequiredWriteAction;
import consulo.ide.newProject.NewModuleBuilderProcessor;
import consulo.ide.wizard.newModule.NewModuleWizardContext;
import consulo.logging.Logger;
import consulo.moduleImport.ModuleImportContext;
import consulo.moduleImport.ModuleImportProvider;
import consulo.roots.impl.ExcludedContentFolderTypeProvider;
import consulo.ui.RequiredUIAccess;

import javax.annotation.Nonnull;
import java.io.File;

public class NewOrImportModuleUtil {
  private static final Logger LOG = Logger.getInstance(NewOrImportModuleUtil.class);

  @Nonnull
  @RequiredReadAction
  public static Module doCreate(@Nonnull NewModuleWizardContext context, @Nonnull NewModuleBuilderProcessor processor, @Nonnull final Project project, @Nonnull final VirtualFile baseDir) {
    return doCreate(context, processor, ModuleManager.getInstance(project).getModifiableModel(), baseDir, true);
  }

  @Nonnull
  public static Module doCreate(@Nonnull NewModuleWizardContext context,
                                @Nonnull NewModuleBuilderProcessor processor,
                                @Nonnull final ModifiableModuleModel modifiableModel,
                                @Nonnull final VirtualFile baseDir,
                                final boolean requireModelCommit) {
    return WriteAction.compute(() -> doCreateImpl(context, processor, modifiableModel, baseDir, requireModelCommit));
  }

  @SuppressWarnings("unchecked")
  @Nonnull
  @RequiredWriteAction
  private static Module doCreateImpl(@Nonnull NewModuleWizardContext context,
                                     @Nonnull NewModuleBuilderProcessor processor,
                                     @Nonnull ModifiableModuleModel modifiableModel,
                                     @Nonnull VirtualFile baseDir,
                                     boolean requireModelCommit) {
    String name = StringUtil.notNullize(context.getName(), baseDir.getName());

    Module newModule = modifiableModel.newModule(name, baseDir.getPath());

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(newModule);
    ModifiableRootModel modifiableModelForModule = moduleRootManager.getModifiableModel();
    ContentEntry contentEntry = modifiableModelForModule.addContentEntry(baseDir);

    if (context.isNewProject()) {
      contentEntry.addFolder(contentEntry.getUrl() + "/" + Project.DIRECTORY_STORE_FOLDER, ExcludedContentFolderTypeProvider.getInstance());
    }

    processor.process(context, contentEntry, modifiableModelForModule);

    modifiableModelForModule.commit();

    if (requireModelCommit) {
      modifiableModel.commit();
    }

    baseDir.refresh(true, true);
    return newModule;
  }

  @Nonnull
  @RequiredUIAccess
  public static <T extends ModuleImportContext> AsyncResult<Project> importProject(@Nonnull T context, @Nonnull ModuleImportProvider<T> importProvider) {
    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    final String projectFilePath = context.getPath();
    String projectName = context.getName();

    try {
      File projectDir = new File(projectFilePath);
      FileUtil.ensureExists(projectDir);
      File projectConfigDir = new File(projectDir, Project.DIRECTORY_STORE_FOLDER);
      FileUtil.ensureExists(projectConfigDir);

      final Project newProject = projectManager.createProject(projectName, projectFilePath);

      if (newProject == null) return AsyncResult.rejected();

      newProject.save();

      ModifiableModuleModel modifiableModel = ModuleManager.getInstance(newProject).getModifiableModel();

      importProvider.process(context, newProject, modifiableModel, module -> {
      });

      WriteAction.runAndWait(modifiableModel::commit);

      newProject.save();

      context.dispose();

      return AsyncResult.resolved(newProject);
    }
    catch (Exception e) {
      context.dispose();

      return AsyncResult.<Project>undefined().rejectWithThrowable(e);
    }
  }
}
