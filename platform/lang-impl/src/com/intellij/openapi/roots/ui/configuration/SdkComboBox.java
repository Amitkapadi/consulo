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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.projectWizard.ProjectSdkListRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectSdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkListConfigurable;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * @since May 18, 2005
 */
public class SdkComboBox extends ComboBoxWithWidePopup {

  private static final Icon EMPTY_ICON = EmptyIcon.create(1, 16);

  @Nullable
  private final Condition<SdkTypeId> myFilter;
  @Nullable
  private final Condition<SdkTypeId> myCreationFilter;

  public SdkComboBox(@NotNull final ProjectSdksModel jdkModel) {
    this(jdkModel, null, false);
  }

  public SdkComboBox(@NotNull final ProjectSdksModel jdkModel, @Nullable Condition<SdkTypeId> filter, boolean withNoneItem) {
    this(jdkModel, filter, filter, withNoneItem);
  }

  public SdkComboBox(@NotNull final ProjectSdksModel jdkModel,
                     @Nullable Condition<SdkTypeId> filter,
                     @Nullable Condition<SdkTypeId> creationFilter,
                     boolean withNoneItem) {
    super(new SdkComboBoxModel(jdkModel, getSdkFilter(filter), withNoneItem));
    myFilter = filter;
    myCreationFilter = creationFilter;
    setRenderer(new ProjectSdkListRenderer() {
      @Override
      public void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (SdkComboBox.this.isEnabled()) {
          setIcon(EMPTY_ICON);    // to fix vertical size
          if (value instanceof InvalidSdkComboBoxItem) {
            final String str = value.toString();
            append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
          }
          else if (value instanceof ProjectSdkComboBoxItem) {
            final Sdk jdk = jdkModel.getProjectSdk();
            if (jdk != null) {
              setIcon(((SdkType)jdk.getSdkType()).getIcon());
              append(ProjectBundle.message("project.roots.project.jdk.inherited"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              append(" (" + jdk.getName() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            else {
              final String str = value.toString();
              append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
          }
          else if (value instanceof NoneSdkComboBoxItem) {
            setIcon(AllIcons.Actions.Help);
            append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else {
            super
              .doCustomize(list, value != null ? ((SdkComboBoxItem)value).getSdk() : new NoneSdkComboBoxItem(), index, selected, hasFocus);
          }
        }
      }
    });
  }

  @Override
  public Dimension getPreferredSize() {
    final Rectangle rec = ScreenUtil.getScreenRectangle(0, 0);
    final Dimension size = super.getPreferredSize();
    final int maxWidth = rec.width / 4;
    if (size.width > maxWidth) {
      size.width = maxWidth;
    }
    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    final Dimension minSize = super.getMinimumSize();
    final Dimension prefSize = getPreferredSize();
    if (minSize.width > prefSize.width) {
      minSize.width = prefSize.width;
    }
    return minSize;
  }

  public void setSetupButton(final JButton setUpButton,
                             @Nullable final Project project,
                             final ProjectSdksModel jdksModel,
                             final SdkComboBoxItem firstItem,
                             @Nullable final Condition<Sdk> additionalSetup,
                             final boolean moduleJdkSetup) {
    setSetupButton(setUpButton, project, jdksModel, firstItem, additionalSetup,
                   ProjectBundle.message("project.roots.set.up.jdk.title", moduleJdkSetup ? 1 : 2));
  }

  public void setSetupButton(final JButton setUpButton,
                             @Nullable final Project project,
                             final ProjectSdksModel jdksModel,
                             final SdkComboBoxItem firstItem,
                             @Nullable final Condition<Sdk> additionalSetup,
                             final String actionGroupTitle) {
    setUpButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DefaultActionGroup group = new DefaultActionGroup();
        jdksModel.createAddActions(group, SdkComboBox.this, new Consumer<Sdk>() {
          @Override
          public void consume(final Sdk jdk) {
            if (project != null) {
              final SdkListConfigurable configurable = SdkListConfigurable.getInstance(project);
              configurable.addSdkNode(jdk, false);
            }
            reloadModel(new SdkComboBoxItem(jdk), project);
            setSelectedSdk(jdk); //restore selection
            if (additionalSetup != null) {
              if (additionalSetup.value(jdk)) { //leave old selection
                setSelectedSdk(firstItem.getSdk());
              }
            }
          }
        }, myCreationFilter);
        final DataContext dataContext = DataManager.getInstance().getDataContext(SdkComboBox.this);
        if (group.getChildrenCount() > 1) {
          JBPopupFactory.getInstance()
            .createActionGroupPopup(actionGroupTitle, group, dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
            .showUnderneathOf(setUpButton);
        }
        else {
          final AnActionEvent event =
            new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, new Presentation(""), ActionManager.getInstance(), 0);
          group.getChildren(event)[0].actionPerformed(event);
        }
      }
    });
  }

  public void setEditButton(final JButton editButton, final Project project, final Computable<Sdk> retrieveJDK) {
    editButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final Sdk projectJdk = retrieveJDK.compute();
        if (projectJdk != null) {
          ProjectStructureConfigurable.getInstance(project).select(projectJdk, true);
        }
      }
    });
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final SdkComboBoxItem selectedItem = getSelectedItem();
        if (selectedItem instanceof ProjectSdkComboBoxItem) {
          editButton.setEnabled(ProjectStructureConfigurable.getInstance(project).getProjectSdksModel().getProjectSdk() != null);
        }
        else {
          editButton.setEnabled(!(selectedItem instanceof InvalidSdkComboBoxItem) && selectedItem != null && selectedItem.getSdk() != null);
        }
      }
    });
  }

  @Override
  public SdkComboBoxItem getSelectedItem() {
    return (SdkComboBoxItem)super.getSelectedItem();
  }

  @Nullable
  public Sdk getSelectedSdk() {
    final SdkComboBoxItem selectedItem = (SdkComboBoxItem)super.getSelectedItem();
    return selectedItem != null ? selectedItem.getSdk() : null;
  }

  public void setSelectedSdk(Sdk jdk) {
    final int index = indexOf(jdk);
    if (index >= 0) {
      setSelectedIndex(index);
    }
  }

  public void setInvalidSdk(String name) {
    removeInvalidElement();
    addItem(new InvalidSdkComboBoxItem(name));
    setSelectedIndex(getModel().getSize() - 1);
  }

  public void setSelectedSdk(@Nullable String name) {
    if (name != null) {
      ProjectSdkTable projectSdkTable = ProjectSdkTable.getInstance();
      final Sdk sdk = projectSdkTable.findSdk(name);
      if (sdk != null) {
        setSelectedSdk(sdk);
      }
      else {
        setInvalidSdk(name);
      }
    }
    else {
      if (getItemCount() > 0 && getItemAt(0) instanceof NoneSdkComboBoxItem) {
        setSelectedIndex(0);
      }
      else {
        setInvalidSdk("null");
      }
    }
  }

  private int indexOf(Sdk jdk) {
    final SdkComboBoxModel model = (SdkComboBoxModel)getModel();
    final int count = model.getSize();
    for (int idx = 0; idx < count; idx++) {
      final SdkComboBoxItem elementAt = model.getElementAt(idx);
      if (jdk == null) {
        if (elementAt instanceof NoneSdkComboBoxItem || elementAt instanceof ProjectSdkComboBoxItem) {
          return idx;
        }
      }
      else {
        Sdk elementAtJdk = elementAt.getSdk();
        if (elementAtJdk != null && jdk.getName().equals(elementAtJdk.getName())) {
          return idx;
        }
      }
    }
    return -1;
  }

  private void removeInvalidElement() {
    final SdkComboBoxModel model = (SdkComboBoxModel)getModel();
    final int count = model.getSize();
    for (int idx = 0; idx < count; idx++) {
      final SdkComboBoxItem elementAt = model.getElementAt(idx);
      if (elementAt instanceof InvalidSdkComboBoxItem) {
        removeItemAt(idx);
        break;
      }
    }
  }

  public void reloadModel(SdkComboBoxItem firstItem, @Nullable Project project) {
    final DefaultComboBoxModel model = ((DefaultComboBoxModel)getModel());
    if (project == null) {
      model.addElement(firstItem);
      return;
    }
    model.removeAllElements();
    model.addElement(firstItem);
    final ProjectSdksModel projectJdksModel = ProjectStructureConfigurable.getInstance(project).getProjectSdksModel();
    List<Sdk> projectJdks = new ArrayList<Sdk>(projectJdksModel.getProjectSdks().values());
    if (myFilter != null) {
      projectJdks = ContainerUtil.filter(projectJdks, getSdkFilter(myFilter));
    }
    Collections.sort(projectJdks, new Comparator<Sdk>() {
      @Override
      public int compare(final Sdk o1, final Sdk o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    for (Sdk projectJdk : projectJdks) {
      model.addElement(new SdkComboBoxItem(projectJdk));
    }
  }

  private static class SdkComboBoxModel extends DefaultComboBoxModel {
    public SdkComboBoxModel(final ProjectSdksModel sdksModel, Condition<Sdk> sdkFilter, boolean withNoneItem) {
      Sdk[] sdks = sdksModel.getSdks();
      if (sdkFilter != null) {
        final List<Sdk> filtered = ContainerUtil.filter(sdks, sdkFilter);
        sdks = filtered.toArray(new Sdk[filtered.size()]);
      }
      Arrays.sort(sdks, new Comparator<Sdk>() {
        @Override
        public int compare(final Sdk s1, final Sdk s2) {
          return s1.getName().compareToIgnoreCase(s2.getName());
        }
      });

      if (withNoneItem) {
        addElement(new NoneSdkComboBoxItem());
      }

      for (Sdk jdk : sdks) {
        addElement(new SdkComboBoxItem(jdk));
      }
    }

    // implements javax.swing.ListModel
    @Override
    public SdkComboBoxItem getElementAt(int index) {
      return (SdkComboBoxItem)super.getElementAt(index);
    }
  }

  private static Condition<Sdk> getSdkFilter(@Nullable final Condition<SdkTypeId> filter) {
    return filter == null ? Conditions.<Sdk>alwaysTrue() : new Condition<Sdk>() {
      @Override
      public boolean value(Sdk sdk) {
        return filter.value(sdk.getSdkType());
      }
    };
  }

  public static class SdkComboBoxItem {
    private final Sdk mySdk;

    public SdkComboBoxItem(@Nullable Sdk sdk) {
      mySdk = sdk;
    }

    public Sdk getSdk() {
      return mySdk;
    }

    @Nullable
    public String getSdkName() {
      return mySdk != null ? mySdk.getName() : null;
    }

    @Override
    public String toString() {
      return mySdk.getName();
    }
  }

  @Deprecated
  public static class ProjectSdkComboBoxItem extends SdkComboBoxItem {
    public ProjectSdkComboBoxItem() {
      super(null);
    }

    @Override
    public String toString() {
      return ProjectBundle.message("jdk.combo.box.project.item");
    }
  }

  public static class NoneSdkComboBoxItem extends SdkComboBoxItem {
    public NoneSdkComboBoxItem() {
      super(null);
    }

    @Override
    public String toString() {
      return ProjectBundle.message("sdk.combo.box.none.item");
    }
  }

  private static class InvalidSdkComboBoxItem extends SdkComboBoxItem {
    private final String mySdkName;

    public InvalidSdkComboBoxItem(String name) {
      super(null);
      mySdkName = name;
    }

    @Override
    public String getSdkName() {
      return mySdkName;
    }

    @Override
    public String toString() {
      return ProjectBundle.message("jdk.combo.box.invalid.item", mySdkName);
    }
  }
}
