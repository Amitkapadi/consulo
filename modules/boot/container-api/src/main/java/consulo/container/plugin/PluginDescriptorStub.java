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
package consulo.container.plugin;

import consulo.util.nodep.xml.node.SimpleXmlElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.lang.Override;
import java.lang.UnsupportedOperationException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 2019-07-17
 */
public abstract class PluginDescriptorStub implements PluginDescriptor {
  @Nonnull
  @Override
  public ClassLoader getPluginClassLoader() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public File getPath() {
    return null;
  }

  @Nullable
  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public String getChangeNotes() {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Nonnull
  @Override
  public PluginId[] getDependentPluginIds() {
    return PluginId.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public PluginId[] getOptionalDependentPluginIds() {
    return PluginId.EMPTY_ARRAY;
  }

  @Override
  public String getVendor() {
    return null;
  }

  @Nullable
  @Override
  public String getVersion() {
    return null;
  }

  @Nullable
  @Override
  public String getPlatformVersion() {
    return null;
  }

  @Override
  public String getResourceBundleBaseName() {
    return null;
  }

  @Nullable
  @Override
  public String getLocalize() {
    return null;
  }

  @Override
  public String getCategory() {
    return null;
  }

  @Nonnull
  @Override
  public List<SimpleXmlElement> getActionsDescriptionElements() {
    return Collections.emptyList();
  }

  @Nonnull
  @Override
  public List<ComponentConfig> getAppComponents() {
    return Collections.emptyList();
  }

  @Nonnull
  @Override
  public List<ComponentConfig> getProjectComponents() {
    return Collections.emptyList();
  }

  @Nonnull
  @Override
  public List<PluginListenerDescriptor> getApplicationListeners() {
    return Collections.emptyList();
  }

  @Nonnull
  @Override
  public List<PluginListenerDescriptor> getProjectListeners() {
    return Collections.emptyList();
  }

  @Nonnull
  @Override
  public List<PluginListenerDescriptor> getModuleListeners() {
    return Collections.emptyList();
  }

  @Override
  public String getVendorEmail() {
    return null;
  }

  @Override
  public String getVendorUrl() {
    return null;
  }

  @Override
  public String getUrl() {
    return null;
  }

  @Nonnull
  @Override
  public Collection<HelpSetPath> getHelpSets() {
    return Collections.emptyList();
  }

  @Override
  public String getDownloads() {
    return null;
  }

  @Nonnull
  @Override
  public List<SimpleExtension> getSimpleExtensions() {
    return Collections.emptyList();
  }

  @Override
  public boolean isBundled() {
    return false;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public void setEnabled(boolean enabled) {

  }

  @Override
  public boolean isLoaded() {
    return false;
  }

  @Override
  public boolean isDeleted() {
    return false;
  }
}
