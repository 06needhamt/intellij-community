package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 26, 2003
 * Time: 3:51:58 PM
 * To change this template use Options | File Templates.
 */
public class InstalledPluginsTableModel extends PluginTableModel {
  public static Map<PluginId, Integer> NewVersions2Plugins = new HashMap<PluginId, Integer>();

  public InstalledPluginsTableModel(SortableProvider sortableProvider) {
    super(sortableProvider, new PluginManagerColumnInfo(PluginManagerColumnInfo.COLUMN_NAME, sortableProvider));

    view = new ArrayList<IdeaPluginDescriptor>(Arrays.asList(PluginManager.getPlugins()));
    for (Iterator<IdeaPluginDescriptor> iterator = view.iterator(); iterator.hasNext();) {
      @NonNls final String s = iterator.next().getPluginId().getIdString();
      if ("com.intellij".equals(s)) iterator.remove();
    }
    sortByColumn(0);
  }

  public void addData(ArrayList<IdeaPluginDescriptor> list) {
    modifyData(list);
  }

  public void modifyData(ArrayList<IdeaPluginDescriptor> list) {
    //  For each downloadable plugin we need to know whether its counterpart
    //  is already installed, and if yes compare the difference in versions:
    //  availability of newer versions will be indicated separately.
    for (IdeaPluginDescriptor descr : list) {
      PluginId descrId = descr.getPluginId();
      IdeaPluginDescriptor existing = PluginManager.getPlugin(descrId);
      if (existing != null) {
        if (descr instanceof PluginNode) {
          updateExistingPluginInfo(descr, existing);
        } else {
          view.add(descr);
        }
      }
    }
    safeSort();
  }

  public void clearData() {
    view.clear();
    NewVersions2Plugins.clear();
  }

  private static void updateExistingPluginInfo(IdeaPluginDescriptor descr, IdeaPluginDescriptor existing) {
    int state = PluginManagerColumnInfo.compareVersion(descr.getVersion(), existing.getVersion());
    if (state > 0) {
      NewVersions2Plugins.put(existing.getPluginId(), 1);
    }

    final IdeaPluginDescriptorImpl plugin = (IdeaPluginDescriptorImpl)existing;
    plugin.setDownloadsCount(descr.getDownloads());
    plugin.setVendor(descr.getVendor());
    plugin.setVendorEmail(descr.getVendorEmail());
    plugin.setVendorUrl(descr.getVendorUrl());
    plugin.setUrl(descr.getUrl());
  }

  public static boolean hasNewerVersion(PluginId descr) {
    return NewVersions2Plugins.containsKey(descr);
  }
}
