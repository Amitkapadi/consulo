/*
 * Copyright 2013-2016 consulo.io
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
package consulo.ui.shared;

import consulo.annotation.UsedInPlugin;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * @author VISTALL
 * @since 21-Jun-16
 */
public final class RGBColor implements Serializable, ColorValue {
  @Nonnull
  @UsedInPlugin
  public static RGBColor fromFloatValues(float r, float g, float b) {
    return fromFloatValues(r, g, b, 1f);
  }

  @Nonnull
  @UsedInPlugin
  public static RGBColor fromFloatValues(float r, float g, float b, float a) {
    return new RGBColor((int)(r * 255 + 0.5), (int)(g * 255 + 0.5), (int)(b * 255 + 0.5), a);
  }

  /**
   * Converts a <code>String</code> to an integer and returns the
   * specified opaque <code>Color</code>. This method handles string
   * formats that are used to represent octal and hexadecimal numbers.
   *
   * @param nm a <code>String</code> that represents
   *           an opaque color as a 24-bit integer
   * @return the new <code>Color</code> object.
   * @throws NumberFormatException if the specified string cannot
   *                               be interpreted as a decimal,
   *                               octal, or hexadecimal integer.
   * @see java.lang.Integer#decode
   */
  @Nonnull
  @UsedInPlugin
  public static RGBColor decode(@Nonnull String nm) {
    int i = Integer.decode(nm);
    return new RGBColor((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
  }

  private int myRed;
  private int myGreen;
  private int myBlue;
  private float myAlpha;

  private RGBColor() {
  }

  public RGBColor(int red, int green, int blue) {
    this(red, green, blue, 1f);
  }

  public RGBColor(RGBColor color, float alpha) {
    this(color.getRed(), color.getGreen(), color.getBlue(), alpha);
  }

  public RGBColor(int red, int green, int blue, float alpha) {
    myRed = red;
    myGreen = green;
    myBlue = blue;
    myAlpha = alpha;
  }

  public float[] getFloatValues() {
    float[] values = new float[4];
    values[0] = ((float)getRed()) / 255f;
    values[1] = ((float)getGreen()) / 255f;
    values[2] = ((float)getBlue()) / 255f;
    values[3] = getAlpha();
    return values;
  }

  @Nonnull
  @Override
  public RGBColor toRGB() {
    return this;
  }

  public int getRed() {
    return myRed;
  }

  public int getGreen() {
    return myGreen;
  }

  public int getBlue() {
    return myBlue;
  }

  public float getAlpha() {
    return myAlpha;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RGBColor rgbColor = (RGBColor)o;

    if (myRed != rgbColor.myRed) return false;
    if (myGreen != rgbColor.myGreen) return false;
    if (myBlue != rgbColor.myBlue) return false;
    if (Float.compare(rgbColor.myAlpha, myAlpha) != 0) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myRed;
    result = 31 * result + myGreen;
    result = 31 * result + myBlue;
    result = 31 * result + (myAlpha != +0.0f ? Float.floatToIntBits(myAlpha) : 0);
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("RGBColor{");
    sb.append("myRed=").append(myRed);
    sb.append(", myGreed=").append(myGreen);
    sb.append(", myBlue=").append(myBlue);
    sb.append(", myAlpha=").append(myAlpha);
    sb.append('}');
    return sb.toString();
  }
}
