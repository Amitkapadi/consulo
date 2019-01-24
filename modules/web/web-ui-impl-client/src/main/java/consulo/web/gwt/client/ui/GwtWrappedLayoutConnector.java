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
package consulo.web.gwt.client.ui;

import com.vaadin.client.ConnectorHierarchyChangeEvent;
import com.vaadin.shared.ui.Connect;
import consulo.web.gwt.client.util.GwtUIUtil;

/**
 * @author VISTALL
 * @since 26-Oct-17
 */
@Connect(canonicalName = GwtConnectorConstants.PACKAGE_NAME + ".WGwtWrappedLayoutImpl")
public class GwtWrappedLayoutConnector extends GwtLayoutConnector {
  @Override
  public void onConnectorHierarchyChange(ConnectorHierarchyChangeEvent connectorHierarchyChangeEvent) {
    getWidget().build(GwtUIUtil.remapWidgets(this));
  }

  @Override
  public GwtWrappedLayout getWidget() {
    return (GwtWrappedLayout)super.getWidget();
  }
}
