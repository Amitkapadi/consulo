/*
 * Copyright 2013-2017 consulo.io
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
package consulo.web.wm.impl;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.CommandProcessorBase;
import com.intellij.openapi.wm.impl.InternalDecoratorListener;
import com.intellij.openapi.wm.impl.WindowInfoImpl;
import com.intellij.openapi.wm.impl.commands.FinalizableCommand;
import com.intellij.util.messages.MessageBusConnection;
import consulo.annotation.access.RequiredWriteAction;
import consulo.localize.LocalizeValue;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.ToolWindowInternalDecorator;
import consulo.ui.ex.ToolWindowStripeButton;
import consulo.ui.layout.DockLayout;
import consulo.ui.web.internal.WebRootPaneImpl;
import consulo.web.ui.ex.WebToolWindowPanelImpl;
import consulo.web.ui.ex.WebToolWindowStripeButtonImpl;
import consulo.wm.impl.ToolWindowManagerBase;
import consulo.wm.impl.UnifiedToolWindowImpl;
import org.jdom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;

/**
 * @author VISTALL
 * @since 24-Sep-17
 */
@Singleton
@State(name = ToolWindowManagerBase.ID, storages = @Storage(value = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED))
public class WebToolWindowManagerImpl extends ToolWindowManagerBase {
  private IdeFrameEx myFrame;

  @Inject
  public WebToolWindowManagerImpl(Project project, Provider<WindowManager> windowManager) {
    super(project, windowManager);

    if (project.isDefault()) {
      return;
    }

    MessageBusConnection busConnection = project.getMessageBus().connect();
    busConnection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project, UIAccess uiAccess) {
        if (project == myProject) {
          uiAccess.giveAndWaitIfNeed(WebToolWindowManagerImpl.this::projectOpened);
        }
      }

      @Override
      public void projectClosed(Project project, UIAccess uiAccess) {
        if (project == myProject) {
          WebToolWindowManagerImpl.this.projectClosed();
        }
      }
    });
  }

  @RequiredUIAccess
  private void projectOpened() {
    WindowManagerEx windowManager = (WindowManagerEx)myWindowManager.get();

    myFrame = windowManager.allocateFrame(myProject);

    WebToolWindowPanelImpl toolWindowPanel = new WebToolWindowPanelImpl();

    myToolWindowPanel = toolWindowPanel;

    WebRootPaneImpl rootPanel = ((WebIdeFrameImpl)myFrame).getRootPanel();

    rootPanel.setCenterComponent(toolWindowPanel);

    ((WebIdeFrameImpl)myFrame).show();
  }

  private void projectClosed() {
    WindowManagerEx windowManager = (WindowManagerEx)myWindowManager.get();

    windowManager.releaseFrame(myFrame);

    myFrame = null;
  }

  @Override
  protected void initAll(List<FinalizableCommand> commandsList) {
    appendUpdateToolWindowsPaneCmd(commandsList);

    Component editorComponent = getEditorComponent(myProject);
    //myEditorComponentFocusWatcher.install(editorComponent);

    appendSetEditorComponentCmd(editorComponent, commandsList);
    //if (myEditorWasActive && editorComponent instanceof DesktopEditorsSplitters) {
    //  activateEditorComponentImpl(commandsList, true);
    //}
  }

  private Component getEditorComponent(Project project) {
    return FileEditorManagerEx.getInstanceEx(project).getUIComponent();
  }

  @Override
  @Nonnull
  @RequiredUIAccess
  protected Component createInitializingLabel() {
    Label label = Label.create("Initializing...");
    DockLayout dock = DockLayout.create();
    dock.center(label);
    return label;
  }

  @RequiredUIAccess
  @Override
  protected void doWhenFirstShown(Object component, Runnable runnable) {
    UIAccess.get().give(runnable);
  }

  @Nonnull
  @Override
  protected CommandProcessorBase createCommandProcessor() {
    return new WebCommandProcessorImpl();
  }

  @Nonnull
  @Override
  protected InternalDecoratorListener createInternalDecoratorListener() {
    return new MyInternalDecoratorListenerBase() {
      @Override
      public void resized(@Nonnull ToolWindowInternalDecorator source) {

      }
    };
  }

  @Nonnull
  @Override
  protected ToolWindowStripeButton createStripeButton(ToolWindowInternalDecorator internalDecorator) {
    return new WebToolWindowStripeButtonImpl((WebToolWindowInternalDecorator)internalDecorator, (WebToolWindowPanelImpl)myToolWindowPanel);
  }

  @Nonnull
  @Override
  protected ToolWindowEx createToolWindow(String id, LocalizeValue displayName, boolean canCloseContent, @Nullable Object component, boolean shouldBeAvailable) {
    return new UnifiedToolWindowImpl(this, id, displayName, canCloseContent, component, shouldBeAvailable);
  }

  @Nonnull
  @Override
  protected ToolWindowInternalDecorator createInternalDecorator(Project project, @Nonnull WindowInfoImpl info, ToolWindowEx toolWindow, boolean dumbAware) {
    return new WebToolWindowInternalDecorator(project, info, (UnifiedToolWindowImpl)toolWindow, dumbAware);
  }

  @Override
  public boolean isUnified() {
    return true;
  }

  @Override
  protected void appendRequestFocusInToolWindowCmd(String id, List<FinalizableCommand> commandList, boolean forced) {

  }

  @Override
  protected void appendRemoveWindowedDecoratorCmd(WindowInfoImpl info, List<FinalizableCommand> commandsList) {

  }

  @Override
  protected void appendAddFloatingDecorator(ToolWindowInternalDecorator internalDecorator, List<FinalizableCommand> commandList, WindowInfoImpl toBeShownInfo) {

  }

  @Override
  protected void appendAddWindowedDecorator(ToolWindowInternalDecorator internalDecorator, List<FinalizableCommand> commandList, WindowInfoImpl toBeShownInfo) {

  }

  @Override
  protected void appendUpdateToolWindowsPaneCmd(List<FinalizableCommand> commandsList) {

  }

  @Override
  protected boolean hasModalChild(WindowInfoImpl info) {
    return false;
  }

  @RequiredUIAccess
  @Nullable
  @Override
  public Element getStateFromUI() {
    return new Element("state");
  }

  @RequiredWriteAction
  @Nullable
  @Override
  public Element getState(Element element) {
    return element;
  }

  @Override
  public boolean canShowNotification(@Nonnull String toolWindowId) {
    return false;
  }

  @Override
  public void activateEditorComponent() {

  }

  @Override
  public boolean isEditorComponentActive() {
    return false;
  }

  @Override
  public void notifyByBalloon(@Nonnull String toolWindowId, @Nonnull MessageType type, @Nonnull String htmlBody) {

  }

  @Nullable
  @Override
  public Balloon getToolWindowBalloon(String id) {
    return null;
  }

  @Override
  public boolean isMaximized(@Nonnull ToolWindow wnd) {
    return false;
  }

  @Override
  public void setMaximized(@Nonnull ToolWindow wnd, boolean maximized) {

  }
}
