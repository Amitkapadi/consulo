/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.project.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.notification.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.*;
import com.intellij.openapi.extensions.impl.ExtensionAreaId;
import com.intellij.openapi.components.impl.ProjectPathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.TimedReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import consulo.components.impl.PlatformComponentManagerImpl;
import consulo.components.impl.stores.*;
import consulo.injecting.InjectingContainerBuilder;
import consulo.ui.RequiredUIAccess;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProjectImpl extends PlatformComponentManagerImpl implements ProjectEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.project.impl.ProjectImpl");
  private static final ExtensionPointName<ServiceDescriptor> PROJECT_SERVICES = ExtensionPointName.create("com.intellij.projectService");

  public static final String NAME_FILE = ".name";

  private ProjectManager myManager;

  private MyProjectManagerListener myProjectManagerListener;

  private final AtomicBoolean mySavingInProgress = new AtomicBoolean(false);

  public boolean myOptimiseTestLoadSpeed;
  @NonNls
  public static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";

  private String myName;

  public static Key<Long> CREATION_TIME = Key.create("ProjectImpl.CREATION_TIME");
  public static final Key<String> CREATION_TRACE = Key.create("ProjectImpl.CREATION_TRACE");

  private final List<ProjectComponent> myProjectComponents = new CopyOnWriteArrayList<>();

  protected ProjectImpl(@Nonnull ProjectManager manager, @Nonnull String dirPath, boolean isOptimiseTestLoadSpeed, String projectName, boolean noUIThread) {
    super(ApplicationManager.getApplication(), "Project " + (projectName == null ? dirPath : projectName), ExtensionAreaId.PROJECT);

    putUserData(CREATION_TIME, System.nanoTime());

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      putUserData(CREATION_TRACE, DebugUtil.currentStackTrace());
    }

    if (!isDefault()) {
      if (noUIThread) {
        getStateStore().setProjectFilePathNoUI(dirPath);
      }
      else {
        getStateStore().setProjectFilePath(dirPath);
      }
    }

    myOptimiseTestLoadSpeed = isOptimiseTestLoadSpeed;

    myManager = manager;

    myName = isDefault() ? TEMPLATE_PROJECT_NAME : projectName == null ? getStateStore().getProjectName() : projectName;
  }

  @Nullable
  @Override
  protected ExtensionPointName<ServiceDescriptor> getServiceExtensionPointName() {
    return PROJECT_SERVICES;
  }

  @Nonnull
  @Override
  protected ComponentConfig[] getComponentConfigs(IdeaPluginDescriptor ideaPluginDescriptor) {
    return ideaPluginDescriptor.getProjectComponents();
  }

  @Override
  public void setProjectName(@Nonnull String projectName) {
    if (!projectName.equals(myName)) {
      myName = projectName;
      StartupManager.getInstance(this).runWhenProjectIsInitialized(new DumbAwareRunnable() {
        @Override
        public void run() {
          if (isDisposed()) return;

          JFrame frame = WindowManager.getInstance().getFrame(ProjectImpl.this);
          String title = FrameTitleBuilder.getInstance().getProjectTitle(ProjectImpl.this);
          if (frame != null && title != null) {
            frame.setTitle(title);
          }
        }
      });
    }
  }

  @Override
  protected void bootstrapInjectingContainer(@Nonnull InjectingContainerBuilder builder) {
    super.bootstrapInjectingContainer(builder);

    builder.bind(Project.class).to(this);
    builder.bind(ProjectEx.class).to(this);
    builder.bind(ProjectPathMacroManager.class).to(ProjectPathMacroManager.class).forceSingleton();

    final Class<? extends IProjectStore> storeClass = isDefault() ? DefaultProjectStoreImpl.class : ProjectStoreImpl.class;
    builder.bind(IProjectStore.class).to(storeClass).forceSingleton();
  }

  @Nonnull
  @Override
  public IProjectStore getStateStore() {
    return getComponent(IProjectStore.class);
  }

  @Override
  protected void notifyAboutInitialization(float percentOfLoad, Object component) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText2(getComponentName(component));
    }

    if (component instanceof ProjectComponent) {
      myProjectComponents.add((ProjectComponent)component);
    }
  }

  @Override
  public boolean isOpen() {
    return ProjectManagerEx.getInstanceEx().isProjectOpened(this);
  }

  @Override
  public boolean isInitialized() {
    return isOpen() && !isDisposed() && StartupManagerEx.getInstanceEx(this).startupActivityPassed();
  }

  @Override
  @Nonnull
  public String getProjectFilePath() {
    return getStateStore().getProjectFilePath();
  }

  @Override
  public VirtualFile getProjectFile() {
    return getStateStore().getProjectFile();
  }

  @Override
  public VirtualFile getBaseDir() {
    return getStateStore().getProjectBaseDir();
  }

  @Override
  public String getBasePath() {
    return getStateStore().getProjectBasePath();
  }

  @Nonnull
  @Override
  public String getName() {
    return myName;
  }

  @NonNls
  @Override
  public String getPresentableUrl() {
    if (myName == null) return null;  // not yet initialized
    return getStateStore().getPresentableUrl();
  }

  @Nonnull
  @NonNls
  @Override
  public String getLocationHash() {
    String str = getPresentableUrl();
    if (str == null) str = getName();

    final String prefix = !isDefault() ? "" : getName();
    return prefix + Integer.toHexString(str.hashCode());
  }

  @Override
  @Nullable
  public VirtualFile getWorkspaceFile() {
    return getStateStore().getWorkspaceFile();
  }

  @Override
  public boolean isOptimiseTestLoadSpeed() {
    return myOptimiseTestLoadSpeed;
  }

  @Override
  public void setOptimiseTestLoadSpeed(final boolean optimiseTestLoadSpeed) {
    myOptimiseTestLoadSpeed = optimiseTestLoadSpeed;
  }

  @Override
  public void initNotLazyServices() {
    long start = System.currentTimeMillis();
//    ProfilingUtil.startCPUProfiling();
    super.initNotLazyServices();
//    ProfilingUtil.captureCPUSnapshot();
    long time = System.currentTimeMillis() - start;
    LOG.info(getNotLazyServicesCount() + " project components initialized in " + time + " ms");
    getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).projectComponentsInitialized(this);

    myProjectManagerListener = new MyProjectManagerListener();
    myManager.addProjectManagerListener(this, myProjectManagerListener);
  }

  @Override
  public void save() {
    if (ApplicationManagerEx.getApplicationEx().isDoNotSave()) {
      // no need to save
      return;
    }

    if (!mySavingInProgress.compareAndSet(false, true)) {
      return;
    }

    HeavyProcessLatch.INSTANCE.prioritizeUiActivity();

    try {
      if (!isDefault()) {
        String projectBasePath = getStateStore().getProjectBasePath();
        if (projectBasePath != null) {
          File projectDir = new File(projectBasePath);
          File nameFile = new File(projectDir, DIRECTORY_STORE_FOLDER + "/" + NAME_FILE);
          if (!projectDir.getName().equals(getName())) {
            try {
              FileUtil.writeToFile(nameFile, getName());
            }
            catch (IOException e) {
              LOG.error("Unable to store project name", e);
            }
          }
          else {
            FileUtil.delete(nameFile);
          }
        }
      }

      StoreUtil.save(getStateStore(), this);
    }
    finally {
      mySavingInProgress.set(false);
      ApplicationManager.getApplication().getMessageBus().syncPublisher(ProjectSaved.TOPIC).saved(this);
    }
  }

  @RequiredUIAccess
  @Override
  public void dispose() {
    Application application = Application.get();
    assert application.isWriteAccessAllowed();  // dispose must be under write action

    // can call dispose only via com.intellij.ide.impl.ProjectUtil.closeAndDispose()
    LOG.assertTrue(application.isUnitTestMode() || !ProjectManagerEx.getInstanceEx().isProjectOpened(this));

    LOG.assertTrue(!isDisposed());
    if (myProjectManagerListener != null) {
      myManager.removeProjectManagerListener(this, myProjectManagerListener);
    }

    myManager = null;
    myName = null;
    myProjectManagerListener = null;

    if (!application.isDisposed()) {
      application.getMessageBus().syncPublisher(ProjectLifecycleListener.TOPIC).afterProjectClosed(this);
    }

    super.dispose();

    TimedReference.disposeTimed();
  }

  private void projectOpened() {
    for (ProjectComponent component : myProjectComponents) {
      try {
        component.projectOpened();
      }
      catch (Throwable e) {
        LOG.error(component.toString(), e);
      }
    }
  }

  private void projectClosed() {
    List<ProjectComponent> components = new ArrayList<>(myProjectComponents);
    Collections.reverse(components);
    for (ProjectComponent component : components) {
      try {
        component.projectClosed();
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  private class MyProjectManagerListener extends ProjectManagerAdapter {
    @Override
    public void projectOpened(Project project) {
      LOG.assertTrue(project == ProjectImpl.this);
      ProjectImpl.this.projectOpened();
    }

    @Override
    public void projectClosed(Project project) {
      LOG.assertTrue(project == ProjectImpl.this);
      ProjectImpl.this.projectClosed();
    }
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @Override
  public void checkUnknownMacros(final boolean showDialog) {
    final IProjectStore stateStore = getStateStore();

    final TrackingPathMacroSubstitutor[] substitutors = stateStore.getSubstitutors();
    final Set<String> unknownMacros = new HashSet<>();
    for (final TrackingPathMacroSubstitutor substitutor : substitutors) {
      unknownMacros.addAll(substitutor.getUnknownMacros(null));
    }

    if (!unknownMacros.isEmpty()) {
      if (!showDialog || ProjectMacrosUtil.checkMacros(this, new HashSet<>(unknownMacros))) {
        final PathMacros pathMacros = PathMacros.getInstance();
        final Set<String> macros2invalidate = new HashSet<>(unknownMacros);
        for (Iterator it = macros2invalidate.iterator(); it.hasNext(); ) {
          final String macro = (String)it.next();
          final String value = pathMacros.getValue(macro);
          if ((value == null || value.trim().isEmpty()) && !pathMacros.isIgnoredMacroName(macro)) {
            it.remove();
          }
        }

        if (!macros2invalidate.isEmpty()) {
          final Set<String> components = new HashSet<>();
          for (TrackingPathMacroSubstitutor substitutor : substitutors) {
            components.addAll(substitutor.getComponents(macros2invalidate));
          }

          for (final TrackingPathMacroSubstitutor substitutor : substitutors) {
            substitutor.invalidateUnknownMacros(macros2invalidate);
          }

          final UnknownMacroNotification[] notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(UnknownMacroNotification.class, this);
          for (final UnknownMacroNotification notification : notifications) {
            if (macros2invalidate.containsAll(notification.getMacros())) notification.expire();
          }

          ApplicationManager.getApplication().runWriteAction(() -> stateStore.reinitComponents(components, true));
        }
      }
    }
  }

  @NonNls
  @Override
  public String toString() {
    return "Project" +
           (isDisposed() ? " (Disposed" + (temporarilyDisposed ? " temporarily" : "") + ")" : isDefault() ? "" : " '" + getPresentableUrl() + "'") +
           (isDefault() ? " (Default)" : "") +
           " " +
           myName;
  }

  public static void dropUnableToSaveProjectNotification(@Nonnull final Project project, Collection<File> readOnlyFiles) {
    final UnableToSaveProjectNotification[] notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(UnableToSaveProjectNotification.class, project);
    if (notifications.length == 0) {
      Notifications.Bus.notify(new UnableToSaveProjectNotification(project, readOnlyFiles), project);
    }
  }

  public static class UnableToSaveProjectNotification extends Notification {
    private Project myProject;
    private final List<String> myFileNames;

    private UnableToSaveProjectNotification(@Nonnull final Project project, final Collection<File> readOnlyFiles) {
      super("Project Settings", "Could not save project!", buildMessage(), NotificationType.ERROR, new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@Nonnull Notification notification, @Nonnull HyperlinkEvent event) {
          final UnableToSaveProjectNotification unableToSaveProjectNotification = (UnableToSaveProjectNotification)notification;
          final Project _project = unableToSaveProjectNotification.getProject();
          notification.expire();

          if (_project != null && !_project.isDisposed()) {
            _project.save();
          }
        }
      });

      myProject = project;
      myFileNames = ContainerUtil.map(readOnlyFiles, File::getPath);
    }

    public List<String> getFileNames() {
      return myFileNames;
    }

    private static String buildMessage() {
      final StringBuilder sb = new StringBuilder("<p>Unable to save project files. Please ensure project files are writable and you have permissions to modify them.");
      return sb.append(" <a href=\"\">Try to save project again</a>.</p>").toString();
    }

    public Project getProject() {
      return myProject;
    }

    @Override
    public void expire() {
      myProject = null;
      super.expire();
    }
  }
}
