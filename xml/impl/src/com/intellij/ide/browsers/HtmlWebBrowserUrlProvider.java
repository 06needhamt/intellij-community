package com.intellij.ide.browsers;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class HtmlWebBrowserUrlProvider extends WebBrowserUrlProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.browsers.HtmlWebBrowserUrlProvider");

  @NotNull
  public String getUrl(@NotNull final PsiFile file, final boolean shiftDown) throws Exception {
    final VirtualFile virtualFile = file.getVirtualFile();
    LOG.assertTrue(virtualFile != null);
    return virtualFile.getUrl();
  }

  @Override
  public boolean canHandleFile(@NotNull final PsiFile file) {
    final Language language = file.getViewProvider().getBaseLanguage();
    return HTMLLanguage.INSTANCE == language || XHTMLLanguage.INSTANCE == language;
  }

  public static boolean isAvailableFor(@NotNull final PsiFile file) {
    final Language language = file.getLanguage();
    return language instanceof HTMLLanguage || language instanceof XHTMLLanguage;
  }
}
