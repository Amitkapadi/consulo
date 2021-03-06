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
package com.intellij.internal.statistic.tmp;

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.updater.StatisticsSendManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import consulo.ui.Alerts;
import consulo.ui.annotation.RequiredUIAccess;

import javax.inject.Inject;
import javax.inject.Provider;

public class SendStatisticsAction extends DumbAwareAction {
  private final Provider<UsageStatisticsPersistenceComponent> myUsageStatisticsPersistenceComponent;
  private final Provider<StatisticsSendManager> myStatisticsSendManager;

  @Inject
  public SendStatisticsAction(Provider<UsageStatisticsPersistenceComponent> usageStatisticsPersistenceComponent, Provider<StatisticsSendManager> statisticsSendManager) {
    myUsageStatisticsPersistenceComponent = usageStatisticsPersistenceComponent;
    myStatisticsSendManager = statisticsSendManager;
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    myStatisticsSendManager.get().sendNow(myUsageStatisticsPersistenceComponent.get());

    Alerts.okInfo("Sended");
  }
}
