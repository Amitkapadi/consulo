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
package consulo.web.servlet;

import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.util.concurrency.AppExecutorUtil;
import consulo.start.WelcomeFrameFactory;
import consulo.ui.Layouts;
import consulo.ui.RequiredUIAccess;
import consulo.ui.UIAccess;
import consulo.ui.Window;
import consulo.ui.ex.internal.WGwtLoadingPanelImpl;
import consulo.web.application.WebApplication;
import consulo.web.application.WebSession;
import consulo.web.application.impl.VaadinWebSessionImpl;
import consulo.web.servlet.ui.UIBuilder;
import consulo.web.servlet.ui.UIServlet;
import org.jetbrains.annotations.NotNull;

import javax.servlet.annotation.WebServlet;
import java.util.concurrent.TimeUnit;

/**
 * @author VISTALL
 * @since 10-Sep-17
 */
public class NewAppUIBuilder implements UIBuilder {
  @WebServlet(urlPatterns = "/app/*")
  public static class Servlet extends UIServlet {
    public Servlet() {
      super(NewAppUIBuilder.class, "/app");
    }
  }

  @RequiredUIAccess
  @Override
  public void build(@NotNull Window window) {
    window.setContent(new WGwtLoadingPanelImpl());

    UIAccess access = UIAccess.get();

    scheduleWelcomeFrame(access, window);
  }

  private void scheduleWelcomeFrame(UIAccess access, Window window) {
    AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
      WebApplication application = WebApplication.getInstance();
      if (application == null || !((ApplicationEx)application).isLoaded()) {
        if (access.isValid()) {
          scheduleWelcomeFrame(access, window);
        }
        return;
      }

      if (access.isValid()) {
        access.give(() -> showWelcomeFrame(application, window));
      }
    }, 1, TimeUnit.SECONDS);
  }

  @RequiredUIAccess
  private void showWelcomeFrame(WebApplication application, Window window) {
    window.setContent(Layouts.dock());

    WebSession currentSession = application.getCurrentSession();
    if (currentSession != null) {
      WebSession newSession = currentSession;

      currentSession.close();

      currentSession = newSession.copy();
    }
    else {
      currentSession = new VaadinWebSessionImpl();
    }

    application.setCurrentSession(currentSession);

    Window frame = WelcomeFrameFactory.getInstance().createFrame();

    frame.show();
  }
}
