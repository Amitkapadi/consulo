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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.SdkImpl;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.SdkProjectStructureElement;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.*;

public class SdkListConfigurable extends BaseStructureConfigurable {
  private final ProjectSdksModel mySdksTreeModel;
  private final SdkModel.Listener myListener = new SdkModel.Listener() {
    @Override
    public void sdkAdded(Sdk sdk) {
    }

    @Override
    public void beforeSdkRemove(Sdk sdk) {
    }

    @Override
    public void sdkChanged(Sdk sdk, String previousName) {
      updateName();
    }

    @Override
    public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
      updateName();
    }

    private void updateName() {
      final TreePath path = myTree.getSelectionPath();
      if (path != null) {
        final NamedConfigurable configurable = ((MyNode)path.getLastPathComponent()).getConfigurable();
        if (configurable != null && configurable instanceof SdkConfigurable) {
          configurable.updateName();
        }
      }
    }
  };

  public SdkListConfigurable(final Project project, ProjectStructureConfigurable root) {
    super(project);
    mySdksTreeModel = root.getProjectSdksModel();
    mySdksTreeModel.addListener(myListener);
  }

  @Override
  protected String getComponentStateKey() {
    return "JdkListConfigurable.UI";
  }

  @Override
  protected void processRemovedItems() {
  }

  @Override
  protected boolean wasObjectStored(final Object editableObject) {
    return false;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("jdks.node.display.name");
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return myCurrentConfigurable != null ? myCurrentConfigurable.getHelpTopic() : "reference.settingsdialog.project.structure.jdk";
  }

  @Override
  @NotNull
  @NonNls
  public String getId() {
    return "jdk.list";
  }

  @Override
  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Override
  protected void loadTree() {
    final Map<SdkType, List<Sdk>> map = new HashMap<SdkType, List<Sdk>>();

    for(Sdk sdk : mySdksTreeModel.getProjectSdks().values()) {
      final SdkType sdkType = (SdkType)sdk.getSdkType();

      List<Sdk> list = map.get(sdkType);
      if(list == null) {
        map.put(sdkType, list = new ArrayList<Sdk>());
      }

      list.add(sdk);
    }

    for (Map.Entry<SdkType, List<Sdk>> entry : map.entrySet()) {
      final SdkType key = entry.getKey();
      final List<Sdk> value = entry.getValue();

      final MyNode groupNode = createSdkGroupNode(key);
      groupNode.setAllowsChildren(true);
      addNode(groupNode, myRoot);

      for(Sdk sdk : value) {
        final SdkConfigurable configurable = new SdkConfigurable((SdkImpl)sdk, mySdksTreeModel, TREE_UPDATER, myHistory,
                                                                 myProject);

        addNode(new MyNode(configurable), groupNode);
      }
    }
  }

  @NotNull
  private static MyNode createSdkGroupNode(SdkType key) {
    return new MyNode(new TextConfigurable<SdkType>(key, key.getPresentableName(), "", "", key.getGroupIcon()));
  }

  @NotNull
  @Override
  protected Collection<? extends ProjectStructureElement> getProjectStructureElements() {
    final List<ProjectStructureElement> result = new ArrayList<ProjectStructureElement>();
    for (Sdk sdk : mySdksTreeModel.getProjectSdks().values()) {
      result.add(new SdkProjectStructureElement(myContext, sdk));
    }
    return result;
  }

  public boolean addSdkNode(final Sdk sdk, final boolean selectInTree) {
    if (!myUiDisposed) {
      myContext.getDaemonAnalyzer().queueUpdate(new SdkProjectStructureElement(myContext, sdk));

      MyNode newSdkNode = new MyNode(new SdkConfigurable((SdkImpl)sdk, mySdksTreeModel, TREE_UPDATER, myHistory, myProject));
 
      final MyNode groupNode = MasterDetailsComponent.findNodeByObject(myRoot, sdk.getSdkType());
      if(groupNode != null) {
        addNode(newSdkNode, groupNode);
      }
      else {
        final MyNode sdkGroupNode = createSdkGroupNode((SdkType)sdk.getSdkType());

        addNode(sdkGroupNode, myRoot);
        addNode(newSdkNode, sdkGroupNode);
      }

      if (selectInTree) {
        selectNodeInTree(newSdkNode);
      }
      return true;
    }
    return false;
  }

  @Override
  protected boolean canBeRemoved(Object[] editableObjects) {
    for (Object editableObject : editableObjects) {
      if(editableObject instanceof Sdk && ((Sdk)editableObject).isBundled()) {
        return false;
      }
    }
    return super.canBeRemoved(editableObjects);
  }

  @Override
  protected void onItemDeleted(Object item) {
    for(int i = 0; i < myRoot.getChildCount(); i++) {
      final TreeNode childAt = myRoot.getChildAt(i);
      if(childAt instanceof MyNode) {
        if(childAt.getChildCount() == 0) {
          myRoot.remove((MutableTreeNode)childAt);
          ((DefaultTreeModel)myTree.getModel()).reload(myRoot);
        }
      }
    }
  }

  @Override
  public void dispose() {
    mySdksTreeModel.removeListener(myListener);
    mySdksTreeModel.disposeUIResources();
  }

  public ProjectSdksModel getSdksTreeModel() {
    return mySdksTreeModel;
  }

  @Override
  public void reset() {
    super.reset();
    myTree.setRootVisible(false);
  }

  @Override
  public void apply() throws ConfigurationException {
    boolean modifiedJdks = false;
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final TreeNode groupNode = myRoot.getChildAt(i);

      for(int k = 0; k < groupNode.getChildCount(); k ++) {
        final MyNode sdkNode = (MyNode) groupNode.getChildAt(k);
        final NamedConfigurable configurable = sdkNode.getConfigurable();
        if (configurable.isModified()) {
          configurable.apply();
          modifiedJdks = true;
        }
      }
    }

    if (mySdksTreeModel.isModified() || modifiedJdks) mySdksTreeModel.apply(this);
  }

  @Override
  public boolean isModified() {
    return super.isModified() || mySdksTreeModel.isModified();
  }

  public static SdkListConfigurable getInstance(Project project) {
    return ServiceManager.getService(project, SdkListConfigurable.class);
  }

  @Override
  public AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(ProjectBundle.message("add.new.jdk.text")) {
      @NotNull
      @Override
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        DefaultActionGroup group = new DefaultActionGroup(ProjectBundle.message("add.new.jdk.text"), true);
        mySdksTreeModel.createAddActions(group, myTree, new Consumer<Sdk>() {
          @Override
          public void consume(final Sdk projectJdk) {
            addSdkNode(projectJdk, true);
          }
        });
        return group.getChildren(null);
      }
    };
  }

  @Override
  protected void removeJdk(final Sdk jdk) {
    mySdksTreeModel.removeSdk(jdk);
    myContext.getDaemonAnalyzer().removeElement(new SdkProjectStructureElement(myContext, jdk));
  }

  @Override
  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select an SDK to view or edit its details here";
  }
}
