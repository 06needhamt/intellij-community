package com.intellij.lang.properties;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.nio.charset.Charset;

/**
 * @author max
 */
public class PropertiesFileType extends LanguageFileType {
  public static final Icon FILE_ICON = IconLoader.getIcon("/fileTypes/properties.png");
  public static final LanguageFileType FILE_TYPE = new PropertiesFileType();
  @NonNls public static final String DEFAULT_EXTENSION = "properties";
  @NonNls public static final String DOT_DEFAULT_EXTENSION = "."+DEFAULT_EXTENSION;

  private PropertiesFileType() {
    super(PropertiesLanguage.INSTANCE);
  }

  @NotNull
  public String getName() {
    return "Properties";
  }

  @NotNull
  public String getDescription() {
    return PropertiesBundle.message("properties.files.file.type.description");
  }

  @NotNull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  public Icon getIcon() {
    return FILE_ICON;
  }

  public String getCharset(@NotNull VirtualFile file, final byte[] content) {
    Charset charset = EncodingManager.getInstance().getDefaultCharsetForPropertiesFiles(file);
    String defaultCharsetName = charset == null ? CharsetToolkit.getDefaultSystemCharset().name() : charset.name();
    return defaultCharsetName;
  }
}
