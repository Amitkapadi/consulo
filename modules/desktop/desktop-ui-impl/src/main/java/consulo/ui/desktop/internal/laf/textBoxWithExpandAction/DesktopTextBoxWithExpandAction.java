/*
 * Copyright 2013-2019 consulo.io
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
package consulo.ui.desktop.internal.laf.textBoxWithExpandAction;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.fields.ExpandableTextField;
import consulo.awt.TargetAWT;
import consulo.ui.RequiredUIAccess;
import consulo.ui.TextBoxWithExpandAction;
import consulo.ui.desktop.internal.DesktopTextBoxImpl;
import consulo.ui.desktop.internal.base.SwingComponentDelegate;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.EventListener;
import java.util.List;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 2019-04-26
 */
public class DesktopTextBoxWithExpandAction {
  public static TextBoxWithExpandAction create(@Nullable Image editButtonImage, String dialogTitle, Function<String, List<String>> parser, Function<List<String>, String> joiner) {
    LookAndFeel lookAndFeel = UIManager.getLookAndFeel();

    if (lookAndFeel instanceof SupportTextBoxWithExpandActionLaf) {
      return new SupportedTextBoxWithExpandAction(parser, joiner, (SupportTextBoxWithExpandActionLaf)lookAndFeel);
    }

    return new FallbackTextBoxWithExpandAction(editButtonImage, dialogTitle, parser, joiner);
  }

  private static class SupportedTextBoxWithExpandAction extends SwingComponentDelegate<ExpandableTextField> implements TextBoxWithExpandAction {
    private SupportedTextBoxWithExpandAction(Function<String, List<String>> parser, Function<List<String>, String> joiner, SupportTextBoxWithExpandActionLaf lookAndFeel) {
      myComponent = new ExpandableTextField(parser::apply, joiner::apply, lookAndFeel);
      myComponent.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        @SuppressWarnings("unchecked")
        @RequiredUIAccess
        protected void textChanged(DocumentEvent e) {
          getListenerDispatcher(ValueListener.class).valueChanged(new ValueEvent(SupportedTextBoxWithExpandAction.this, getValue()));
        }
      });
    }

    @Nullable
    @Override
    public String getValue() {
      return myComponent.getText();
    }

    @RequiredUIAccess
    @Override
    public void setValue(String value, boolean fireEvents) {
      myComponent.setText(value);
    }

    @Nonnull
    @Override
    public TextBoxWithExpandAction setDialogTitle(@Nonnull String text) {
      return this;
    }
  }

  private static class FallbackTextBoxWithExpandAction extends SwingComponentDelegate<ComponentWithBrowseButton<JComponent>> implements TextBoxWithExpandAction {
    private DesktopTextBoxImpl myTextBox;

    private String myDialogTitle;

    private FallbackTextBoxWithExpandAction(Image editButtonImage, String dialogTitle, Function<String, List<String>> parser, Function<List<String>, String> joiner) {
      myTextBox = new DesktopTextBoxImpl("");
      myDialogTitle = StringUtil.notNullize(dialogTitle);

      JTextField awtTextField = (JTextField)myTextBox.toAWTComponent();
      myComponent = new ComponentWithBrowseButton<>(awtTextField, e -> Messages.showTextAreaDialog(awtTextField, myDialogTitle, myDialogTitle, parser::apply, joiner::apply));

      if (editButtonImage != null) {
        myComponent.setButtonIcon(TargetAWT.to(editButtonImage));
      }
    }

    @Nonnull
    @Override
    public <T extends EventListener> Disposable addListener(@Nonnull Class<T> eventClass, @Nonnull T listener) {
      return myTextBox.addListener(eventClass, listener);
    }

    @Nonnull
    @Override
    public <T extends EventListener> T getListenerDispatcher(@Nonnull Class<T> eventClass) {
      return myTextBox.getListenerDispatcher(eventClass);
    }

    @Nullable
    @Override
    public String getValue() {
      return myTextBox.getValue();
    }

    @RequiredUIAccess
    @Override
    public void setValue(String value, boolean fireEvents) {
      myTextBox.setValue(value, fireEvents);
    }

    @Nonnull
    @Override
    public TextBoxWithExpandAction setDialogTitle(@Nonnull String text) {
      myDialogTitle = text;
      return this;
    }
  }
}
