/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 31-Jul-2006
 * Time: 17:44:39
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public abstract class InspectionToolsConfigurable implements Configurable, ErrorsConfigurable {
  private CardLayout myLayout = new CardLayout();
  private JPanel myPanel;

  public static final String ID = "Errors";
  public static final String DISPLAY_NAME = "Inspections";

  private JComboBox myProfiles;
  private Map<String, SingleInspectionProfilePanel> myPanels = new HashMap<String, SingleInspectionProfilePanel>();

  private JPanel myWholePanel;
  private JButton myAddButton;
  private JButton myDeleteButton;
  private JButton myImportButton;
  private JButton myExportButton;
  private ArrayList<String> myDeletedProfiles = new ArrayList<String>();
  protected final InspectionProfileManager myProfileManager;
  protected final InspectionProjectProfileManager myProjectProfileManager;
  private static final Logger LOG = Logger.getInstance("#" + InspectionToolsConfigurable.class.getName());


  public InspectionToolsConfigurable(InspectionProjectProfileManager projectProfileManager, InspectionProfileManager profileManager) {
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final ModifiableModel model = SingleInspectionProfilePanel.createNewProfile(-1, getSelectedObject(), myWholePanel, "");
        if (model != null) {
          addProjectProfile((InspectionProfileImpl)model);
          myDeletedProfiles.remove(model.getName());
        }
      }
    });

    myDeleteButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final InspectionProfileImpl selectedProfile = (InspectionProfileImpl)myProfiles.getSelectedItem();
        ((DefaultComboBoxModel)myProfiles.getModel()).removeElement(selectedProfile);
         myDeletedProfiles.add(selectedProfile.getName());
      }
    });

    myImportButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor();
        descriptor.setDescription("Choose profile file");
        final VirtualFile[] files = FileChooser.chooseFiles(myWholePanel, descriptor);
        if (files.length == 0) return;
        final InspectionProfileImpl profile = new InspectionProfileImpl(InspectionProfileImpl.getDefaultProfile());
        try {
          profile.readExternal(JDOMUtil.loadDocument(VfsUtil.virtualToIoFile(files[0])).getRootElement());

          if (Messages.showYesNoDialog(myWholePanel, "Do you want the profile to be saved to current project?", "Choose Profile Purpose", Messages.getQuestionIcon())
              == DialogWrapper.OK_EXIT_CODE) {
            addProjectProfile(profile);
          } else {
            myProfileManager.addProfile(profile);
          }
        }
        catch (InvalidDataException e1) {
          LOG.error(e1);
        }
        catch (JDOMException e1) {
          LOG.error(e1);
        }
        catch (IOException e1) {
          LOG.error(e1);
        }
      }
    });

    myExportButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setDescription("Choose directory to store profile file");
        final VirtualFile[] files = FileChooser.chooseFiles(myWholePanel, descriptor);
        if (files.length == 0) return;
        final Element element = new Element("inspections");
        try {
          final InspectionProfileImpl profile = (InspectionProfileImpl)myProfiles.getSelectedItem();
          profile.writeExternal(element);
          JDOMUtil.writeDocument(new Document(element), files[0].getPath() + File.separator + FileUtil.sanitizeFileName(profile.getName()) + ".xml", SystemProperties.getLineSeparator());
        }
        catch (WriteExternalException e1) {
          LOG.error(e1);
        }
        catch (IOException e1) {
          LOG.error(e1);
        }
      }
    });

    myProjectProfileManager = projectProfileManager;
    myProfileManager = profileManager;
  }

  private void addProjectProfile(InspectionProfileImpl model) {
    final SingleInspectionProfilePanel panel = new SingleInspectionProfilePanel(model.getName(), model);
    myPanel.add(model.getName(), panel);
    myPanels.put(model.getName(), panel);
    ((DefaultComboBoxModel)myProfiles.getModel()).addElement(model);
    myProfiles.setSelectedItem(model);
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableErrorHighlighting.png");
  }

  public String getHelpTopic() {
    return "preferences.errorHighlight";
  }

  public JComponent createComponent() {
    myProfiles.setRenderer(new DefaultListCellRenderer(){
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(((Profile)value).getName());
        return rendererComponent;
      }
    });
    myProfiles.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myLayout.show(myPanel, ((Profile)myProfiles.getSelectedItem()).getName());
      }
    });

    myPanel.setLayout(myLayout);
    return myWholePanel;
  }

  protected abstract InspectionProfileImpl getCurrentProfile();

  public boolean isModified() {
    if (!Comparing.strEqual(((InspectionProfileImpl)myProfiles.getSelectedItem()).getName(), getCurrentProfile().getName())) return true;
    for (SingleInspectionProfilePanel panel : myPanels.values()) {
      if (panel.isModified()) return true;
    }
    return !myDeletedProfiles.isEmpty();
  }

  public void apply() throws ConfigurationException {
    for (String name : myPanels.keySet()) {
      if (myDeletedProfiles.contains(name)) {
        deleteProfile(name);
      } else {
        myPanels.get(name).apply();
      }
    }
    setCurrentProfile((InspectionProfileImpl)myProfiles.getSelectedItem());
    myDeletedProfiles.clear();
  }

  protected void deleteProfile(String name) {
    myProfileManager.deleteProfile(name);
    myProjectProfileManager.deleteProfile(name);
  }

  protected abstract void setCurrentProfile(InspectionProfileImpl profile);

  public void reset() {
    final DefaultComboBoxModel model = new DefaultComboBoxModel();
    myProfiles.setModel(model);
    for (Profile profile : getProfiles()) {
      model.addElement(profile);
      final String profileName = profile.getName();
      final SingleInspectionProfilePanel panel = new SingleInspectionProfilePanel(profileName, ((InspectionProfileImpl)profile).getModifiableModel());
      myPanels.put(profileName, panel);
      panel.reset();
      myPanel.add(profileName, panel);
    }
    myProfiles.setSelectedItem(getCurrentProfile());
    myLayout.show(myPanel, getCurrentProfile().getName());
  }

  protected Collection<Profile> getProfiles() {
    final Collection<Profile> result = new ArrayList<Profile>();
    result.addAll(myProfileManager.getProfiles());
    result.addAll(myProjectProfileManager.getProfiles());
    return result;
  }

  public void disposeUIResources() {
    for (SingleInspectionProfilePanel panel : myPanels.values()) {
      panel.disposeUI();
    }
    myPanels.clear();
  }

  public void selectProfile(String name) {
    for (int i = 0; i < myProfiles.getItemCount(); i++) {
      if (Comparing.strEqual(((InspectionProfileImpl)myProfiles.getItemAt(i)).getName(), name)) {
        myProfiles.setSelectedIndex(i);
        break;
      }
    }
  }

  public void selectInspectionTool(String selectedToolShortName) {
    myPanels.get(((Profile)myProfiles.getSelectedItem()).getName()).selectInspectionTool(selectedToolShortName);
  }

  public InspectionProfileImpl getSelectedObject() {
    return (InspectionProfileImpl)myProfiles.getSelectedItem();
  }

}
