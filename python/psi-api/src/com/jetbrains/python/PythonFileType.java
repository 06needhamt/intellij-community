package com.jetbrains.python;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import icons.PythonPsiApiIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class PythonFileType extends LanguageFileType {
  private static final Pattern ENCODING_PATTERN = Pattern.compile("coding[:=]\\s*([-\\w.]+)");

  public static PythonFileType INSTANCE = new PythonFileType();

  public PythonFileType() {
    this(new PythonLanguage());
  }

  public PythonFileType(Language language) {
    super(language);
  }

  @NotNull
  public String getName() {
    return "Python";
  }

  @NotNull
  public String getDescription() {
    return "Python files";
  }

  @NotNull
  public String getDefaultExtension() {
    return "py";
  }

  @NotNull
  public Icon getIcon() {
    return PythonPsiApiIcons.PythonFile;
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, byte[] content) {
    if (CharsetToolkit.hasUTF8Bom(content)) {
      return CharsetToolkit.UTF8;
    }
    ByteBuffer bytes = ByteBuffer.wrap(content, 0, Math.min(256, content.length));
    String decoded = CharsetToolkit.UTF8_CHARSET.decode(bytes).toString();
    return getCharsetFromEncodingDeclaration(StringUtil.convertLineSeparators(decoded));
  }

  @Override
  public Charset extractCharsetFromFileContent(@Nullable Project project, @Nullable VirtualFile file, @NotNull String content) {
    final String charsetName = getCharsetFromEncodingDeclaration(content);
    if (charsetName == null) {
      return null;
    }
    try {
      return Charset.forName(charsetName);
    }
    catch (IllegalCharsetNameException e) {
      return null;
    }
    catch (UnsupportedCharsetException e) {
      return null;
    }
  }

  @Nullable
  public static String getCharsetFromEncodingDeclaration(String content) {
    if (!content.contains("coding")) {
      return null;
    }
    final List<String> lines = StringUtil.split(content, "\n");
    int count = 0;
    for (String line : lines) {
      final Matcher matcher = ENCODING_PATTERN.matcher(line);
      if (matcher.find()) {
        final String charset = matcher.group(1);
        return normalizeCharset(charset);
      }
      count++;
      if (count == 2) break;
    }
    return null;
  }

  @Nullable
  private static String normalizeCharset(String charset) {
    if (charset == null) {
      return null;
    }
    charset = charset.toLowerCase();
    if ("latin-1".equals(charset)) {
      return "iso-8859-1";
    }
    return charset;
  }
}
