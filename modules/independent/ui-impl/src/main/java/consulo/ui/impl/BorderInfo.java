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
package consulo.ui.impl;

import consulo.ui.border.BorderPosition;
import consulo.ui.border.BorderStyle;
import consulo.ui.style.ColorKey;

/**
* @author VISTALL
* @since 15-Oct-17
*/
public class BorderInfo {
  private BorderPosition myBorderPosition;
  private BorderStyle myBorderStyle;
  private ColorKey myColorKey;
  private int myWidth;

  public BorderInfo(BorderPosition borderPosition, BorderStyle borderStyle, ColorKey colorKey, int width) {
    myBorderPosition = borderPosition;
    myBorderStyle = borderStyle;
    myColorKey = colorKey;
    myWidth = width;
  }

  public BorderPosition getBorderPosition() {
    return myBorderPosition;
  }

  public BorderStyle getBorderStyle() {
    return myBorderStyle;
  }

  public ColorKey getColorKey() {
    return myColorKey;
  }

  public int getWidth() {
    return myWidth;
  }
}
