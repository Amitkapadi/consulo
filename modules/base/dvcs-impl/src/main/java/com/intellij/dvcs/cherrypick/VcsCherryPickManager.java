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
package com.intellij.dvcs.cherrypick;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import consulo.logging.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.*;

@Singleton
public class VcsCherryPickManager {
  private static final Logger LOG = Logger.getInstance(VcsCherryPickManager.class);
  @Nonnull
  private final Project myProject;
  @Nonnull
  private final ProjectLevelVcsManager myProjectLevelVcsManager;
  @Nonnull
  private final Set<CommitId> myIdsInProgress = ContainerUtil.newConcurrentSet();

  @Inject
  public VcsCherryPickManager(@Nonnull Project project, @Nonnull ProjectLevelVcsManager projectLevelVcsManager) {
    myProject = project;
    myProjectLevelVcsManager = projectLevelVcsManager;
  }

  public void cherryPick(@Nonnull VcsLog log) {
    log.requestSelectedDetails(new Consumer<List<VcsFullCommitDetails>>() {
      @Override
      public void consume(List<VcsFullCommitDetails> details) {
        ProgressManager.getInstance().run(new CherryPickingTask(myProject, ContainerUtil.reverse(details)));
      }
    }, null);
  }

  public boolean isCherryPickAlreadyStartedFor(@Nonnull List<CommitId> commits) {
    for (CommitId commit : commits) {
      if (myIdsInProgress.contains(commit)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private VcsCherryPicker getCherryPickerForCommit(@Nonnull VcsFullCommitDetails commitDetails) {
    AbstractVcs vcs = myProjectLevelVcsManager.getVcsFor(commitDetails.getRoot());
    if (vcs == null) return null;
    VcsKey key = vcs.getKeyInstanceMethod();
    return getCherryPickerFor(key);
  }

  @Nullable
  public VcsCherryPicker getCherryPickerFor(@Nonnull final VcsKey key) {
    return ContainerUtil.find(Extensions.getExtensions(VcsCherryPicker.EXTENSION_POINT_NAME, myProject), new Condition<VcsCherryPicker>() {
      @Override
      public boolean value(VcsCherryPicker picker) {
        return picker.getSupportedVcs().equals(key);
      }
    });
  }

  private class CherryPickingTask extends Task.Backgroundable {
    @Nonnull
    private final List<VcsFullCommitDetails> myAllDetailsInReverseOrder;
    @Nonnull
    private final ChangeListManagerEx myChangeListManager;

    public CherryPickingTask(@Nonnull Project project, @Nonnull List<VcsFullCommitDetails> detailsInReverseOrder) {
      super(project, "Cherry-Picking");
      myAllDetailsInReverseOrder = detailsInReverseOrder;
      myChangeListManager = (ChangeListManagerEx)ChangeListManager.getInstance(myProject);
      myChangeListManager.blockModalNotifications();
    }

    @Nullable
    private VcsCherryPicker getCherryPickerOrReportError(@Nonnull VcsFullCommitDetails details) {
      CommitId commitId = new CommitId(details.getId(), details.getRoot());
      if (myIdsInProgress.contains(commitId)) {
        showError("Cherry pick process is already started for commit " +
                  commitId.getHash().toShortString() +
                  " from root " +
                  commitId.getRoot().getName());
        return null;
      }
      myIdsInProgress.add(commitId);

      VcsCherryPicker cherryPicker = getCherryPickerForCommit(details);
      if (cherryPicker == null) {
        showError(
                "Cherry pick is not supported for commit " + details.getId().toShortString() + " from root " + details.getRoot().getName());
        return null;
      }
      return cherryPicker;
    }

    public void showError(@Nonnull String message) {
      VcsNotifier.getInstance(myProject).notifyWeakError(message);
      LOG.warn(message);
    }

    @Override
    public void run(@Nonnull ProgressIndicator indicator) {
      try {
        boolean isOk = true;
        MultiMap<VcsCherryPicker, VcsFullCommitDetails> groupedCommits = createArrayMultiMap();
        for (VcsFullCommitDetails details : myAllDetailsInReverseOrder) {
          VcsCherryPicker cherryPicker = getCherryPickerOrReportError(details);
          if (cherryPicker == null) {
            isOk = false;
            break;
          }
          groupedCommits.putValue(cherryPicker, details);
        }

        if (isOk) {
          for (Map.Entry<VcsCherryPicker, Collection<VcsFullCommitDetails>> entry : groupedCommits.entrySet()) {
            entry.getKey().cherryPick(Lists.newArrayList(entry.getValue()));
          }
        }
      }
      finally {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            myChangeListManager.unblockModalNotifications();
            for (VcsFullCommitDetails details : myAllDetailsInReverseOrder) {
              myIdsInProgress.remove(new CommitId(details.getId(), details.getRoot()));
            }
          }
        });
      }
    }

    @Nonnull
    public MultiMap<VcsCherryPicker, VcsFullCommitDetails> createArrayMultiMap() {
      return new MultiMap<VcsCherryPicker, VcsFullCommitDetails>() {
        @Nonnull
        @Override
        protected Collection<VcsFullCommitDetails> createCollection() {
          return new ArrayList<>();
        }
      };
    }
  }

  public static VcsCherryPickManager getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, VcsCherryPickManager.class);
  }
}
