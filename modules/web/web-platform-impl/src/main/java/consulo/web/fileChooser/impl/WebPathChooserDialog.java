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
package consulo.web.fileChooser.impl;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.PathChooserDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.ui.*;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.shared.Size;
import consulo.ui.shared.border.BorderPosition;
import consulo.web.fileChooser.FileTreeComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 15-Sep-17
 */
public class WebPathChooserDialog implements PathChooserDialog, FileChooserDialog {
  private FileChooserDescriptor myDescriptor;
  @Nullable
  private Project myProject;

  public WebPathChooserDialog(@Nullable Project project, @Nonnull FileChooserDescriptor descriptor) {
    myDescriptor = descriptor;
    myProject = project;
  }

  @Nonnull
  @Override
  @RequiredUIAccess
  public AsyncResult<VirtualFile[]> chooseAsync(@Nullable VirtualFile toSelect) {
    return chooseAsync(myProject, toSelect == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[]{toSelect});
  }

  @RequiredUIAccess
  @Nonnull
  @Override
  public AsyncResult<VirtualFile[]> chooseAsync(@Nullable Project project, @Nonnull VirtualFile[] toSelect) {
    AsyncResult<VirtualFile[]> result = AsyncResult.undefined();

    Window fileTree = Window.createModal("Select file");
    fileTree.setSize(new Size(400, 400));
    fileTree.setContent(Label.create("Test"));

    DockLayout dockLayout = DockLayout.create();
    Tree<FileElement> component = FileTreeComponent.create(myProject, myDescriptor);

    dockLayout.center(component);

    DockLayout bottomLayout = DockLayout.create();
    bottomLayout.addBorder(BorderPosition.TOP);
    HorizontalLayout rightButtons = HorizontalLayout.create();
    bottomLayout.right(rightButtons);

    Button ok = Button.create("OK");
    ok.addClickListener(() -> {
      fileTree.close();

      VirtualFile file = component.getSelectedNode().getValue().getFile();

      UIAccess.current().give(() -> result.setDone(new VirtualFile[]{file}));
    });
    ok.setEnabled(false);
    rightButtons.add(ok);
    consulo.ui.Button cancel = Button.create("Cancel");
    cancel.addClickListener(() -> {
      fileTree.close();

      UIAccess.current().give((Runnable)result::setRejected);
    });

    component.addSelectListener(node -> {
      VirtualFile file = node.getValue().getFile();
      ok.setEnabled(myDescriptor.isFileSelectable(file));
    });

    rightButtons.add(cancel);
    dockLayout.bottom(bottomLayout);

    fileTree.setContent(dockLayout);

    fileTree.show();

    return result;
  }
}
