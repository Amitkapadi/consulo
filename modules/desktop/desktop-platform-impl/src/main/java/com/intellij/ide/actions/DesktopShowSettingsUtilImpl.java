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
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.options.newEditor.DesktopSettingsDialog;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import consulo.ide.ide.base.BaseShowSettingsUtil;
import consulo.ui.RequiredUIAccess;
import consulo.ui.UIAccess;
import consulo.ui.impl.ModalityPerProjectEAPDescriptor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author max
 */
@Singleton
public class DesktopShowSettingsUtilImpl extends BaseShowSettingsUtil {
  private static final Logger LOG = Logger.getInstance(DesktopShowSettingsUtilImpl.class);

  private final AtomicBoolean myShown = new AtomicBoolean(false);

  private final DefaultProjectFactory myDefaultProjectFactory;

  @Inject
  DesktopShowSettingsUtilImpl(DefaultProjectFactory defaultProjectFactory) {
    myDefaultProjectFactory = defaultProjectFactory;
  }

  @RequiredUIAccess
  @Override
  public void showSettingsDialog(@Nullable Project project) {
    showSettingsImpl(project, UIAccess.current(), null);
  }

  @SuppressWarnings("deprecation")
  private void showSettingsImpl(@Nullable Project tempProject, @Nonnull UIAccess uiAccess, @Nullable Configurable toSelect) {
    Project actualProject = tempProject != null ? tempProject : myDefaultProjectFactory.getDefaultProject();

    Task.Backgroundable.queue(actualProject, "Opening " + CommonBundle.settingsTitle() + "...", i -> {
      Configurable[] configurables = buildConfigurables(actualProject);

      uiAccess.give(() -> {
        try {
          myShown.set(true);

          DesktopSettingsDialog dialog;
          if (ModalityPerProjectEAPDescriptor.is()) {
            dialog = new DesktopSettingsDialog(actualProject, configurables, toSelect, true);
          }
          else {
            dialog = new DesktopSettingsDialog(actualProject, configurables, toSelect);
          }

          dialog.showAsync().doWhenDone(() -> myShown.set(false));
        }
        catch (Exception e) {
          LOG.error(e);
        }
      });
    });
  }

  @RequiredUIAccess
  private static AsyncResult<Void> _showSettingsDialog(@Nonnull final Project project, @Nonnull Configurable[] configurables, @Nullable Configurable toSelect) {
    if (ModalityPerProjectEAPDescriptor.is()) {
      return new DesktopSettingsDialog(project, configurables, toSelect, true).showAsync();
    }
    else {
      return new DesktopSettingsDialog(project, configurables, toSelect).showAsync();
    }
  }

  @RequiredUIAccess
  @Override
  public void showSettingsDialog(@Nullable final Project project, final Class configurableClass) {
    assert Configurable.class.isAssignableFrom(configurableClass) : "Not a configurable: " + configurableClass.getName();

    Configurable[] configurables = buildConfigurables(project);

    Configurable config = findByClass(configurables, configurableClass);

    assert config != null : "Cannot find configurable: " + configurableClass.getName();

    @Nonnull Project nnProject = project != null ? project : ProjectManager.getInstance().getDefaultProject();
    _showSettingsDialog(nnProject, configurables, config);
  }

  @Nullable
  private static Configurable findByClass(Configurable[] configurables, Class configurableClass) {
    for (Configurable configurable : configurables) {
      if (configurableClass.isInstance(configurable)) {
        return configurable;
      }
    }
    return null;
  }

  @RequiredUIAccess
  @Override
  public void showSettingsDialog(@Nullable final Project project, @Nonnull final String nameToSelect) {
    Configurable[] configurables = buildConfigurables(project);

    Project actualProject = project != null ? project : ProjectManager.getInstance().getDefaultProject();

    DesktopSettingsDialog dialog;
    if (ModalityPerProjectEAPDescriptor.is()) {
      dialog = new DesktopSettingsDialog(actualProject, configurables, nameToSelect, true);
    }
    else {
      dialog = new DesktopSettingsDialog(actualProject, configurables, nameToSelect);
    }
    dialog.show();
  }

  @Override
  @RequiredUIAccess
  public void showSettingsDialog(@Nullable Project project, final String id2Select, final String filter) {
    Configurable[] configurables = buildConfigurables(project);

    Project actualProject = project != null ? project : ProjectManager.getInstance().getDefaultProject();

    final Configurable configurable2Select = findConfigurable2Select(id2Select, configurables);

    final DesktopSettingsDialog dialog;
    if (ModalityPerProjectEAPDescriptor.is()) {
      dialog = new DesktopSettingsDialog(actualProject, configurables, configurable2Select, true);
    }
    else {
      dialog = new DesktopSettingsDialog(actualProject, configurables, configurable2Select);
    }

    new UiNotifyConnector.Once(dialog.getContentPane(), new Activatable() {
      @Override
      public void showNotify() {
        final OptionsEditor editor = dialog.getDataUnchecked(OptionsEditor.KEY);
        LOG.assertTrue(editor != null);
        editor.select(configurable2Select, filter);
      }
    });
    dialog.showAsync();
  }

  @Nullable
  private static Configurable findConfigurable2Select(String id2Select, Configurable[] configurables) {
    for (Configurable configurable : configurables) {
      final Configurable conf = containsId(id2Select, configurable);
      if (conf != null) return conf;
    }
    return null;
  }

  @Nullable
  private static Configurable containsId(String id2Select, Configurable configurable) {
    if (configurable instanceof SearchableConfigurable && id2Select.equals(((SearchableConfigurable)configurable).getId())) {
      return configurable;
    }
    if (configurable instanceof SearchableConfigurable.Parent) {
      for (Configurable subConfigurable : ((SearchableConfigurable.Parent)configurable).getConfigurables()) {
        final Configurable config = containsId(id2Select, subConfigurable);
        if (config != null) return config;
      }
    }
    return null;
  }

  @RequiredUIAccess
  @Override
  public void showSettingsDialog(@Nonnull final Project project, final Configurable toSelect) {
    _showSettingsDialog(project, buildConfigurables(project), toSelect);
  }

  @RequiredUIAccess
  @Override
  public AsyncResult<Void> editConfigurable(@Nullable String title, Project project, Configurable configurable) {
    return editConfigurable(title, project, createDimensionKey(configurable), configurable);
  }

  @Override
  public <T extends Configurable> T findApplicationConfigurable(final Class<T> confClass) {
    return ConfigurableExtensionPointUtil.findApplicationConfigurable(confClass);
  }

  @Override
  public <T extends Configurable> T findProjectConfigurable(final Project project, final Class<T> confClass) {
    return ConfigurableExtensionPointUtil.findProjectConfigurable(project, confClass);
  }

  @RequiredUIAccess
  @Override
  public AsyncResult<Void> editConfigurable(@Nullable String title, Project project, String dimensionServiceKey, @Nonnull Configurable configurable) {
    return editConfigurable(null, project, configurable, title, dimensionServiceKey, null);
  }

  @Override
  @RequiredUIAccess
  public AsyncResult<Void> editConfigurable(String title, Project project, Configurable configurable, Runnable advancedInitialization) {
    return editConfigurable(null, project, configurable, title, createDimensionKey(configurable), advancedInitialization);
  }

  @RequiredUIAccess
  @Override
  public AsyncResult<Void> editConfigurable(Component parent, Configurable configurable) {
    return editConfigurable(parent, configurable, null);
  }

  @RequiredUIAccess
  @Override
  public AsyncResult<Void> editConfigurable(final Component parent, final Configurable configurable, @Nullable final Runnable advancedInitialization) {
    return editConfigurable(parent, null, configurable, null, createDimensionKey(configurable), advancedInitialization);
  }

  @RequiredUIAccess
  private static AsyncResult<Void> editConfigurable(@Nullable Component parent,
                                                    @Nullable Project project,
                                                    Configurable configurable,
                                                    String title,
                                                    String dimensionKey,
                                                    @Nullable final Runnable advancedInitialization) {
    SingleConfigurableEditor editor;
    if (parent != null) {
      editor = new SingleConfigurableEditor(parent, configurable, title, dimensionKey, true, DialogWrapper.IdeModalityType.IDE);
    }
    else {
      editor = new SingleConfigurableEditor(project, configurable, title, dimensionKey, true, DialogWrapper.IdeModalityType.IDE);
    }
    if (advancedInitialization != null) {
      new UiNotifyConnector.Once(editor.getContentPane(), new Activatable() {
        @Override
        public void showNotify() {
          advancedInitialization.run();
        }
      });
    }
    return editor.showAsync();
  }

  @RequiredUIAccess
  @Override
  public AsyncResult<Void> editConfigurable(Component parent, String dimensionServiceKey, Configurable configurable) {
    return editConfigurable(parent, null, configurable, null, dimensionServiceKey, null);
  }

  @Override
  public boolean isAlreadyShown() {
    return myShown.get();
  }
}
