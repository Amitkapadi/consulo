/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.idea.starter;

import com.intellij.ide.*;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerUISettings;
import com.intellij.idea.ApplicationStarter;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.impl.DesktopApplicationImpl;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.DesktopWindowManagerImpl;
import com.intellij.openapi.wm.impl.SystemDock;
import consulo.application.ApplicationProperties;
import consulo.awt.TargetAWT;
import consulo.container.impl.classloader.PluginLoadStatistics;
import consulo.container.plugin.PluginManager;
import consulo.container.util.StatCollector;
import consulo.desktop.start.splash.DesktopSplash;
import consulo.desktop.startup.customize.FirstStartCustomizeUtil;
import consulo.logging.Logger;
import consulo.plugins.internal.PluginsInitializeInfo;
import consulo.start.CommandLineArgs;
import consulo.start.WelcomeFrameManager;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.List;
import java.util.Set;

public class DesktopApplicationPostStarter extends ApplicationPostStarter {
  private static final Logger LOG = Logger.getInstance(DesktopApplicationPostStarter.class);

  public DesktopApplicationPostStarter(ApplicationStarter applicationStarter) {
    super(applicationStarter);
  }

  @Nullable
  @Override
  public StartupProgress createSplash(CommandLineArgs args) {
    if (!args.isNoSplash()) {
      final SplashScreen splashScreen = getSplashScreen();
      if (splashScreen == null) {
        DesktopSplash splash = new DesktopSplash(false);
        splash.show();
        return splash;
      }
    }
    return null;
  }

  @Nonnull
  @Override
  protected Application createApplication(boolean isHeadlessMode, Ref<StartupProgress> splashRef, CommandLineArgs args) {
    return new DesktopApplicationImpl(isHeadlessMode, splashRef);
  }

  @Nullable
  private SplashScreen getSplashScreen() {
    try {
      return SplashScreen.getSplashScreen();
    }
    catch (Throwable t) {
      LOG.warn(t);
      return null;
    }
  }

  @Override
  @RequiredUIAccess
  public void main(StatCollector stat, Runnable appInitializeMark, ApplicationEx app, boolean newConfigFolder, @Nonnull CommandLineArgs args) {
    SystemDock.getInstance().updateMenu();

    // if OS has dock, RecentProjectsManager will be already created, but not all OS have dock, so, we trigger creation here to ensure that RecentProjectsManager app listener will be added
    RecentProjectsManager.getInstance();

    // Event queue should not be changed during initialization of application components.
    // It also cannot be changed before initialization of application components because IdeEventQueue uses other
    // application components. So it is proper to perform replacement only here.
    DesktopWindowManagerImpl windowManager = (DesktopWindowManagerImpl)WindowManager.getInstance();
    IdeEventQueue.getInstance().setWindowManager(windowManager);

    RecentProjectsManagerBase recentProjectsManager = RecentProjectsManagerBase.getInstanceEx();

    appInitializeMark.run();

    stat.dump("Startup statistics", LOG::info);

    PluginLoadStatistics.get().dumpPluginClassStatistics(LOG::info);

    app.invokeAndWait(() -> {
      StartupProgress desktopSplash = mySplashRef.get();
      if (desktopSplash != null) {
        desktopSplash.dispose();
        mySplashRef.set(null);  // Allow GC collect the splash window
      }
    }, ModalityState.NON_MODAL);

    if(Boolean.getBoolean("consulo.first.start.testing") || newConfigFolder && !ApplicationProperties.isInSandbox()) {
      FirstStartCustomizeUtil.show(true);
    }

    boolean willOpenProject = recentProjectsManager.willReopenProjectOnStart() && !args.isNoRecentProjects();

    AppLifecycleListener lifecyclePublisher = app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC);
    lifecyclePublisher.appFrameCreated(args, willOpenProject);

    if (recentProjectsManager.willReopenProjectOnStart() && !args.isNoRecentProjects()) {
      windowManager.showFrame();
    }
    else {
      WelcomeFrameManager.getInstance().showFrame();
    }

    app.invokeLater(() -> {
      if (!args.isNoRecentProjects()) {
        AsyncResult<Project> projectFromCommandLine = AsyncResult.rejected();

        if (myApplicationStarter.isPerformProjectLoad()) {
          projectFromCommandLine = CommandLineProcessor.processExternalCommandLine(args, null);
        }

        projectFromCommandLine.doWhenRejected(recentProjectsManager::doReopenLastProject);
      }

      SwingUtilities.invokeLater(() -> reportPluginError(myPluginsInitializeInfo));

      UsageTrigger.trigger("consulo.app.started");
    }, ModalityState.NON_MODAL);
  }

  static void reportPluginError(PluginsInitializeInfo info) {
    List<String> pluginErrors = info.getPluginErrors();

    List<String> plugins2Disable = info.getPlugins2Disable();
    Set<String> plugins2Enable = info.getPlugins2Enable();

    if (pluginErrors != null) {
      for (String pluginError : pluginErrors) {
        String message = IdeBundle.message("title.plugin.notification.title");
        Notifications.Bus.notify(new Notification(message, message, pluginError, NotificationType.ERROR, new NotificationListener() {
          @RequiredUIAccess
          @Override
          public void hyperlinkUpdate(@Nonnull Notification notification, @Nonnull HyperlinkEvent event) {
            notification.expire();

            String description = event.getDescription();
            if (PluginsInitializeInfo.EDIT.equals(description)) {
              PluginManagerConfigurable configurable = new PluginManagerConfigurable(PluginManagerUISettings.getInstance());
              IdeFrame ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null);
              ShowSettingsUtil.getInstance().editConfigurable(ideFrame == null ? null : TargetAWT.to(ideFrame.getWindow()), configurable);
              return;
            }

            List<String> disabledPlugins = consulo.container.plugin.PluginManager.getDisabledPlugins();
            if (plugins2Disable != null && PluginsInitializeInfo.DISABLE.equals(description)) {
              for (String pluginId : plugins2Disable) {
                if (!disabledPlugins.contains(pluginId)) {
                  disabledPlugins.add(pluginId);
                }
              }
            }
            else if (plugins2Enable != null && PluginsInitializeInfo.ENABLE.equals(description)) {
              disabledPlugins.removeAll(plugins2Enable);
            }

            PluginManager.replaceDisabledPlugins(disabledPlugins);
          }
        }));
      }
    }
  }

}
