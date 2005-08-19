/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.TextFieldWithHistory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.DevKitBundle;

/**
 * User: anna
 * Date: Nov 22, 2004
 */
public class IdeaJdkConfigurable implements AdditionalDataConfigurable {
  private JLabel mySandboxHomeLabel = new JLabel(DevKitBundle.message("sandbox.home.label"));
  private TextFieldWithHistory mySandboxHome = new TextFieldWithHistory() {
    public void addCurrentTextToHistory() {
      super.addCurrentTextToHistory();
      final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
      final String value = propertiesComponent.getValue(SANDBOX_HISTORY);
      final String text = getText();
      if (value == null) {
        propertiesComponent.setValue(SANDBOX_HISTORY, text);
      }
      else if (value.indexOf(text) == -1) {
        propertiesComponent.setValue(SANDBOX_HISTORY, value + "\n" + text);
      }
    }
  };

  private Sdk myIdeaJdk;

  private boolean myModified;
  @NonNls private static final String SANDBOX_HISTORY = "DEVKIT_SANDBOX_HISTORY";

  public void setSdk(Sdk sdk) {
    myIdeaJdk = sdk;
  }

  public JComponent createComponent() {
    mySandboxHome.setHistorySize(5);
    JPanel wholePanel = new JPanel(new GridBagLayout());
    wholePanel.add(mySandboxHomeLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST,
                                                              GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    wholePanel.add(GuiUtils.constructFieldWithBrowseButton(mySandboxHome,
                                                           new ActionListener() {
                                                             public void actionPerformed(ActionEvent e) {
                                                               FileChooserDescriptor descriptor = FileChooserDescriptorFactory
                                                                 .createSingleFolderDescriptor();
                                                               descriptor.setTitle(DevKitBundle.message("sandbox.home"));
                                                               descriptor.setDescription(
                                                                 DevKitBundle.message("sandbox.purpose"));
                                                               VirtualFile[] files = FileChooser.chooseFiles(mySandboxHome, descriptor);
                                                               if (files.length != 0) {
                                                                 mySandboxHome.setText(
                                                                   FileUtil.toSystemDependentName(files[0].getPath()));
                                                               }
                                                               myModified = true;
                                                             }
                                                           }),
                   new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST,
                                          GridBagConstraints.HORIZONTAL, new Insets(0, 66, 0, 0), 0, 0));

    mySandboxHome.addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        myModified = true;
      }
    });
    mySandboxHome.setText("");
    myModified = true;
    return wholePanel;
  }

  public boolean isModified() {
    return myModified;
  }

  public void apply() throws ConfigurationException {
    if (mySandboxHome.getText() == null || mySandboxHome.getText().length() == 0) {
      throw new ConfigurationException(DevKitBundle.message("sandbox.specification"));
    }
    mySandboxHome.addCurrentTextToHistory();
    Sandbox sandbox = new Sandbox(mySandboxHome.getText());
    final SdkModificator modificator = myIdeaJdk.getSdkModificator();
    modificator.setSdkAdditionalData(sandbox);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        modificator.commitChanges();
      }
    });
    myModified = false;
  }

  public void reset() {
    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    final String history = propertiesComponent.getValue(SANDBOX_HISTORY);
    if (history != null) {
      final String[] items = history.split("\n");
      ArrayList<String> result = new ArrayList<String>();
      for (String item : items) {
        if (item != null && item.length() > 0) {
          result.add(item);
        }
      }
      mySandboxHome.setHistory(result);
    }
    if (myIdeaJdk != null && myIdeaJdk.getSdkAdditionalData() instanceof Sandbox) {
      final String sandboxHome = ((Sandbox)myIdeaJdk.getSdkAdditionalData()).getSandboxHome();
      mySandboxHome.setText(sandboxHome);
      mySandboxHome.setSelectedItem(sandboxHome);
      myModified = false;
    } else {
      mySandboxHome.setText("");
    }
  }

  public void disposeUIResources() {
  }
}
