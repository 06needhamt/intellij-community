package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.idea.svn.SvnVcs;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;

import org.tmatesoft.svn.util.PathUtil;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 25.06.2005
 * Time: 17:12:47
 * To change this template use File | Settings | File Templates.
 */
public class SelectLocationDialog extends DialogWrapper {
  private Project myProject;
  private RepositoryBrowserComponent myRepositoryBrowser;
  private String myURL;
  private String myDstName;
  private String myDstLabel;
  private JTextField myDstText;
  private boolean myIsShowFiles;

  public SelectLocationDialog(Project project, String url) {
    this(project, url, null, null, true);
  }

  public SelectLocationDialog(Project project, String url,
                              String dstLabel, String dstName, boolean showFiles) {
    super(project, true);
    myProject = project;
    myDstLabel = dstLabel;
    myDstName = dstName;
    myURL = url;
    myIsShowFiles = showFiles;
    setTitle("Select Repository Location");
    init();
  }

  protected String getDimensionServiceKey() {
    return "svn.repositoryBrowser";
  }

  protected void init() {
    super.init();
    myRepositoryBrowser.setRepositoryURL(myURL, myIsShowFiles);
    if (myRepositoryBrowser.getRootURL() != null) {
      myRepositoryBrowser.getPreferredFocusedComponent().requestFocus();
    }
    myRepositoryBrowser.addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        getOKAction().setEnabled(isOKActionEnabled());
      }
    });
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(2, 2, 2, 2);
    gc.gridwidth = 2;
    gc.gridheight = 1;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.BOTH;
    gc.weightx = 1;
    gc.weighty = 1;


    myRepositoryBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));
    panel.add(myRepositoryBrowser, gc);
    if (myDstName != null) {
      gc.gridy += 1;
      gc.gridwidth = 1;
      gc.gridx = 0;
      gc.fill = GridBagConstraints.NONE;
      gc.weightx = 0;
      gc.weighty = 0;

      JLabel dstLabel = new JLabel(myDstLabel);
      panel.add(dstLabel, gc);

      gc.gridx += 1;
      gc.weightx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;

      myDstText = new JTextField();
      myDstText.setText(myDstName);
      myDstText.selectAll();
      panel.add(myDstText, gc);

      myDstText.getDocument().addDocumentListener(new DocumentListener() {
        public void insertUpdate(DocumentEvent e) {
          getOKAction().setEnabled(isOKActionEnabled());
        }

        public void removeUpdate(DocumentEvent e) {
          getOKAction().setEnabled(isOKActionEnabled());
        }

        public void changedUpdate(DocumentEvent e) {
          getOKAction().setEnabled(isOKActionEnabled());
        }
      });

      DialogUtil.registerMnemonic(dstLabel, myDstText);
      gc.gridx = 0;
      gc.gridy += 1;
      gc.gridwidth = 2;

      panel.add(new JSeparator(), gc);
    }

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return (JComponent)myRepositoryBrowser.getPreferredFocusedComponent();
  }

  public boolean shouldCloseOnCross() {
    return true;
  }

  public boolean isOKActionEnabled() {
    boolean ok = myRepositoryBrowser.getSelectedURL() != null;
    if (ok && myDstText != null) {
      return myDstText.getText().trim().length() > 0;
    }
    return ok;
  }

  public String getDestinationName() {
    return PathUtil.encode(myDstText.getText().trim());
  }

  public String getSelectedURL() {
    return myRepositoryBrowser.getSelectedURL();
  }
}
