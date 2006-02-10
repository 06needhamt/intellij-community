/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ResourceUtil;
import com.intellij.ui.TitledSeparator;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class IntentionDescriptionPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionDescriptionPanel");
  private JPanel myPanel;

  private JPanel myAfterPanel;
  private JPanel myBeforePanel;
  private JEditorPane myDescriptionBrowser;
  private TitledSeparator myBeforeSeparator;
  private TitledSeparator myAfterSeparator;
  private List<IntentionUsagePanel> myBeforeUsagePanels = new ArrayList<IntentionUsagePanel>();
  private List<IntentionUsagePanel> myAfterUsagePanels = new ArrayList<IntentionUsagePanel>();
  private static final @NonNls String BEFORE_TEMPLATE = "before.java.template";
  private static final @NonNls String AFTER_TEMPLATE = "after.java.template";

  public void reset(IntentionActionMetaData actionMetaData)  {
    try {
      final URL url = actionMetaData.getDescription();
      final String description = url != null ? ResourceUtil.loadText(url) : CodeInsightBundle.message("under.construction.string");
      myDescriptionBrowser.setText(description);

      showUsages(myBeforePanel, myBeforeSeparator, myBeforeUsagePanels, actionMetaData.getExampleUsagesBefore());
      showUsages(myAfterPanel, myAfterSeparator, myAfterUsagePanels, actionMetaData.getExampleUsagesAfter());

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myPanel.revalidate();
        }
      });

    }
    catch (IOException e) {
      LOG.error(e);
    }
  }


  public void reset(String intentionCategory)  {
    try {
      String text = CodeInsightBundle.message("intention.settings.category.text", intentionCategory);

      myDescriptionBrowser.setText(text);

      URL beforeURL = getClass().getClassLoader().getResource(getClass().getPackage().getName().replace('.','/') + "/" + BEFORE_TEMPLATE);
      showUsages(myBeforePanel, myBeforeSeparator, myBeforeUsagePanels, new URL[]{beforeURL});
      URL afterURL = getClass().getClassLoader().getResource(getClass().getPackage().getName().replace('.','/') + "/" + AFTER_TEMPLATE);
      showUsages(myAfterPanel, myAfterSeparator, myAfterUsagePanels, new URL[]{afterURL});

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myPanel.revalidate();
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void showUsages(final JPanel panel,
                                 final TitledSeparator separator,
                                 List<IntentionUsagePanel> usagePanels,
                                 URL[] exampleUsages) throws IOException {
    GridBagConstraints gb = null;
    boolean reuse = panel.getComponents().length == exampleUsages.length;
    if (!reuse) {
      disposeUsagePanels(usagePanels);
      panel.setLayout(new GridBagLayout());
      panel.removeAll();
      gb = new GridBagConstraints();
      gb.anchor = GridBagConstraints.NORTHWEST;
      gb.fill = GridBagConstraints.BOTH;
      gb.gridheight = GridBagConstraints.REMAINDER;
      gb.gridwidth = 1;
      gb.gridx = 0;
      gb.gridy = 0;
      gb.insets = new Insets(0,0,0,0);
      gb.ipadx = 5;
      gb.ipady = 5;
      gb.weightx = 1;
      gb.weighty = 1;
    }

    for (int i = 0; i < exampleUsages.length; i++) {
      final URL exampleUsage = exampleUsages[i];
      final String name = StringUtil.trimEnd(exampleUsage.getPath(), IntentionActionMetaData.EXAMPLE_USAGE_URL_SUFFIX);
      final FileTypeManagerEx fileTypeManager = FileTypeManagerEx.getInstanceEx();
      final String extension = fileTypeManager.getExtension(name);
      final FileType fileType = fileTypeManager.getFileTypeByExtension(extension);

      IntentionUsagePanel usagePanel;
      if (reuse) {
        usagePanel = (IntentionUsagePanel)panel.getComponent(i);
      }
      else {
        usagePanel = new IntentionUsagePanel();
        usagePanels.add(usagePanel);
      }
      usagePanel.reset(ResourceUtil.loadText(exampleUsage), fileType);

      String title = StringUtil.trimEnd(new File(exampleUsage.getFile()).getName(), IntentionActionMetaData.EXAMPLE_USAGE_URL_SUFFIX);
      separator.setText(title);
      if (!reuse) {
        if (i == exampleUsages.length) {
          gb.gridwidth = GridBagConstraints.REMAINDER;
        }
        panel.add(usagePanel, gb);
        gb.gridx++;
      }
    }
  }

  public JPanel getComponent() {
    return myPanel;
  }

  public void dispose() {
    disposeUsagePanels(myBeforeUsagePanels);
    disposeUsagePanels(myAfterUsagePanels);
  }

  private static void disposeUsagePanels(List<IntentionUsagePanel> usagePanels) {
    for (final IntentionUsagePanel usagePanel : usagePanels) {
      usagePanel.dispose();
    }
    usagePanels.clear();
  }

  public void init(final int preferredWidth) {
    //adjust vertical dimension to be equal for all three panels
    double height = (myDescriptionBrowser.getSize().getHeight() + myBeforePanel.getSize().getHeight() + myAfterPanel.getSize().getHeight()) / 3;
    final Dimension newd = new Dimension(preferredWidth, (int)height);
    myDescriptionBrowser.setSize(newd);
    myDescriptionBrowser.setPreferredSize(newd);
    myDescriptionBrowser.setMaximumSize(newd);
    myDescriptionBrowser.setMinimumSize(newd);

    myBeforePanel.setSize(newd);
    myBeforePanel.setPreferredSize(newd);
    myBeforePanel.setMaximumSize(newd);
    myBeforePanel.setMinimumSize(newd);

    myAfterPanel.setSize(newd);
    myAfterPanel.setPreferredSize(newd);
    myAfterPanel.setMaximumSize(newd);
    myAfterPanel.setMinimumSize(newd);
  }
}