/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.impl.content.DesktopToolWindowContentUi;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.UIBundle;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import consulo.annotations.RequiredDispatchThread;
import consulo.ui.SwingUIDecorator;
import consulo.ui.image.Image;
import consulo.ui.laf.MorphColor;
import consulo.wm.impl.ToolWindowManagerBase;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author pegov
 */
public abstract class DesktopToolWindowHeader extends JPanel implements Disposable {
  private class GearAction extends DumbAwareAction {
    private NotNullProducer<ActionGroup> myGearProducer;

    public GearAction(NotNullProducer<ActionGroup> gearProducer) {
      super("Show options", null, AllIcons.General.GearPlain);
      myGearProducer = gearProducer;
    }

    @RequiredDispatchThread
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
      final InputEvent inputEvent = e.getInputEvent();
      final ActionPopupMenu popupMenu =
              ((ActionManagerImpl)ActionManager.getInstance()).createActionPopupMenu(DesktopToolWindowContentUi.POPUP_PLACE, myGearProducer.produce(), new MenuItemPresentationFactory(true));

      int x = 0;
      int y = 0;
      if (inputEvent instanceof MouseEvent) {
        x = ((MouseEvent)inputEvent).getX();
        y = ((MouseEvent)inputEvent).getY();
      }

      popupMenu.getComponent().show(inputEvent.getComponent(), x, y);
    }
  }

  private class HideAction extends DumbAwareAction {

    @RequiredDispatchThread
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
      hideToolWindow();
    }

    @RequiredDispatchThread
    @Override
    public final void update(@Nonnull final AnActionEvent event) {
      Presentation presentation = event.getPresentation();

      presentation.setText(UIBundle.message("tool.window.hide.action.name"));
      presentation.setIcon(getHideIcon(myToolWindow));
    }

    private Image getHideIcon(ToolWindow toolWindow) {
      ToolWindowAnchor anchor = toolWindow.getAnchor();
      if (anchor == ToolWindowAnchor.BOTTOM) {
        return AllIcons.General.HideDownPart;
      }
      else if (anchor == ToolWindowAnchor.RIGHT) {
        return AllIcons.General.HideRightPart;
      }

      return AllIcons.General.HideLeftPart;
    }
  }

  private final ToolWindow myToolWindow;

  private final DefaultActionGroup myAdditionalActionGroup = new DefaultActionGroup();

  private ActionToolbar myToolbar;

  public DesktopToolWindowHeader(final DesktopToolWindowImpl toolWindow, @Nonnull final NotNullProducer<ActionGroup> gearProducer) {
    super(new BorderLayout());

    myToolWindow = toolWindow;

    JPanel westPanel = new JPanel() {
      @Override
      public void doLayout() {
        if (getComponentCount() > 0) {
          Rectangle r = getBounds();
          Insets insets = getInsets();

          Component c = getComponent(0);
          Dimension size = c.getPreferredSize();
          if (size.width < (r.width - insets.left - insets.right)) {
            c.setBounds(insets.left, insets.top, size.width, r.height - insets.top - insets.bottom);
          } else {
            c.setBounds(insets.left, insets.top, r.width - insets.left - insets.right, r.height - insets.top - insets.bottom);
          }
        }
      }
    };

    westPanel.setOpaque(false);
    add(westPanel, BorderLayout.CENTER);

    westPanel.add(toolWindow.getContentUI().getTabComponent());

    DesktopToolWindowContentUi.initMouseListeners(westPanel, toolWindow.getContentUI(), true);

    myToolbar = ActionManager.getInstance().createActionToolbar("ToolwindowHeader", new DefaultActionGroup(myAdditionalActionGroup, new GearAction(gearProducer), new HideAction()), true);
    myToolbar.setTargetComponent(this);
    myToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    myToolbar.setReservePlaceAutoPopupIcon(false);

    JComponent component = myToolbar.getComponent();
    component.setBorder(JBUI.Borders.empty());
    component.setOpaque(false);

    add(wrapAndFillVertical(component), BorderLayout.EAST);

    westPanel.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        toolWindow.getContentUI().showContextMenu(comp, x, y, toolWindow.getPopupGroup(), toolWindow.getContentManager().getSelectedContent());
      }
    });

    westPanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        toolWindow.fireActivated();
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(final MouseEvent e) {
        if (!e.isPopupTrigger()) {
          if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
            if (e.isAltDown()) {
              toolWindow.fireHidden();
            }
            else {
              toolWindow.fireHiddenSide();
            }
          }
          else {
            toolWindow.fireActivated();
          }
        }
      }
    });

    setBackground(MorphColor.ofWithoutCache(() -> myToolWindow.isActive() ? SwingUIDecorator.get(SwingUIDecorator::getSidebarColor) : UIUtil.getPanelBackground()));

    setBorder(JBUI.Borders.customLine(UIUtil.getBorderColor(), TabsUtil.TABS_BORDER, 0, TabsUtil.TABS_BORDER, 0));

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        ToolWindowManagerBase mgr = toolWindow.getToolWindowManager();
        mgr.setMaximized(myToolWindow, !mgr.isMaximized(myToolWindow));
        return true;
      }
    }.installOn(westPanel);
  }

  @Nonnull
  private JPanel wrapAndFillVertical(JComponent owner) {
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.MIDDLE, false, true));
    panel.add(owner);
    panel.setOpaque(false);
    return panel;
  }

  @Override
  public void dispose() {
    removeAll();
  }

  public void setAdditionalTitleActions(AnAction[] actions) {
    myAdditionalActionGroup.removeAll();
    myAdditionalActionGroup.addAll(actions);
    if (actions.length > 0) {
      myAdditionalActionGroup.addSeparator();
    }

    if (myToolbar != null) {
      myToolbar.updateActionsImmediately();
    }
  }

  protected boolean isActive() {
    return myToolWindow.isActive();
  }

  protected abstract void hideToolWindow();

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    return new Dimension(size.width, TabsUtil.getTabsHeight() + JBUI.scale(TabsUtil.TAB_VERTICAL_PADDING) * 2);
  }

  @Override
  public Dimension getMinimumSize() {
    Dimension size = super.getMinimumSize();
    return new Dimension(size.width, TabsUtil.getTabsHeight() + JBUI.scale(TabsUtil.TAB_VERTICAL_PADDING) * 2);
  }
}
