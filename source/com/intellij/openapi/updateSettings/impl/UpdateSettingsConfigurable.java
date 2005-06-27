package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.*;
import com.intellij.ide.actions.CheckForUpdateAction;
import org.jdom.Element;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormat;

/**
 * Created by IntelliJ IDEA.
 * User: pti
 * Date: Jun 10, 2005
 * Time: 12:39:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class UpdateSettingsConfigurable extends BaseConfigurable implements ApplicationComponent, JDOMExternalizable {

  private UpdatesSettingsPanel myUpdatesSettingsPanel;
  private boolean myCheckNowEnabled = true;

  public static final String ON_START_UP = "On every start";
  public static final String DAILY = "Daily";
  public static String WEEKLY = "Weekly";
  public static String MONTHLY = "Monthly";

  public boolean CHECK_NEEDED = true;
  public String CHECK_PERIOD = WEEKLY;
  public long LAST_TIME_CHECKED = 0;

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public String getDisplayName() {
    return "Updates";
  }

  public JComponent createComponent() {
    myUpdatesSettingsPanel = new UpdatesSettingsPanel();
    return myUpdatesSettingsPanel.myPanel;
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/confidurableUpdates.png");
  }

  public void apply() throws ConfigurationException {
    CHECK_PERIOD = (String)myUpdatesSettingsPanel.myPeriodCombo.getSelectedItem();
    CHECK_NEEDED = myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected();
  }

  public void reset() {
    myUpdatesSettingsPanel.myCbCheckForUpdates.setSelected(CHECK_NEEDED);
    myUpdatesSettingsPanel.myPeriodCombo.setSelectedItem(CHECK_PERIOD);
    myUpdatesSettingsPanel.myPeriodCombo.setEnabled(myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected());
    myUpdatesSettingsPanel.updateLastCheckedLabel();
  }

  public boolean isModified() {
      return CHECK_NEEDED != myUpdatesSettingsPanel.myCbCheckForUpdates.isSelected() ||
           !CHECK_PERIOD.equals(myUpdatesSettingsPanel.myPeriodCombo.getSelectedItem());
  }

  public void disposeUIResources() {
    myUpdatesSettingsPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.versionUpdates"; //TODO[pti]: request Help Topic
  }

  public String getComponentName() {
    return "UpdatesConfigurable";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public static UpdateSettingsConfigurable getInstance() {
    return ApplicationManager.getApplication().getComponent(UpdateSettingsConfigurable.class);
  }

  public void setCheckNowEnabled (boolean enabled) {
    myCheckNowEnabled = enabled;
  }

  private class UpdatesSettingsPanel {

    private JPanel myPanel;
    private JButton myBtnCheckNow;
    private JComboBox myPeriodCombo;
    private JCheckBox myCbCheckForUpdates;
    private JLabel myBuildNumber;
    private JLabel myVersionNumber;
    private JLabel myLastCheckedDate;

    public UpdatesSettingsPanel() {
      myPeriodCombo.addItem(ON_START_UP);
      myPeriodCombo.addItem(DAILY);
      myPeriodCombo.addItem(WEEKLY);
      myPeriodCombo.addItem(MONTHLY);

      String version = ApplicationInfo.getInstance().getMajorVersion() + "." + ApplicationInfo.getInstance().getMajorVersion();
      if (version.equalsIgnoreCase("null.null")) {
        version = ApplicationInfo.getInstance().getVersionName();
      }
      myVersionNumber.setText(version);

      String currentBuild = (ApplicationInfo.getInstance().getBuildNumber() == null) ? "N/A" : ApplicationInfo.getInstance().getBuildNumber();
      myBuildNumber.setText(currentBuild);

      myCbCheckForUpdates.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myPeriodCombo.setEnabled(myCbCheckForUpdates.isSelected());
        }
      });

      myBtnCheckNow.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          CheckForUpdateAction.actionPerformed(false);
          updateLastCheckedLabel();
        }
      });
      myBtnCheckNow.setEnabled(myCheckNowEnabled);
    }

    private void updateLastCheckedLabel() {
      final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);
      myLastCheckedDate.setText(LAST_TIME_CHECKED == 0 ? "Never" : dateFormat.format(LAST_TIME_CHECKED));
    }
  }

}
