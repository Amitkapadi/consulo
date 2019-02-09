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
package com.intellij.openapi.util;

import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import consulo.ui.image.Image;
import org.intellij.lang.annotations.MagicConstant;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;

public interface Iconable {
  int ICON_FLAG_VISIBILITY = 0x0001;
  int ICON_FLAG_READ_STATUS = 0x0002;

  @MagicConstant(flags = {ICON_FLAG_VISIBILITY, ICON_FLAG_READ_STATUS})
  @interface IconFlags {
  }

  Icon getIcon(@IconFlags int flags);

  class LastComputedIcon {
    private static final Key<ConcurrentIntObjectMap<Image>> LAST_COMPUTED_ICON = Key.create("lastComputedIcon");

    @Nullable
    public static Image get(@Nonnull UserDataHolder holder, int flags) {
      ConcurrentIntObjectMap<Image> map = holder.getUserData(LAST_COMPUTED_ICON);
      return map == null ? null : map.get(flags);
    }

    public static void put(@Nonnull UserDataHolder holder, Image icon, int flags) {
      ConcurrentIntObjectMap<Image> map = holder.getUserData(LAST_COMPUTED_ICON);
      if (icon == null) {
        if (map != null) {
          map.remove(flags);
        }
      }
      else {
        if (map == null) {
          map = ((UserDataHolderEx)holder).putUserDataIfAbsent(LAST_COMPUTED_ICON, ContainerUtil.createConcurrentIntObjectMap());
        }
        map.put(flags, icon);
      }
    }
  }
}
