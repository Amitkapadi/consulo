/*
 * Copyright 2013-2016 must-be.org
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
package consulo.ui.internal;

import consulo.ui.CheckBox;
import consulo.ui.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author VISTALL
 * @since 09-Jun-16
 */
public class DesktopCheckBoxImpl extends JCheckBox implements CheckBox {
  private static class ActionListenerImpl implements ActionListener {
    private SelectListener mySelectListener;

    public ActionListenerImpl(SelectListener selectListener) {
      mySelectListener = selectListener;
    }

    @Override
    public int hashCode() {
      return mySelectListener.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ActionListenerImpl && ((ActionListenerImpl)obj).mySelectListener.equals(((ActionListenerImpl)obj).mySelectListener);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      mySelectListener.selectChanged((CheckBox)e.getSource());
    }
  }

  public DesktopCheckBoxImpl(String text, boolean selected) {
    super(text, selected);
  }

  @Nullable
  @Override
  public Component getParentComponent() {
    return (Component)getParent();
  }

  @Override
  public void addSelectListener(@NotNull SelectListener selectListener) {
    addActionListener(new ActionListenerImpl(selectListener));
  }

  @Override
  public void removeSelectListener(@NotNull SelectListener selectListener) {
    removeActionListener(new ActionListenerImpl(selectListener));
  }
}
