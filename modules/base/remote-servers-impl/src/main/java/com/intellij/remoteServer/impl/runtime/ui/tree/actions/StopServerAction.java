package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.icons.AllIcons;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServerNode;
import javax.annotation.Nonnull;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public class StopServerAction extends ServerActionBase {

  public StopServerAction() {
    super("Stop/Disconnect", "Stop/disconnect from the selected server", AllIcons.Actions.Suspend);
  }

  protected void performAction(@Nonnull ServerNode serverNode) {
    if (serverNode.isStopActionEnabled()) {
      serverNode.stopServer();
    }
  }

  @Override
  protected boolean isEnabledForServer(@Nonnull ServerNode serverNode) {
    return serverNode.isStopActionEnabled();
  }
}
