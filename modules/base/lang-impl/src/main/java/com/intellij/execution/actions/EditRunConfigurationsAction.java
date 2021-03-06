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

package com.intellij.execution.actions;

import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.ExecutionLocalize;
import consulo.ui.annotation.RequiredUIAccess;

public class EditRunConfigurationsAction extends AnAction {
  public EditRunConfigurationsAction() {
    getTemplatePresentation().setIcon(AllIcons.Actions.EditSource);
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null && project.isDisposed()) {
      return;
    }
    if (project == null) {
      //setup template project configurations
      project = ProjectManager.getInstance().getDefaultProject();
    }
    new EditConfigurationsDialog(project).showAsync();
  }

  @RequiredUIAccess
  @Override
  public void update(final AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(true);

    if (ActionPlaces.RUN_CONFIGURATIONS_COMBOBOX.equals(e.getPlace())) {
      LocalizeValue textValue = ExecutionLocalize.editConfigurationAction();

      presentation.setTextValue(textValue);
      presentation.setDescriptionValue(textValue.map(Presentation.NO_MNEMONIC));
    }
  }
}
