package com.intellij.ide.browsers;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.XmlBundle;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author spleaner
 */
@State(name = "WebBrowsersConfiguration", storages = {@Storage(id = "other", file = "$APP_CONFIG$/browsers.xml")})
public class BrowsersConfiguration implements ApplicationComponent, Configurable, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.browsers.BrowsersConfiguration");
  private static final Icon ICON = IconLoader.getIcon("/general/browsersettings.png");

  private static final Icon SAFARI_ICON = IconLoader.getIcon("/xml/browsers/safari16.png");
  private static final Icon FIREFOX_ICON = IconLoader.getIcon("/xml/browsers/firefox16.png");
  private static final Icon EXPLORER_ICON = IconLoader.getIcon("/xml/browsers/explorer16.png");
  private static final Icon OPERA_ICON = IconLoader.getIcon("/xml/browsers/opera16.png");
  private static final Icon CHROME_ICON = IconLoader.getIcon("/xml/browsers/chrome16.png");
  private WebBrowsersPanel mySettingsPanel;

  private Map<BrowserFamily, Pair<String, Boolean>> myBrowserToPathMap = new HashMap<BrowserFamily, Pair<String, Boolean>>();

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static enum BrowserFamily {
    EXPLORER(XmlBundle.message("browsers.explorer"), "iexplore", null, null, EXPLORER_ICON),
    SAFARI(XmlBundle.message("browsers.safari"), "safari", "safari", "Safari", SAFARI_ICON),
    OPERA(XmlBundle.message("browsers.opera"), "opera", "opera", "Opera", OPERA_ICON),
    FIREFOX(XmlBundle.message("browsers.firefox"), "firefox", "firefox", "Firefox", FIREFOX_ICON),
    CHROME(XmlBundle.message("browsers.chrome"), "chrome", null, null, CHROME_ICON);

    private String myName;
    private String myWindowsPath;
    private String myLinuxPath;
    private String myMacPath;
    private Icon myIcon;

    BrowserFamily(final String name, final String windowsPath, final String linuxPath, final String macPath, final Icon icon) {
      myName = name;
      myWindowsPath = windowsPath;
      myLinuxPath = linuxPath;
      myMacPath = macPath;
      myIcon = icon;
    }

    @Nullable
    public String getExecutionPath() {
      if (SystemInfo.isWindows) {
        return myWindowsPath;
      }
      else if (SystemInfo.isLinux) {
        return myLinuxPath;
      }
      else if (SystemInfo.isMac) {
        return myMacPath;
      }

      return null;
    }

    public String getName() {
      return myName;
    }

    public Icon getIcon() {
      return myIcon;
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public Element getState() {
    @NonNls Element element = new Element("WebBrowsersConfiguration");
    for (BrowserFamily browserFamily : myBrowserToPathMap.keySet()) {
      final Element browser = new Element("browser");
      browser.setAttribute("family", browserFamily.toString());
      final Pair<String, Boolean> value = myBrowserToPathMap.get(browserFamily);
      browser.setAttribute("path", value.first);
      browser.setAttribute("active", value.second.toString());

      element.addContent(browser);
    }

    return element;
  }

  @SuppressWarnings({"unchecked"})
  public void loadState(@NonNls Element element) {
    for (@NonNls Element child : (Iterable<? extends Element>)element.getChildren("browser")) {
      String family = child.getAttributeValue("family");
      final String path = child.getAttributeValue("path");
      final String active = child.getAttributeValue("active");
      final BrowserFamily browserFamily;

      try {
        browserFamily = BrowserFamily.valueOf(family);
        myBrowserToPathMap.put(browserFamily, new Pair<String, Boolean>(path, Boolean.parseBoolean(active)));
      }
      catch (IllegalArgumentException e) {
        // skip
      }
    }
  }

  void updateBrowserValue(final BrowserFamily family, final Pair<String, Boolean> stringBooleanPair) {
    myBrowserToPathMap.put(family, stringBooleanPair);
  }

  Pair<String, Boolean> suggestBrowserPath(@NotNull final BrowserFamily browserFamily) {
    Pair<String, Boolean> result = myBrowserToPathMap.get(browserFamily);
    if (result == null) {
      final String path = browserFamily.getExecutionPath();
      result = new Pair<String, Boolean>(path == null ? "" : path, path != null);
      myBrowserToPathMap.put(browserFamily, result);
    }

    return result;
  }

  public static BrowsersConfiguration getInstance() {
    return ApplicationManager.getApplication().getComponent(BrowsersConfiguration.class);
  }

  @NotNull
  public String getComponentName() {
    return "BrowsersConfiguration";
  }

  public void initComponent() {
    installBrowserActions();
  }

  public void disposeComponent() {
  }

  @Nls
  public String getDisplayName() {
    return XmlBundle.message("browsers.configuration.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    if (mySettingsPanel == null) {
      mySettingsPanel = new WebBrowsersPanel(this);
    }

    return mySettingsPanel;
  }

  public boolean isModified() {
    LOG.assertTrue(mySettingsPanel != null);
    return mySettingsPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    LOG.assertTrue(mySettingsPanel != null);
    mySettingsPanel.apply();
  }

  public void reset() {
    LOG.assertTrue(mySettingsPanel != null);
    mySettingsPanel.reset();
  }

  public void disposeUIResources() {
    mySettingsPanel.dispose();
    mySettingsPanel = null;
  }

  public static void launchBrowser(final BrowserFamily family, @NotNull final String url) {
    getInstance()._launchBrowser(family, url);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void _launchBrowser(final BrowserFamily family, @NotNull final String url) {
    final Pair<String, Boolean> pair = suggestBrowserPath(family);
    if (pair != null) {
      final String path = pair.first;
      if (path != null && path.length() > 0) {
        String[] command = null;
        if (SystemInfo.isMac) {
          command = new String[]{"open", "-a", path, url};
        }
        if (SystemInfo.isWindows9x) {
          if (path.indexOf(File.separatorChar) != -1) {
            command = new String[]{path, url};
          } else {
            command = new String[]{"command.com", "/c", "start", path, url};
          }
        }
        else if (SystemInfo.isWindows) {
          if (path.indexOf(File.separatorChar) != -1) {
            command = new String[]{path, url};
          } else {
            command = new String[]{"cmd.exe", "/c", "start", path, url};
          }
        }
        else if (SystemInfo.isLinux) {
          command = new String[]{path, url};
        }

        if (command != null) {
          try {
            Runtime.getRuntime().exec(command);
          }
          catch (IOException e) {
            Messages.showErrorDialog(e.getMessage(), XmlBundle.message("browser.error"));
          }
        }
        else {
          LOG.assertTrue(false);
        }
      }
      else {
        Messages.showErrorDialog(XmlBundle.message("browser.path.not.specified", family.getName()), XmlBundle.message("browser.path.not.specified.title"));
      }
    }
    else {
      LOG.assertTrue(false);
    }
  }

  public void installBrowserActions() {
    installBrowserAction(BrowserFamily.FIREFOX);
    installBrowserAction(BrowserFamily.EXPLORER);
    installBrowserAction(BrowserFamily.SAFARI);
    installBrowserAction(BrowserFamily.CHROME);
    installBrowserAction(BrowserFamily.OPERA);
  }

  private void installBrowserAction(@NotNull final BrowserFamily family) {
    final ActionManager actionManager = ActionManager.getInstance();

    @NonNls final String actionId = "BROWSER_" + family.toString();
    AnAction action = actionManager.getAction(actionId);
    if (action == null) {
      action = new AnAction(family.getName(), XmlBundle.message("browser.description", family.getName()), getBrowserIcon(family)) {
        @Nullable
        private PsiFile getFile(@NotNull final DataContext context) {
          final Editor editor = PlatformDataKeys.EDITOR.getData(context);
          if (editor != null) {
            final Project project = editor.getProject();
            if (project != null) {
              final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
              final PsiFile psiFile = manager.getPsiFile(editor.getDocument());
              if (psiFile != null) {
                return psiFile;
              }
            }
          }

          return null;
        }


        public void actionPerformed(final AnActionEvent e) {
          final PsiFile psiFile = getFile(e.getDataContext());
          LOG.assertTrue(psiFile != null);
          final WebBrowserUrlProvider provider = WebBrowserUrlProviders.getProvider(psiFile);
          LOG.assertTrue(provider != null);

          final Project project = psiFile.getProject();
          PsiDocumentManager.getInstance(project).commitAllDocuments();

          try {
            launchBrowser(family, provider.getUrl(psiFile, e.getInputEvent().isShiftDown()));
          }
          catch (WebBrowserUrlProvider.BrowserException e1) {
            Messages.showErrorDialog(e1.getMessage(), XmlBundle.message("browser.error"));
          }
          catch (Exception e1) {
            LOG.error(e1);
          }
        }

        @Override
        public void update(final AnActionEvent e) {
          boolean visible = suggestBrowserPath(family).second.booleanValue();
          if (visible) {
            visible = false;
            final PsiFile file = getFile(e.getDataContext());
            if (file != null) {
              final VirtualFile virtualFile = file.getVirtualFile();
              if (virtualFile != null && virtualFile.isInLocalFileSystem()) {
                final WebBrowserUrlProvider urlProvider = WebBrowserUrlProviders.getProvider(file);
                visible = urlProvider != null;
              }
            }
          }

          final Presentation presentation = e.getPresentation();
          presentation.setVisible(visible);
        }
      };

      actionManager.registerAction(actionId, action);

      AnAction actionGroup = actionManager.getAction("EditorContextBarMenu");
      if (actionGroup == null) {
        actionGroup = new DefaultActionGroup();
        actionManager.registerAction("EditorContextBarMenu", actionGroup);
      }

      if (actionGroup instanceof DefaultActionGroup) {
        ((DefaultActionGroup)actionGroup).add(action);
      }
    }
  }

  private static Icon getBrowserIcon(final BrowserFamily family) {
    return family.getIcon();
  }
}
