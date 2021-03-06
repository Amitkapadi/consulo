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
package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 19, 2005
 */
public abstract class RuleAction extends ToggleAction implements DumbAware {
  private final UsageViewImpl myView;
  private boolean myState;

  public RuleAction(@Nonnull UsageView view, @Nonnull String text, @Nonnull Image icon) {
    super(text, null, icon);
    myView = (UsageViewImpl)view;
    myState = getOptionValue();
  }

  protected abstract boolean getOptionValue();

  protected abstract void setOptionValue(boolean value);

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myState;
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    setOptionValue(state);
    myState = state;

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      project.getMessageBus().syncPublisher(UsageFilteringRuleProvider.RULES_CHANGED).run();
    }

    myView.select();
  }
}
