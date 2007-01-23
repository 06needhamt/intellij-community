/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.12.2006
 * Time: 16:04:55
 */
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.IgnoredFileBean;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IgnoreUnversionedDialog extends DialogWrapper {
  private JRadioButton myIgnoreSpecifiedFileRadioButton;
  private JRadioButton myIgnoreAllFilesUnderRadioButton;
  private JTextField myIgnoreDirectoryTextField;
  private JRadioButton myIgnoreAllFilesMatchingRadioButton;
  private JTextField myIgnoreMaskTextField;
  private JPanel myPanel;
  private JTextField myIgnoreFileTextField;
  private List<VirtualFile> myFilesToIgnore;
  private Project myProject;

  public IgnoreUnversionedDialog(final Project project) {
    super(project, false);
    myProject = project;
    setTitle(VcsBundle.message("ignored.edit.title"));
    init();
    myIgnoreAllFilesUnderRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myIgnoreDirectoryTextField.setEnabled(myIgnoreAllFilesUnderRadioButton.isSelected());
      }
    });
    myIgnoreAllFilesMatchingRadioButton.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myIgnoreMaskTextField.setEnabled(myIgnoreAllFilesMatchingRadioButton.isSelected());
      }
    });
    myIgnoreSpecifiedFileRadioButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateFileTextField();
      }
    });
  }

  private void updateFileTextField() {
    myIgnoreFileTextField.setEnabled(myIgnoreSpecifiedFileRadioButton.isSelected() &&
                                     (myFilesToIgnore == null || myFilesToIgnore.size() == 1));
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public void setFilesToIgnore(List<VirtualFile> virtualFiles) {
    assert virtualFiles.size() > 0;
    myFilesToIgnore = virtualFiles;
    if (virtualFiles.size() == 1) {
      VirtualFile projectDir = myProject.getBaseDir();
      String path = FileUtil.getRelativePath(new File(projectDir.getPresentableUrl()), new File(virtualFiles.get(0).getPresentableUrl()));
      myIgnoreFileTextField.setText(path);
    }
    else {
      myIgnoreFileTextField.setText(VcsBundle.message("ignored.edit.multiple.files", virtualFiles.size()));
    }
    updateFileTextField();

    final VirtualFile[] ancestors = VfsUtil.getCommonAncestors(virtualFiles.toArray(new VirtualFile[virtualFiles.size()]));
    if (ancestors.length > 0) {
      myIgnoreDirectoryTextField.setText(ancestors [0].getPresentableUrl());
    }
    else {
      myIgnoreDirectoryTextField.setText(virtualFiles.get(0).getParent().getPresentableUrl());
    }

    final Set<String> extensions = new HashSet<String>();
    for(VirtualFile vf: virtualFiles) {
      final String extension = vf.getExtension();
      if (extension != null) {
        extensions.add(extension);
      }
    }
    if (extensions.size() > 0) {
      final String[] extensionArray = extensions.toArray(new String[extensions.size()]);
      myIgnoreMaskTextField.setText("*." + extensionArray [0]);
    }
    else {
      myIgnoreMaskTextField.setText(virtualFiles.get(0).getPresentableName());
    }
  }

  public void setIgnoredFile(final IgnoredFileBean bean) {
    if (bean.getPath() != null) {
      String path = bean.getPath().replace('/', File.separatorChar);
      if (path.endsWith(File.separator)) {
        myIgnoreAllFilesUnderRadioButton.setSelected(true);
        myIgnoreDirectoryTextField.setText(path);
      }
      else {
        myIgnoreSpecifiedFileRadioButton.setSelected(true);
        myIgnoreFileTextField.setText(path);
      }
    }
    else {
      myIgnoreAllFilesMatchingRadioButton.setSelected(true);
      myIgnoreMaskTextField.setText(bean.getMask());
    }
  }

  public IgnoredFileBean[] getSelectedIgnoredFiles() {
    VirtualFile projectDir = myProject.getBaseDir();
    if (myIgnoreSpecifiedFileRadioButton.isSelected()) {
      if (myFilesToIgnore == null) {
        IgnoredFileBean bean = new IgnoredFileBean();
        bean.setPath(myIgnoreFileTextField.getText().replace(File.separatorChar, '/'));
        return new IgnoredFileBean[] { bean };
      }
      IgnoredFileBean[] result = new IgnoredFileBean[myFilesToIgnore.size()];
      for(int i=0; i<myFilesToIgnore.size(); i++) {
        result [i] = new IgnoredFileBean();
        String path = FileUtil.getRelativePath(new File(projectDir.getPresentableUrl()), new File(myFilesToIgnore.get(i).getPresentableUrl()));
        result [i].setPath(path);
      }
      return result;
    }
    if (myIgnoreAllFilesUnderRadioButton.isSelected()) {
      IgnoredFileBean result = new IgnoredFileBean();
      String path = FileUtil.getRelativePath(new File(projectDir.getPresentableUrl()), new File(myIgnoreDirectoryTextField.getText()));
      if (path == null) {
        path = myIgnoreDirectoryTextField.getText().replace(File.separatorChar, '/');
      }
      if (!path.endsWith(File.separator)) {
        path += File.separator;
      }
      result.setPath(path.replace(File.separatorChar, '/'));
      return new IgnoredFileBean[] { result };
    }
    if (myIgnoreAllFilesMatchingRadioButton.isSelected()) {
      IgnoredFileBean result = new IgnoredFileBean();
      result.setMask(myIgnoreMaskTextField.getText());
      return new IgnoredFileBean[] { result };
    }
    return new IgnoredFileBean[0];
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "IgnoreUnversionedDialog";
  }
}