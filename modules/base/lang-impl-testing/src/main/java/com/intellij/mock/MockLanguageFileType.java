package com.intellij.mock;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;

/**
 * @author gregsh
 */
public class MockLanguageFileType extends LanguageFileType {

  private final String myExtension;

  public MockLanguageFileType(@Nonnull Language language, String extension) {
    super(language);
    myExtension = extension;
  }

  @Nonnull
  @Override
  public String getId() {
    return getLanguage().getID();
  }

  @Nonnull
  @Override
  public String getDescription() {
    return "";
  }

  @Nonnull
  @Override
  public String getDefaultExtension() {
    return myExtension;
  }

  @Override
  public Image getIcon() {
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof LanguageFileType)) return false;
    return getLanguage().equals(((LanguageFileType)obj).getLanguage());
  }
}
